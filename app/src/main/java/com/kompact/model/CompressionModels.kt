package com.kompact.model

import android.net.Uri

enum class AppScreen { HOME, SETTINGS, ABOUT, DONATE, CONTACT }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class CompressionType { IMAGE }

enum class SourceCategory { NONE, IMAGE, DOCUMENT, MIXED, UNSUPPORTED }

enum class PreviewKind { NONE, IMAGE }

enum class CompressionGoal { PERCENTAGE, TARGET_SIZE }

enum class ImageOutputFormat { ORIGINAL, JPG, PNG, WEBP, BMP, GIF, TIF, HEIF, HEIC, AVIF, JXL, TELEGRAM_PNG }

data class CropBounds(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    companion object {
        val Full = CropBounds()
        private const val MIN_SIZE = 0.02f
        private const val EPSILON = 0.001f
    }

    fun normalized(): CropBounds {
        val clampedLeft = left.coerceIn(0f, 1f - MIN_SIZE)
        val clampedTop = top.coerceIn(0f, 1f - MIN_SIZE)
        val clampedRight = right.coerceIn(clampedLeft + MIN_SIZE, 1f)
        val clampedBottom = bottom.coerceIn(clampedTop + MIN_SIZE, 1f)
        return CropBounds(
            left = clampedLeft,
            top = clampedTop,
            right = maxOf(clampedRight, clampedLeft + MIN_SIZE).coerceIn(0f, 1f),
            bottom = maxOf(clampedBottom, clampedTop + MIN_SIZE).coerceIn(0f, 1f),
        )
    }

    fun isFull(): Boolean {
        return left <= EPSILON && top <= EPSILON && (1f - right) <= EPSILON && (1f - bottom) <= EPSILON
    }
}

data class ResizeConfig(
    val width: Int = 0,
    val height: Int = 0,
    val maintainAspectRatio: Boolean = true,
    val isPercentage: Boolean = false
) {
    fun isValid() = if (isPercentage) width > 0 else width > 0 && height > 0
}

enum class BackgroundType { TRANSPARENT, CUSTOM }

data class BackgroundConfig(
    val type: BackgroundType = BackgroundType.TRANSPARENT,
    val customColor: Int = android.graphics.Color.BLACK
)

data class ExifData(
    val dateTime: String? = null,
    val make: String? = null,
    val model: String? = null,
    val orientation: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val position: String? = null,
    val exposureTime: String? = null,
    val fNumber: String? = null,
    val iso: Int? = null,
    val focalLength: String? = null,
    val flash: Int? = null,
    val whiteBalance: Int? = null,
    val artist: String? = null,
    val copyright: String? = null,
    val software: String? = null,
    val description: String? = null,
    val userComment: String? = null,
)

enum class ColorFilterType { NONE, GRAYSCALE, SEPIA, VINTAGE, COOL, WARM, CUSTOM }

data class ColorFilterConfig(
    val filterType: ColorFilterType = ColorFilterType.NONE,
    val intensity: Float = 1.0f, // Optional for future intensity tuning
    val hue: Float = 0f // Hue value from 0 to 360 for CUSTOM filter
)

data class ImageCompressionConfig(
    val goal: CompressionGoal = CompressionGoal.PERCENTAGE,
    val percentage: Int = 70,
    val targetSizeBytes: Long? = null,
    val format: ImageOutputFormat = ImageOutputFormat.ORIGINAL,
    val exifData: ExifData? = null,
    val originalExifData: ExifData? = null,
    val stripExif: Boolean = false,
    val outputFileName: String? = null,
    val cropBounds: CropBounds? = null,
    val textConfig: TextOverlayConfig? = null,
    val resizeConfig: ResizeConfig? = null,
    val backgroundConfig: BackgroundConfig? = null,
    val rotationDegrees: Float = 0f,
    val colorFilterConfig: ColorFilterConfig? = null,
    val preserveSourceDate: Boolean = false,
)

data class CompressionResult(
    val source: Uri,
    val output: Uri?,
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long,
    val durationMillis: Long,
    val success: Boolean,
    val message: String,
)

data class CompressionRequest(
    val type: CompressionType,
    val uris: List<Uri>,
    val imageConfig: ImageCompressionConfig = ImageCompressionConfig(),
    val destinationFolder: Uri? = null,
)

data class CompressionProgress(
    val processed: Int,
    val total: Int,
    val result: CompressionResult,
)

data class SelectedFileDescriptor(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?,
)

data class ImagePreviewPayload(
    val source: Uri,
    val previewUri: Uri,
    val croppedPreviewUri: Uri? = null,
    val originalBytes: Long,
    val estimatedBytes: Long,
    val qualityUsed: Int,
    val formatExtension: String,
)

data class LivePreviewState(
    val kind: PreviewKind = PreviewKind.NONE,
    val previews: List<ImagePreviewPayload> = emptyList(),
    val estimatedBatchBytes: Long = 0L,
    val isGenerating: Boolean = false,
    val errorMessage: String? = null,
)

data class CompressionUiState(
    val selectedUris: List<Uri> = emptyList(),
    val selectedFiles: List<SelectedFileDescriptor> = emptyList(),
    val sourceCategory: SourceCategory = SourceCategory.NONE,
    val compressionType: CompressionType = CompressionType.IMAGE,
    val imageConfig: ImageCompressionConfig = ImageCompressionConfig(),
    val destinationFolder: Uri? = null,
    val defaultDownloadsLabel: String = "",
    val isProcessing: Boolean = false,
    val results: List<CompressionResult> = emptyList(),
    val errorMessage: String? = null,
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val totalOriginalBytes: Long = 0,
    val totalCompressedBytes: Long = 0,
    val livePreview: LivePreviewState = LivePreviewState(),
    val lastResult: CompressionResult? = null,
    val canProcessSelection: Boolean = true,
    val selectionMessage: String? = null,
    val exifData: ExifData? = null,
    val originalExifData: ExifData? = null,
    val stripExif: Boolean = false,
    val currentScreen: AppScreen = AppScreen.HOME,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultDestinationFolder: String? = null,
    val isExifEditorVisible: Boolean = false,
    val isCropEditorVisible: Boolean = false,
    val isTextEditorVisible: Boolean = false,
    val isResizeEditorVisible: Boolean = false,
    val isBackgroundEditorVisible: Boolean = false,
    val isColorFilterEditorVisible: Boolean = false,
    val deleteOriginals: Boolean = false,
    val preserveSourceDate: Boolean = false,
)
