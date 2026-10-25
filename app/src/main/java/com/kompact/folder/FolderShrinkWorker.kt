package com.kompact.folder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kompact.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

class FolderShrinkWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 26) return@withContext Result.failure(workDataOf("error" to "Folder shrinking requires Android 8 or newer"))
        val store = FolderJobStore(applicationContext)
        val replacement = PhotoReplacement(applicationContext)
        try {
            setForeground(foreground(applicationContext.getString(R.string.folder_scanning)))
            // Recovery precedes new input/permission validation, even for a different selected folder.
            replacement.recover()
            if (Build.VERSION.SDK_INT >= 29) {
                check(applicationContext.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    applicationContext.getString(R.string.folder_location_permission)
                }
            }
            val tree = Uri.parse(requireNotNull(inputData.getString("tree")))
            require(tree.authority == "com.android.externalstorage.documents") {
                applicationContext.getString(R.string.folder_local_storage)
            }
            val megapixels = inputData.getDouble("megapixels", 5.0)
            require(megapixels.isFinite() && megapixels in 0.1..200.0)
            val job = id.toString()
            if (!store.scanned(job)) {
                scan(tree, inputData.getBoolean("recursive", false), store, job)
                store.finishScan(job)
            }
            while (true) {
                currentCoroutineContext().ensureActive()
                val (uri, name) = store.next(job) ?: break
                publish(store.summary(job), name)
                try {
                    // Commit the file and its queue entry together before honoring Stop.
                    withContext(NonCancellable) {
                        val saved = shrink(Uri.parse(uri), megapixels, replacement)
                        if (saved > 0) store.finish(job, uri, "reduced", "", saved)
                        else store.finish(job, uri, "skipped", "Already below the limit or no space saving")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: PhotoReplacement.RecoveryRequired) {
                    throw e // Do not touch another photo while an original needs restoration.
                } catch (e: ImageMetadata.Unsupported) {
                    store.finish(job, uri, "skipped", e.message.orEmpty())
                } catch (e: Exception) {
                    store.finish(job, uri, "error", e.message ?: e.javaClass.simpleName)
                }
            }
            val summary = store.summary(job)
            setProgress(summary.data())
            Result.success(summary.data())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(workDataOf("error" to (e.message ?: e.javaClass.simpleName).take(1500)))
        } finally {
            store.close()
        }
    }

    private suspend fun scan(tree: Uri, recursive: Boolean, store: FolderJobStore, job: String) {
        val folders = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        folders.add(DocumentsContract.getTreeDocumentId(tree))
        while (folders.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val folder = folders.removeFirst()
            if (!visited.add(folder)) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folder)
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
            val cursor = applicationContext.contentResolver.query(children, projection, null, null, null)
                ?: error("Cannot list selected folder")
            cursor.use {
                while (it.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val documentId = it.getString(0)
                    val name = it.getString(1).orEmpty()
                    val mime = it.getString(2).orEmpty()
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (recursive) folders.add(documentId)
                    } else if (mime.startsWith("image/") || name.substringAfterLast('.', "").lowercase() in
                        setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "avif", "jxl", "dng", "gif", "tif", "tiff", "bmp")) {
                        store.add(job, DocumentsContract.buildDocumentUriUsingTree(tree, documentId).toString(), name)
                    }
                }
            }
        }
    }

    @RequiresApi(26)
    private suspend fun shrink(uri: Uri, megapixels: Double, replacement: PhotoReplacement): Long {
        val resolver = applicationContext.contentResolver
        // Bound both compressed input and bitmap memory; a too-large image is reported, never downsampled blindly.
        val original = resolver.openInputStream(uri)?.use {
            val bytes = it.readBytesBounded(128 * 1024 * 1024)
            bytes
        } ?: error("Cannot read photo")
        val format = ImageMetadata.format(original)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(original, 0, original.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Cannot decode image dimensions" }
        val (width, height) = MegapixelLimit.dimensions(bounds.outWidth, bounds.outHeight, megapixels)
        if (width == bounds.outWidth && height == bounds.outHeight) return 0
        ImageMetadata.checkSource(original, format)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
        require(bounds.outWidth.toLong() / sample * (bounds.outHeight / sample) <= 32_000_000) { "Image exceeds the bitmap memory budget" }
        val decoded = BitmapFactory.decodeByteArray(original, 0, original.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Cannot decode photo")
        val encoded = try {
            if (decoded.colorSpace?.isSrgb == false) {
                throw ImageMetadata.Unsupported("Wide-gamut image is left unchanged to preserve its colour profile")
            }
            // BitmapFactory does not apply EXIF orientation: preserve the original orientation tag verbatim.
            val resized = Bitmap.createScaledBitmap(decoded, width, height, true)
            try {
                ByteArrayOutputStream().use { stream ->
                    @Suppress("DEPRECATION")
                    val compression = when (format) {
                        ImageMetadata.Format.JPEG -> Bitmap.CompressFormat.JPEG
                        ImageMetadata.Format.PNG -> Bitmap.CompressFormat.PNG
                        ImageMetadata.Format.WEBP -> if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP
                    }
                    check(resized.compress(compression, if (format == ImageMetadata.Format.JPEG) 85 else 100, stream))
                    ImageMetadata.copy(original, stream.toByteArray(), format)
                }
            } finally {
                if (resized !== decoded) resized.recycle()
            }
        } finally {
            decoded.recycle()
        }
        if (encoded.size >= original.size) return 0
        val verification = BitmapFactory.decodeByteArray(encoded, 0, encoded.size) ?: error("Result cannot be decoded")
        try {
            check(verification.width == width && verification.height == height) { "Result dimensions do not match" }
        } finally {
            verification.recycle()
        }
        currentCoroutineContext().ensureActive()
        replacement.replace(uri, original, encoded)
        return original.size.toLong() - encoded.size
    }

    private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray = ByteArrayOutputStream().use { output ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(output.size().toLong() + count <= limit) { "Image exceeds the input memory budget" }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }

    private suspend fun publish(summary: FolderJobStore.Summary, name: String) {
        setProgress(summary.data())
        setForeground(foreground("${summary.processed}/${summary.total} · $name"))
    }

    private fun foreground(text: String): ForegroundInfo {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.folder_title), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, FolderShrinkActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle(context.getString(R.string.folder_title)).setContentText(text)
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, context.getString(R.string.folder_stop),
                WorkManager.getInstance(context).createCancelPendingIntent(id))
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(4102, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(4102, notification)
    }

    private fun FolderJobStore.Summary.data() = workDataOf("total" to total, "processed" to processed,
        "reduced" to reduced, "skipped" to skipped, "errors" to errors, "saved" to saved)

    companion object {
        const val WORK_NAME = "shrink-photo-folder"
        private const val CHANNEL = "folder-shrinking"
        fun enqueue(context: Context, tree: Uri, megapixels: Double, recursive: Boolean): UUID {
            val request = OneTimeWorkRequestBuilder<FolderShrinkWorker>()
                .setInputData(workDataOf("tree" to tree.toString(), "megapixels" to megapixels, "recursive" to recursive))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
            return request.id
        }
    }
}
