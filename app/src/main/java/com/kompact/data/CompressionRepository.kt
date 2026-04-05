package com.kompact.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.kompact.model.CompressionGoal
import com.kompact.model.CompressionProgress
import com.kompact.model.CompressionRequest
import com.kompact.model.CompressionResult
import com.kompact.model.CompressionType
import com.kompact.model.ImageCompressionConfig
import com.kompact.model.ImageOutputFormat
import com.kompact.util.DefaultDestinationResolver
import com.kompact.util.applyCrop
import com.kompact.util.applyTextOverlay
import com.kompact.util.decodeBitmap
import com.kompact.util.encodeBitmapWithTarget
import com.kompact.util.getFileSize
import com.kompact.util.readExifData
import com.kompact.util.resolveImageExtension
import com.kompact.util.supportsExif
import com.kompact.util.writeExifData
import com.kompact.util.resolveTargetBytes
import com.kompact.util.webpFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

interface CompressionRepository {
    suspend fun compress(
        request: CompressionRequest,
        onProgress: (CompressionProgress) -> Unit = {},
    ): List<CompressionResult>
}

class LocalCompressionRepository(
    private val context: Context,
) : CompressionRepository {

    private val resolver: ContentResolver = context.contentResolver

    override suspend fun compress(
        request: CompressionRequest,
        onProgress: (CompressionProgress) -> Unit,
    ): List<CompressionResult> = withContext(Dispatchers.IO) {
        val total = request.uris.size.coerceAtLeast(1)
        request.uris.mapIndexed { index, uri ->
            val result = compressImage(uri, request)
            onProgress(CompressionProgress(processed = index + 1, total = total, result = result))
            result
        }
    }

    private fun cacheFile(extension: String): File {
        val dir = File(context.cacheDir, "compressed")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${UUID.randomUUID()}.$extension")
    }

    private fun compressImage(uri: Uri, request: CompressionRequest): CompressionResult {
        val config = request.imageConfig
        val start = System.currentTimeMillis()
        val originalSize = resolver.getFileSize(context, uri)
        return runCatching {
            val extension = resolveImageExtension(context, resolver, uri, config.format)
            val outFile = cacheFile(extension)
            if (config.percentage == 0 && config.format == ImageOutputFormat.ORIGINAL && config.exifData == config.originalExifData) {
                // Copy original without re-encoding or exif modification
                val filePath = getFilePath(uri)
                if (filePath != null) {
                    val sourceFile = java.io.File(filePath)
                    if (sourceFile.exists()) {
                        sourceFile.copyTo(outFile, overwrite = true)
                    } else {
                        error("File not found")
                    }
                } else {
                    resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        java.io.FileInputStream(pfd.fileDescriptor).use { input ->
                            java.io.FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    } ?: error("Unable to read original file")
                }
            } else {
                var bitmap = decodeBitmap(resolver, uri) ?: error("Invalid bitmap")
                bitmap = bitmap.applyCrop(config.cropBounds)
                bitmap = bitmap.applyTextOverlay(config.textConfig)
                val targetBytes = resolveTargetBytes(config, originalSize)
                val (compressedBytes, _) = encodeBitmapWithTarget(
                    bitmap = bitmap,
                    outputFormat = config.format,
                    requestedQuality = config.percentage,
                    targetBytes = targetBytes,
                )
                val originalExif = if (supportsExif(config.format)) readExifData(resolver, uri) else null
                val exifToWrite = config.exifData ?: originalExif
                val finalBytes = if (supportsExif(config.format)) {
                    writeExifData(compressedBytes, exifToWrite)
                } else {
                    compressedBytes
                }
                FileOutputStream(outFile).use { stream ->
                    stream.write(finalBytes)
                }
                bitmap.recycle()
            }
            val (finalUri, finalSize) = persistOutput(
                tempFile = outFile,
                sourceUri = uri,
                extension = extension,
                destinationFolder = request.destinationFolder,
                config = config,
            )
            val end = System.currentTimeMillis()
            CompressionResult(
                source = uri,
                output = finalUri,
                originalSizeBytes = originalSize,
                compressedSizeBytes = finalSize,
                durationMillis = end - start,
                success = true,
                message = "Image compression completed",
            )
        }.getOrElse { error ->
            CompressionResult(
                source = uri,
                output = null,
                originalSizeBytes = originalSize,
                compressedSizeBytes = originalSize,
                durationMillis = System.currentTimeMillis() - start,
                success = false,
                message = error.message ?: "Unknown error",
            )
        }
    }

    private fun ContentResolver.getFileSize(uri: Uri): Long {
        val docLength = DocumentFile.fromSingleUri(context, uri)?.length()
        if (docLength != null && docLength >= 0) return docLength
        val projection = arrayOf(OpenableColumns.SIZE)
        return query(uri, projection, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else 0L
        } ?: 0L
    }

    private fun getFilePath(uri: Uri): String? {
        val projection = arrayOf(android.provider.MediaStore.MediaColumns.DATA)
        return resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                if (index >= 0) cursor.getString(index) else null
            } else null
        }
    }

    private fun persistOutput(
        tempFile: File,
        sourceUri: Uri,
        extension: String,
        destinationFolder: Uri?,
        config: ImageCompressionConfig,
    ): Pair<Uri, Long> {
        val targetName = buildOutputName(sourceUri, extension, config)
        val mimeType = mimeFromExtension(extension)
        val tempSize = tempFile.length()
        destinationFolder?.let { folderUri ->
            DocumentFile.fromTreeUri(context, folderUri)
                ?.createOrReplaceFile(mimeType, targetName)
                ?.let { target ->
                    if (copyTempToDocument(tempFile, target)) {
                        tempFile.delete()
                        return target.uri to tempSize
                    }
                }
        }

        createDefaultMediaStoreEntry(targetName, mimeType)?.let { targetUri ->
            if (copyTempToUri(tempFile, targetUri)) {
                finalizePendingEntry(targetUri)
                tempFile.delete()
                return targetUri to tempSize
            } else {
                resolver.delete(targetUri, null, null)
            }
        }

        DocumentFile.fromSingleUri(context, sourceUri)?.parentFile
            ?.createOrReplaceFile(mimeType, targetName)
            ?.let { target ->
                if (copyTempToDocument(tempFile, target)) {
                    tempFile.delete()
                    return target.uri to tempSize
                }
            }
        val cacheTarget = File(tempFile.parentFile, targetName)
        return if (tempFile.renameTo(cacheTarget)) {
            Uri.fromFile(cacheTarget) to cacheTarget.length()
        } else {
            Uri.fromFile(tempFile) to tempSize
        }
    }

    private fun copyTempToDocument(tempFile: File, target: DocumentFile): Boolean {
        return runCatching {
            context.contentResolver.openOutputStream(target.uri)?.use { output ->
                FileInputStream(tempFile).use { input ->
                    input.copyTo(output)
                }
            } ?: return false
            true
        }.getOrElse { false }
    }

    private fun copyTempToUri(tempFile: File, targetUri: Uri): Boolean {
        return runCatching {
            resolver.openOutputStream(targetUri)?.use { output ->
                FileInputStream(tempFile).use { input ->
                    input.copyTo(output)
                }
            } ?: return false
            true
        }.getOrElse { false }
    }

    private fun createDefaultMediaStoreEntry(name: String, mimeType: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val (collection, relativePath) = when {
            mimeType.startsWith("image/") -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                DefaultDestinationResolver.picturesRelativePath()
            else -> MediaStore.Files.getContentUri("external") to DefaultDestinationResolver.picturesRelativePath()
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return runCatching { resolver.insert(collection, values) }.getOrNull()
    }

    private fun finalizePendingEntry(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(uri, values, null, null)
        }
    }

    private fun buildOutputName(sourceUri: Uri, extension: String, config: ImageCompressionConfig): String {
        val customName = config.outputFileName
        if (customName != null && customName.isNotBlank()) {
            if (customName.endsWith(".$extension", ignoreCase = true)) {
                return customName
            }
            return "$customName.$extension"
        }
        val sourceName = DocumentFile.fromSingleUri(context, sourceUri)?.name
            ?: sourceUri.lastPathSegment
            ?: "file"
        val base = if (sourceName.contains('.')) {
            sourceName.substringBeforeLast('.')
        } else {
            sourceName
        }
        return "$base.$extension"
    }

    private fun mimeFromExtension(extension: String): String {
        return when (extension.lowercase(Locale.ROOT)) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "gif" -> "image/gif"
            "tif", "tiff" -> "image/tiff"
            "heif" -> "image/heif"
            "heic" -> "image/heic"
            "avif" -> "image/avif"
            "jxl" -> "image/jxl"
            else -> "application/octet-stream"
        }
    }

    private fun DocumentFile.createOrReplaceFile(mimeType: String, name: String): DocumentFile? {
        // If file exists, find a unique name with progressive suffix
        var baseName = name
        var ext = ""
        val dotIdx = name.lastIndexOf('.')
        if (dotIdx > 0) {
            baseName = name.substring(0, dotIdx)
            ext = name.substring(dotIdx)
        }
        var candidate = name
        var counter = 1
        while (findFile(candidate) != null) {
            candidate = "$baseName ($counter)$ext"
            counter++
        }
        return createFile(mimeType, candidate)
    }
}
