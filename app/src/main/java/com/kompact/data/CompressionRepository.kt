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
import com.kompact.model.hasVisualEdits
import com.kompact.util.DefaultDestinationResolver
import com.kompact.util.applyAdjust
import com.kompact.util.applyBackground
import com.kompact.util.applyColorFilter
import com.kompact.util.applyCrop
import com.kompact.util.applyDraw
import com.kompact.util.applyResize
import com.kompact.util.applyRotation
import com.kompact.util.applyTextOverlay
import com.kompact.util.decodeBitmap
import com.kompact.util.encodeBitmapWithTarget
import com.kompact.util.getFileSize
import com.kompact.util.isRawFile
import com.kompact.util.readExifData
import com.kompact.util.resolveEffectiveFormat
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
            val hasVisualEdits = config.hasVisualEdits()
            // I Raw non possono essere copiati byte-identical: vanno sempre
            // decodificati (via embedded JPEG) e ricodificati in un formato standard.
            val isRaw = isRawFile(resolver, uri)
            val copiesOriginal = !isRaw && !hasVisualEdits && config.percentage == 0 && config.format == ImageOutputFormat.ORIGINAL && config.exifData == config.originalExifData
            val effectiveFormat = resolveEffectiveFormat(context, resolver, uri, config.format)
            // Keep the source extension only for a byte-identical copy; a real
            // re-encode always gets the extension of the format it is encoded in.
            val extension = resolveImageExtension(context, resolver, uri, if (copiesOriginal) ImageOutputFormat.ORIGINAL else effectiveFormat)
            val outFile = cacheFile(extension)

            if (copiesOriginal) {
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
                if (config.stripExif) {
                    stripExifLossless(outFile)
                }
            } else {
                var bitmap = decodeBitmap(resolver, uri) ?: error("Invalid bitmap")
                bitmap = bitmap.applyRotation(config.rotationDegrees)
                    .applyCrop(config.cropBounds)
                    .applyResize(config.resizeConfig)
                    .applyColorFilter(config.colorFilterConfig)
                    .applyBackground(config.backgroundConfig)
                    .applyTextOverlay(config.texts)
                    .applyDraw(config.drawConfig)
                    .applyAdjust(config.adjustConfig)
                val targetBytes = resolveTargetBytes(config, originalSize)
                val (compressedBytes, _) = encodeBitmapWithTarget(
                    bitmap = bitmap,
                    outputFormat = effectiveFormat,
                    requestedQuality = config.percentage,
                    targetBytes = targetBytes,
                )
                val finalBytes = if (supportsExif(effectiveFormat)) {
                    if (config.stripExif) {
                        writeExifData(compressedBytes, null, null, null)
                    } else {
                        val originalExifFromFile = readExifData(resolver, uri)
                        val baseExif = config.originalExifData ?: originalExifFromFile
                        val userExif = config.exifData
                        
                        val exifToWrite = if (userExif != null && userExif != baseExif) {
                            baseExif?.copy(
                                dateTime = userExif.dateTime ?: baseExif.dateTime,
                                make = userExif.make ?: baseExif.make,
                                model = userExif.model ?: baseExif.model,
                                orientation = userExif.orientation ?: baseExif.orientation,
                                latitude = userExif.latitude ?: baseExif.latitude,
                                longitude = userExif.longitude ?: baseExif.longitude,
                                altitude = userExif.altitude ?: baseExif.altitude,
                                position = userExif.position ?: baseExif.position,
                                exposureTime = userExif.exposureTime ?: baseExif.exposureTime,
                                fNumber = userExif.fNumber ?: baseExif.fNumber,
                                iso = userExif.iso ?: baseExif.iso,
                                focalLength = userExif.focalLength ?: baseExif.focalLength,
                                flash = userExif.flash ?: baseExif.flash,
                                whiteBalance = userExif.whiteBalance ?: baseExif.whiteBalance,
                                artist = userExif.artist ?: baseExif.artist,
                                copyright = userExif.copyright ?: baseExif.copyright,
                                software = userExif.software ?: baseExif.software,
                                description = userExif.description ?: baseExif.description,
                                userComment = userExif.userComment ?: baseExif.userComment
                            ) ?: userExif
                        } else {
                            baseExif ?: originalExifFromFile
                        }
                        writeExifData(compressedBytes, exifToWrite, resolver, uri)
                    }
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

    private fun stripExifLossless(file: File) {
        try {
            // Usa AndroidX ExifInterface se disponibile nel tuo progetto, altrimenti android.media.ExifInterface
            val exif = android.media.ExifInterface(file.absolutePath)
            
            val tagsToRemove = arrayOf(
                android.media.ExifInterface.TAG_DATETIME,
                android.media.ExifInterface.TAG_DATETIME_DIGITIZED,
                android.media.ExifInterface.TAG_DATETIME_ORIGINAL,
                android.media.ExifInterface.TAG_GPS_LATITUDE,
                android.media.ExifInterface.TAG_GPS_LONGITUDE,
                android.media.ExifInterface.TAG_GPS_ALTITUDE,
                android.media.ExifInterface.TAG_MAKE,
                android.media.ExifInterface.TAG_MODEL,
                android.media.ExifInterface.TAG_SOFTWARE,
                android.media.ExifInterface.TAG_ARTIST,
                android.media.ExifInterface.TAG_COPYRIGHT,
                android.media.ExifInterface.TAG_USER_COMMENT,
                android.media.ExifInterface.TAG_IMAGE_DESCRIPTION
            )

            for (tag in tagsToRemove) {
                exif.setAttribute(tag, null)
            }
            
            // saveAttributes() sovrascrive solo l'header EXIF, non ri-comprime l'immagine!
            exif.saveAttributes() 
            
        } catch (e: Exception) {
            e.printStackTrace()
            // Se fallisce, il file rimane intatto
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
        // Nome richiesto: nome originale del file (o quello personalizzato
        // dall'utente). Il suffisso "-edit" viene aggiunto solo se nella
        // cartella di destinazione esiste già un file con lo stesso nome.
        val requestedName = buildOutputName(sourceUri, extension, config)
        val isCustomName = !config.outputFileName.isNullOrBlank()
        val mimeType = mimeFromExtension(extension)
        val tempSize = tempFile.length()

        // Get source file date if preserveSourceDate is enabled
        val sourceDate = if (config.preserveSourceDate) getSourceFileDate(sourceUri) else null

        destinationFolder?.let { folderUri ->
            DocumentFile.fromTreeUri(context, folderUri)?.let { folder ->
                val targetName = resolveOutputName(requestedName, isCustomName) { folder.findFile(it) != null }
                folder.createOrReplaceFile(mimeType, targetName)
                    ?.let { target ->
                        if (copyTempToDocument(tempFile, target)) {
                            tempFile.delete()
                            if (config.preserveSourceDate) {
                                setOutputFileDate(target.uri, sourceDate)
                            }
                            return target.uri to tempSize
                        }
                    }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val (collection, relativePath) = mediaStoreDestination(mimeType)
            val targetName = resolveOutputName(requestedName, isCustomName) { name ->
                mediaStoreHasFile(collection, relativePath, name)
            }
            createDefaultMediaStoreEntry(collection, relativePath, targetName, mimeType, sourceDate)?.let { targetUri ->
                if (copyTempToUri(tempFile, targetUri)) {
                    finalizePendingEntry(targetUri)
                    tempFile.delete()
                    return targetUri to tempSize
                } else {
                    resolver.delete(targetUri, null, null)
                }
            }
        }

        DocumentFile.fromSingleUri(context, sourceUri)?.parentFile?.let { parent ->
            val targetName = resolveOutputName(requestedName, isCustomName) { parent.findFile(it) != null }
            parent.createOrReplaceFile(mimeType, targetName)
                ?.let { target ->
                    if (copyTempToDocument(tempFile, target)) {
                        tempFile.delete()
                        if (config.preserveSourceDate) {
                            setOutputFileDate(target.uri, sourceDate)
                        }
                        return target.uri to tempSize
                    }
                }
        }
        val cacheTargetName = resolveOutputName(requestedName, isCustomName) { name ->
            File(tempFile.parentFile, name).exists()
        }
        val cacheTarget = File(tempFile.parentFile, cacheTargetName)
        return if (tempFile.renameTo(cacheTarget)) {
            if (config.preserveSourceDate && sourceDate != null) {
                cacheTarget.setLastModified(sourceDate)
            }
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

    private fun mediaStoreDestination(mimeType: String): Pair<Uri, String> {
        return if (mimeType.startsWith("image/")) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) to
                DefaultDestinationResolver.picturesRelativePath()
        } else {
            MediaStore.Files.getContentUri("external") to DefaultDestinationResolver.picturesRelativePath()
        }
    }

    private fun mediaStoreHasFile(collection: Uri, relativePath: String, displayName: String): Boolean {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        // MediaStore normalizza RELATIVE_PATH con la barra finale
        val normalizedPath = if (relativePath.endsWith("/")) relativePath else "$relativePath/"
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(displayName, normalizedPath)
        return runCatching {
            resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        }.getOrDefault(false)
    }

    private fun createDefaultMediaStoreEntry(
        collection: Uri,
        relativePath: String,
        name: String,
        mimeType: String,
        sourceDate: Long? = null,
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            sourceDate?.let { 
                put(MediaStore.MediaColumns.DATE_MODIFIED, sourceDate / 1000)
            }
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
        val sourceName = DocumentFile.fromSingleUri(context, sourceUri)?.name
            ?: sourceUri.lastPathSegment
        return buildRequestedOutputName(sourceName, extension, config.outputFileName)
    }

    private fun mimeFromExtension(extension: String): String {
        return when (extension.lowercase(Locale.ROOT)) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "gif" -> "image/gif"
            "tif", "tiff" -> "image/tiff"
            "ico" -> "image/x-icon"
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

    private fun getSourceFileDate(sourceUri: Uri): Long? {
        return runCatching {
            DocumentFile.fromSingleUri(context, sourceUri)?.lastModified()
        }.getOrNull() ?: runCatching {
            val projection = arrayOf(OpenableColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED)
            resolver.query(sourceUri, projection, null, null, null)?.use { cursor ->
                val dateIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                if (dateIndex >= 0 && cursor.moveToFirst()) {
                    val dateSeconds = cursor.getLong(dateIndex)
                    if (dateSeconds > 0) dateSeconds * 1000 else null
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun setOutputFileDate(targetUri: Uri, sourceDate: Long?) {
        if (sourceDate == null) return
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DATE_MODIFIED, sourceDate / 1000)
            }
            resolver.update(targetUri, values, null, null)
        }
    }
}

/**
 * Nome di output richiesto: nome personalizzato dall'utente se presente,
 * altrimenti il nome originale del file sorgente con l'estensione del formato
 * effettivo. Nessun suffisso "-edit" viene aggiunto qui.
 */
internal fun buildRequestedOutputName(sourceName: String?, extension: String, customName: String?): String {
    if (!customName.isNullOrBlank()) {
        return if (customName.endsWith(".$extension", ignoreCase = true)) {
            customName
        } else {
            "$customName.$extension"
        }
    }
    val base = sourceName
        ?.substringBeforeLast('.', "")
        .orEmpty()
        .ifBlank { sourceName ?: "file" }
    return "$base.$extension"
}

/**
 * Aggiunge il suffisso "-edit" prima dell'estensione solo quando il nome
 * generato (non personalizzato dall'utente) collide con un file già esistente
 * nella cartella di destinazione. I nomi scelti dall'utente restano invariati:
 * createOrReplaceFile li deduplica già con un suffisso " (n)".
 */
internal fun resolveOutputName(
    requestedName: String,
    isCustomName: Boolean,
    existsInDestination: (String) -> Boolean,
): String {
    if (isCustomName || !existsInDestination(requestedName)) return requestedName
    val dotIndex = requestedName.lastIndexOf('.')
    return if (dotIndex > 0) {
        requestedName.substring(0, dotIndex) + "-edit" + requestedName.substring(dotIndex)
    } else {
        requestedName + "-edit"
    }
}
