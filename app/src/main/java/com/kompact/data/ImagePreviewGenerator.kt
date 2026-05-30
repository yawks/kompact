package com.kompact.data

import android.content.Context
import android.net.Uri
import com.kompact.model.ImageCompressionConfig
import com.kompact.model.ImageOutputFormat
import com.kompact.model.ImagePreviewPayload
import com.kompact.util.applyBackground
import com.kompact.util.applyCrop
import com.kompact.util.applyResize
import com.kompact.util.applyRotation
import com.kompact.util.applyColorFilter
import com.kompact.util.applyTextOverlay
import com.kompact.util.decodeBitmap
import com.kompact.util.encodeBitmapWithTarget
import com.kompact.util.getFileSize
import com.kompact.util.resolveImageExtension
import com.kompact.util.resolveTargetBytes
import com.kompact.util.supportsExif
import com.kompact.util.writeExifData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ImagePreviewGenerator(context: Context) {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val previewFiles = mutableListOf<File>()

    suspend fun generate(source: Uri, config: ImageCompressionConfig): ImagePreviewPayload = withContext(Dispatchers.IO) {
        var bitmap = decodeBitmap(resolver, source) ?: error("Preview not available")
        bitmap = bitmap.applyRotation(config.rotationDegrees)
            .applyCrop(config.cropBounds)
            .applyResize(config.resizeConfig)
            .applyColorFilter(config.colorFilterConfig)
            .applyBackground(config.backgroundConfig)
            .applyTextOverlay(config.textConfig)
        val originalBytes = resolver.getFileSize(appContext, source)
        val extension = resolveImageExtension(appContext, resolver, source, config.format)
        val targetBytes = resolveTargetBytes(config, originalBytes)
        val (previewBytes, appliedQuality) = encodeBitmapWithTarget(
            bitmap = bitmap,
            outputFormat = config.format,
            requestedQuality = config.percentage,
            targetBytes = targetBytes,
        )
        val exifBytes = if (supportsExif(config.format)) {
            if (config.stripExif) writeExifData(previewBytes, null) else writeExifData(previewBytes, config.exifData)
        } else previewBytes
        bitmap.recycle()
        val previewFile = cachePreviewFile(source, extension)
        FileOutputStream(previewFile).use { it.write(exifBytes) }
        addPreviewFile(previewFile)
        val mirrorsOriginalFile = config.percentage == 0 &&
            config.format == ImageOutputFormat.ORIGINAL &&
            config.exifData == config.originalExifData
        val estimatedBytes = if (mirrorsOriginalFile) originalBytes else previewFile.length()
        ImagePreviewPayload(
            source = source,
            previewUri = Uri.fromFile(previewFile),
            originalBytes = originalBytes,
            estimatedBytes = estimatedBytes,
            qualityUsed = appliedQuality,
            formatExtension = extension,
        )
    }

    fun clear() {
        previewFiles.forEach { it.delete() }
        previewFiles.clear()
    }

    private fun cachePreviewFile(source: Uri, extension: String): File {
        val dir = File(appContext.cacheDir, "preview")
        if (!dir.exists()) dir.mkdirs()
        val hash = source.hashCode().toString() + "_" + System.currentTimeMillis()
        return File(dir, "live_preview_${hash}.$extension")
    }

    private fun addPreviewFile(file: File) {
        previewFiles.add(file)
    }
}
