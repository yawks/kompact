package com.kompact.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kompact.data.LocalCompressionRepository
import com.kompact.data.UserPreferencesRepository
import com.kompact.model.AppScreen
import com.kompact.model.CompressionGoal
import com.kompact.model.CompressionRequest
import com.kompact.model.CompressionResult
import com.kompact.model.CompressionType
import com.kompact.model.CompressionUiState
import com.kompact.model.CropBounds
import com.kompact.model.ExifData
import com.kompact.model.ImageCompressionConfig
import com.kompact.model.ImageOutputFormat
import com.kompact.model.LivePreviewState
import com.kompact.model.ImagePreviewPayload
import com.kompact.model.PreviewKind
import com.kompact.model.SelectedFileDescriptor
import com.kompact.model.SourceCategory
import com.kompact.model.ThemeMode
import com.kompact.util.DefaultDestinationResolver
import com.kompact.data.ImagePreviewGenerator
import com.kompact.util.*
import com.kompact.util.readExifData
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

private val DOC_MIME_TYPES = setOf(
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
)
private val DOC_EXTENSIONS = setOf("doc", "docx")
private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp")

class CompressionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LocalCompressionRepository(application)
    private val preferencesRepository = UserPreferencesRepository(application)
    private val previewGenerator = ImagePreviewGenerator(application)
    private val _state = MutableStateFlow(CompressionUiState())
    val state: StateFlow<CompressionUiState> = _state

    private var currentJob: Job? = null
    private var previewJob: Job? = null

    init {
        _state.update {
            it.copy(
                defaultPicturesLabel = DefaultDestinationResolver.defaultPicturesLabel(),
                defaultDocumentsLabel = DefaultDestinationResolver.defaultDocumentsLabel(),
            )
        }
        loadPreferences()
    }

    fun onUrisSelected(uris: List<Uri>) {
        val descriptors = uris.map { describeFile(it) }
        applySelection(uris, descriptors)
    }

    fun updateImageConfig(config: ImageCompressionConfig) {
        val previous = _state.value.imageConfig
        val prevFingerprint = previous.previewFingerprint()
        val nextFingerprint = config.previewFingerprint()

        _state.update {
            it.copy(imageConfig = config)
        }

        // Do not rerun preview for cosmetic-only changes like filename; refresh only when affecting compression.
        if (_state.value.sourceCategory == SourceCategory.IMAGE && prevFingerprint != nextFingerprint) {
            scheduleLivePreview()
        }
    }

    fun updateExifData(exifData: ExifData?) {
        _state.update {
            it.copy(exifData = exifData)
        }
        val currentConfig = _state.value.imageConfig
        val updatedConfig = currentConfig.copy(exifData = exifData)
        updateImageConfig(updatedConfig)
    }

    fun removeFile(uri: Uri) {
        val snapshot = _state.value
        val filteredUris = snapshot.selectedUris.filterNot { it == uri }
        val filteredFiles = snapshot.selectedFiles.filterNot { it.uri == uri }
        applySelection(filteredUris, filteredFiles)
    }

    fun clearSelection() {
        _state.update {
            it.copy(
                selectedUris = emptyList(),
                selectedFiles = emptyList(),
                totalCount = 0,
                processedCount = 0,
                sourceCategory = SourceCategory.NONE,
                canProcessSelection = true,
                selectionMessage = null,
                errorMessage = null,
                results = emptyList(),
                lastResult = null,
                totalOriginalBytes = 0,
                totalCompressedBytes = 0,
                isExifEditorVisible = false,
                isCropEditorVisible = false,
            )
        }
        clearLivePreview()
    }

    fun setDestinationFolder(uri: Uri?) {
        _state.update { it.copy(destinationFolder = uri) }
    }

    fun clearDestinationFolder() {
        setDestinationFolder(null)
    }

    private fun applySelection(uris: List<Uri>, descriptors: List<SelectedFileDescriptor>) {
        val classification = classifySelection(descriptors)
        val firstImageUri = descriptors.firstOrNull { it.detectCategory() == SourceCategory.IMAGE }?.uri
        val exifData = firstImageUri?.let { readExifData(getApplication<Application>().contentResolver, it) }
        _state.update { current ->
            current.copy(
                selectedUris = uris,
                selectedFiles = descriptors,
                compressionType = classification.type ?: current.compressionType,
                sourceCategory = classification.category,
                canProcessSelection = classification.canProcess && descriptors.isNotEmpty(),
                selectionMessage = classification.message,
                errorMessage = null,
                totalCount = uris.size,
                processedCount = 0,
                totalOriginalBytes = 0,
                totalCompressedBytes = 0,
                results = emptyList(),
                lastResult = null,
                imageConfig = current.imageConfig.copy(
                    targetSizeBytes = null,
                    exifData = exifData,
                    originalExifData = exifData,
                    cropBounds = null,
                ),
                exifData = exifData,
                originalExifData = exifData,
                isExifEditorVisible = false,
                isCropEditorVisible = false,
            )
        }
        scheduleLivePreview()
    }

    fun toggleExifEditor() {
        _state.update { state ->
            val canEdit = state.sourceCategory == SourceCategory.IMAGE && state.selectedFiles.size == 1
            val nextVisible = if (canEdit) !state.isExifEditorVisible else false
            state.copy(isExifEditorVisible = nextVisible)
        }
    }

    fun toggleCropEditor() {
        _state.update { state ->
            val canCrop = state.sourceCategory == SourceCategory.IMAGE && state.selectedFiles.size == 1
            val nextVisible = if (canCrop) !state.isCropEditorVisible else false
            state.copy(isCropEditorVisible = nextVisible)
        }
    }

    fun updateCropBounds(bounds: CropBounds?) {
        val normalized = bounds?.normalized()
        _state.update { current ->
            val updatedConfig = current.imageConfig.copy(cropBounds = normalized)
            current.copy(imageConfig = updatedConfig)
        }
        scheduleLivePreview()
    }

    fun compress() {
        val snapshot = _state.value
        if (snapshot.selectedUris.isEmpty()) {
            _state.update { it.copy(errorMessage = "Select at least one file") }
            return
        }
        if (!snapshot.canProcessSelection) {
            _state.update {
                it.copy(errorMessage = snapshot.selectionMessage ?: "Current selection cannot be processed")
            }
            return
        }
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isProcessing = true,
                    errorMessage = null,
                    results = emptyList(),
                    processedCount = 0,
                    totalCount = snapshot.selectedUris.size,
                    totalOriginalBytes = 0,
                    totalCompressedBytes = 0,
                    lastResult = null,
                )
            }
            val requestType = CompressionType.IMAGE
            val request = CompressionRequest(
                type = requestType,
                uris = snapshot.selectedUris,
                imageConfig = snapshot.imageConfig,
                destinationFolder = snapshot.destinationFolder ?: snapshot.defaultDestinationFolder?.let { Uri.parse(it) },
            )
            val results = repository.compress(request) { progress ->
                _state.update { prev ->
                    val updatedResults = prev.results + progress.result
                    val totalOriginal = updatedResults.sumOf { it.originalSizeBytes }
                    val totalCompressed = updatedResults.filter { it.success && it.output != null }
                        .sumOf { it.compressedSizeBytes }
                    prev.copy(
                        results = updatedResults,
                        processedCount = progress.processed,
                        totalCount = progress.total,
                        totalOriginalBytes = totalOriginal,
                        totalCompressedBytes = if (totalCompressed == 0L) totalOriginal else totalCompressed,
                        lastResult = progress.result,
                    )
                }
            }
            val hasSuccess = results.any { it.success }
            val finalOriginal = results.sumOf { res -> res.originalSizeBytes }
            val successfulCompressed = results.filter { res -> res.success && res.output != null }
                .sumOf { it.compressedSizeBytes }
            val totalCompressedBytes = successfulCompressed.takeIf { total -> total > 0 } ?: finalOriginal
            _state.update {
                it.copy(
                    isProcessing = false,
                    results = results,
                    processedCount = results.size,
                    totalCount = results.size,
                    totalOriginalBytes = finalOriginal,
                    totalCompressedBytes = totalCompressedBytes,
                    lastResult = results.lastOrNull()?.copy(
                        message = "Processing completed: ${finalOriginal.asReadableBytes()} → ${totalCompressedBytes.asReadableBytes()}"
                    ),
                    errorMessage = if (hasSuccess) null else results.lastOrNull()?.message,
                )
            }
            clearLivePreview()
        }
    }

    private fun scheduleLivePreview() {
        previewJob?.cancel()
        val snapshot = _state.value
        when (snapshot.sourceCategory) {
            SourceCategory.IMAGE -> scheduleImagePreview(snapshot)
            SourceCategory.DOCUMENT -> showPreviewMessage("Live preview available for images only")
            SourceCategory.MIXED -> showPreviewMessage("Preview requires files of the same type")
            SourceCategory.UNSUPPORTED -> showPreviewMessage("Preview unavailable for this file type")
            SourceCategory.NONE -> clearLivePreview()
        }
    }

    private fun scheduleImagePreview(snapshot: CompressionUiState) {
        val imageDescriptors = snapshot.selectedFiles.filter { it.detectCategory() == SourceCategory.IMAGE }
        if (imageDescriptors.isEmpty()) {
            if (snapshot.selectedFiles.isNotEmpty()) {
                showPreviewMessage("Add an image to generate a preview")
            } else {
                clearLivePreview()
            }
            return
        }
        previewGenerator.clear()
        previewJob = viewModelScope.launch {
            _state.update { state ->
                state.copy(
                    livePreview = state.livePreview.copy(
                        kind = PreviewKind.IMAGE,
                        isGenerating = true,
                        errorMessage = null,
                    )
                )
            }
            val previewResults = mutableListOf<ImagePreviewPayload>()
            for (descriptor in imageDescriptors) {
                runCatching {
                    previewGenerator.generate(descriptor.uri, snapshot.imageConfig)
                }.onSuccess { result ->
                    previewResults.add(result)
                }.onFailure { _ ->
                    // Skip failed previews
                }
            }
            if (previewResults.isEmpty()) {
                showPreviewMessage("Preview not available")
                return@launch
            }
            val currentTotalBytes = _state.value.selectedFiles.sumOf { it.sizeBytes }.coerceAtLeast(0L)
            val totalEstimated = previewResults.sumOf { it.estimatedBytes }
            val batchEstimate = if (previewResults.size == snapshot.selectedFiles.size) {
                totalEstimated
            } else {
                // Estimate for all based on available
                val totalOriginal = previewResults.sumOf { it.originalBytes }
                val ratio = if (totalOriginal > 0) totalEstimated.toDouble() / totalOriginal.toDouble() else 1.0
                (currentTotalBytes * ratio).toLong().coerceAtLeast(1L)
            }
            _state.update { state ->
                state.copy(
                    livePreview = LivePreviewState(
                        kind = PreviewKind.IMAGE,
                        previews = previewResults,
                        estimatedBatchBytes = batchEstimate,
                        isGenerating = false,
                        errorMessage = null,
                    ),
                    imageConfig = state.imageConfig.copy(targetSizeBytes = batchEstimate)
                )
            }
        }
    }

    private fun showPreviewMessage(message: String) {
        previewGenerator.clear()
        _state.update {
            it.copy(
                livePreview = LivePreviewState(
                    kind = PreviewKind.NONE,
                    errorMessage = message,
                    isGenerating = false,
                )
            )
        }
    }

    private fun clearLivePreview() {
        previewJob?.cancel()
        previewGenerator.clear()
        _state.update { it.copy(livePreview = LivePreviewState()) }
    }

    override fun onCleared() {
        super.onCleared()
        currentJob?.cancel()
        previewJob?.cancel()
        previewGenerator.clear()
    }

    private data class PreviewFingerprint(
        val goal: CompressionGoal,
        val percentage: Int,
        val targetSizeBytes: Long?,
        val format: ImageOutputFormat,
        val exifData: ExifData?,
        val cropBounds: CropBounds?,
    )

    private fun ImageCompressionConfig.previewFingerprint(): PreviewFingerprint = PreviewFingerprint(
        goal = goal,
        percentage = percentage,
        targetSizeBytes = targetSizeBytes,
        format = format,
        exifData = exifData,
        cropBounds = cropBounds,
    )

    private fun classifySelection(files: List<SelectedFileDescriptor>): SelectionClassification {
        if (files.isEmpty()) {
            return SelectionClassification(SourceCategory.NONE, CompressionType.IMAGE, canProcess = true, message = null)
        }
        val categories = files.map { it.detectCategory() }.toSet()
        if (categories.size > 1) {
            return SelectionClassification(SourceCategory.MIXED, null, canProcess = false, message = "Select files of the same type")
        }
        val category = categories.first()
        return when (category) {
            SourceCategory.IMAGE -> SelectionClassification(category, CompressionType.IMAGE, canProcess = true, message = null)
            SourceCategory.DOCUMENT -> SelectionClassification(
                category,
                type = null,
                canProcess = false,
                message = "DOC/DOCX → PDF conversion requires an additional converter",
            )
            SourceCategory.UNSUPPORTED -> SelectionClassification(category, null, canProcess = false, message = "Unsupported file type")
            SourceCategory.NONE -> SelectionClassification(SourceCategory.NONE, CompressionType.IMAGE, canProcess = false, message = null)
            SourceCategory.MIXED -> SelectionClassification(SourceCategory.MIXED, null, canProcess = false, message = "Select files of the same type")
        }
    }

    private fun SelectedFileDescriptor.detectCategory(): SourceCategory {
        val normalizedMime = mimeType?.lowercase(Locale.getDefault()).orEmpty()
        val extension = displayName.substringAfterLast('.', "").lowercase(Locale.getDefault())
        return when {
            normalizedMime.startsWith("image/") -> SourceCategory.IMAGE
            normalizedMime in DOC_MIME_TYPES -> SourceCategory.DOCUMENT
            extension in IMAGE_EXTENSIONS -> SourceCategory.IMAGE
            extension in DOC_EXTENSIONS -> SourceCategory.DOCUMENT
            else -> SourceCategory.UNSUPPORTED
        }
    }

    private data class SelectionClassification(
        val category: SourceCategory,
        val type: CompressionType?,
        val canProcess: Boolean,
        val message: String?,
    )

    private fun describeFile(uri: Uri): SelectedFileDescriptor {
        val app = getApplication<Application>()
        val resolver = app.contentResolver
        val document = DocumentFile.fromSingleUri(app, uri)
        val displayName = document?.name ?: uri.lastPathSegment ?: "File"
        val size = document?.length()?.takeIf { it >= 0 } ?: resolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else 0L
        } ?: 0L
        val mimeType = document?.type ?: resolver.getType(uri)
        return SelectedFileDescriptor(
            uri = uri,
            displayName = displayName,
            sizeBytes = size,
            mimeType = mimeType,
        )
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            preferencesRepository.defaultDestinationFolder.collect { uriString ->
                _state.update { it.copy(defaultDestinationFolder = uriString) }
                // Preselect the destination if the user has not set one yet
                if (uriString != null && _state.value.destinationFolder == null) {
                    _state.update { state -> state.copy(destinationFolder = Uri.parse(uriString)) }
                }
            }
        }

        viewModelScope.launch {
            preferencesRepository.themeMode.collect { mode ->
                _state.update { it.copy(themeMode = mode) }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun navigateToSettings() {
        _state.update { it.copy(currentScreen = AppScreen.SETTINGS) }
    }

    fun navigateToHome() {
        _state.update { it.copy(currentScreen = AppScreen.HOME) }
    }

    fun navigateToAbout() {
        _state.update { it.copy(currentScreen = AppScreen.ABOUT) }
    }

    fun navigateToDonate() {
        _state.update { it.copy(currentScreen = AppScreen.DONATE) }
    }

    fun setDefaultDestinationFolder(uriString: String) {
        viewModelScope.launch {
            preferencesRepository.setDefaultDestinationFolder(uriString)
        }
    }

    fun clearDefaultDestinationFolder() {
        viewModelScope.launch {
            preferencesRepository.setDefaultDestinationFolder(null)
        }
    }
}
