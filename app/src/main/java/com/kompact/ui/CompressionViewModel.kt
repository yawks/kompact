package com.kompact.ui
import com.kompact.R

import android.app.Application
import android.net.Uri
import android.os.Build
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
import com.kompact.model.PendingDeleteConsent
import com.kompact.model.PreviewKind
import com.kompact.model.SelectedFileDescriptor
import com.kompact.model.SourceCategory
import com.kompact.model.SystemDialogType
import com.kompact.model.ThemeMode
import com.kompact.model.withoutVisualEdits
import com.kompact.util.DefaultDestinationResolver
import com.kompact.data.ImagePreviewGenerator
import com.kompact.util.*
import com.kompact.util.readExifData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
private val IMAGE_EXTENSIONS = setOf(
    "jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif", "tif", "tiff", "ico",
    // Formati Raw (decodificati via JPEG embedded, vedi util/RawFormat.kt)
    "dng", "cr2", "cr3", "crw", "nef", "nrw", "arw", "srf", "sr2",
    "orf", "rw2", "raw", "raf", "pef", "ptx", "srw", "x3f",
    "3fr", "fff", "erf", "mos", "mef", "mrw", "kdc", "dcr", "iiq", "gpr",
)

class CompressionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LocalCompressionRepository(application)
    private val preferencesRepository = UserPreferencesRepository(application)
    private val previewGenerator = ImagePreviewGenerator(application)
    private val _state = MutableStateFlow(CompressionUiState())
    val state: StateFlow<CompressionUiState> = _state

    private var currentJob: Job? = null
    private var previewJob: Job? = null
    private var previewDebounceJob: Job? = null

    private companion object {
        // Debounce per la live preview: evita di rigenerare l'anteprima a
        // ogni tick dello slider. La generazione è full-res (necessaria per
        // una stima accurata della dimensione), quindi va lanciata solo dopo
        // che l'utente smette di modificare i parametri.
        const val PREVIEW_DEBOUNCE_MS = 200L
    }

    init {
        _state.update {
            it.copy(
                defaultDownloadsLabel = DefaultDestinationResolver.defaultPicturesLabel(),
            )
        }
        loadPreferences()
    }

    fun onUrisSelected(newUris: List<Uri>) {
        val currentUris = _state.value.selectedUris
        val urisToAdd = newUris.filter { it !in currentUris }
        if (urisToAdd.isEmpty() && newUris.isNotEmpty()) {
            return // nothing new to add
        }
        
        // Append new files instead of replacing, to support "Add more" correctly.
        val combinedUris = currentUris + urisToAdd
        val combinedDescriptors = _state.value.selectedFiles + urisToAdd.map { describeFile(it) }
        
        // Reset custom output filename when new files are loaded to prevent old custom name from applying to entirely new files.
        _state.update { current ->
            current.copy(imageConfig = current.imageConfig.copy(outputFileName = null))
        }
        applySelection(combinedUris, combinedDescriptors)
    }

    fun updateDeleteOriginals(delete: Boolean) {
        _state.update { it.copy(deleteOriginals = delete) }
    }

    fun onPendingDeleteResolved(deletedUris: List<Uri>) {
        // Generico: dopo un dialog di sistema, ritenta il delete su tutti
        // i pendingDeleteUris con SourceDeleter e aggiorna lo stato.
        // I file già eliminati dal sistema o da SourceDeleter vengono
        // rimossi; gli altri vengono segnalati come falliti.
        val context = getApplication<Application>()
        _state.update { current ->
            val allDeleted = mutableSetOf<Uri>()
            val stillFailed = mutableListOf<Uri>()
            current.pendingDeleteUris.forEach { uri ->
                when (SourceDeleter.deleteSource(context, uri)) {
                    is SourceDeleter.DeleteResult.Deleted -> allDeleted += uri
                    is SourceDeleter.DeleteResult.Failed -> stillFailed += uri
                }
            }
            finalizeDeleteFlow(current.copy(
                pendingDeleteUris = emptyList(),
                pendingMediaStoreDeleteUris = emptyList(),
                pendingWriteAccessUris = emptyList(),
                failedDeleteUris = current.failedDeleteUris + stillFailed,
                selectedUris = current.selectedUris.filterNot { it in allDeleted },
                selectedFiles = current.selectedFiles.filterNot { it.uri in allDeleted },
            ))
        }
    }

    fun onConsentResult(granted: Boolean) {
        // Resolution for RecoverableSecurityException consent dialogs (Android 10).
        val context = getApplication<Application>()
        _state.update { current ->
            val head = current.pendingDeleteConsents.firstOrNull() ?: return@update current
            val deleted = granted && when (SourceDeleter.deleteSource(context, head.source)) {
                is SourceDeleter.DeleteResult.Deleted -> true
                is SourceDeleter.DeleteResult.Failed -> false
            }
            finalizeDeleteFlow(current.copy(
                pendingDeleteConsents = current.pendingDeleteConsents.drop(1),
                pendingDeleteUris = current.pendingDeleteUris - head.source,
                failedDeleteUris = if (deleted) current.failedDeleteUris else current.failedDeleteUris + head.source,
                selectedUris = if (deleted) current.selectedUris.filterNot { it == head.source } else current.selectedUris,
                selectedFiles = if (deleted) current.selectedFiles.filterNot { it.uri == head.source } else current.selectedFiles,
            ))
        }
    }

    fun retryPendingDelete() {
        // Retry dopo che l'utente ha concesso permessi o dopo un dialog di
        // sistema (createWriteRequest / createDeleteRequest su Android 11+,
        // WRITE_EXTERNAL_STORAGE su Android 9-).
        val context = getApplication<Application>()
        val deletedUris = mutableListOf<Uri>()
        val stillFailed = mutableListOf<Uri>()
        _state.value.pendingDeleteUris.forEach { uri ->
            when (SourceDeleter.deleteSource(context, uri)) {
                is SourceDeleter.DeleteResult.Deleted -> deletedUris += uri
                is SourceDeleter.DeleteResult.Failed -> stillFailed += uri
            }
        }
        _state.update { current ->
            finalizeDeleteFlow(current.copy(
                pendingDeleteUris = emptyList(),
                failedDeleteUris = current.failedDeleteUris + stillFailed,
                selectedUris = current.selectedUris.filterNot { it in deletedUris },
                selectedFiles = current.selectedFiles.filterNot { it.uri in deletedUris },
            ))
        }
    }

    fun onPendingDeleteFailed() {
        _state.update { current ->
            finalizeDeleteFlow(current.copy(
                pendingDeleteUris = emptyList(),
                pendingMediaStoreDeleteUris = emptyList(),
                pendingWriteAccessUris = emptyList(),
                failedDeleteUris = current.failedDeleteUris + current.pendingDeleteUris,
            ))
        }
    }

    /**
     * Imposta gli URI MediaStore in attesa del dialog createDeleteRequest
     * (Android 11+). Chiamato dall'UI prima di lanciare l'IntentSender.
     */
    fun setPendingMediaStoreDeleteUris(uris: List<Uri>) {
        _state.update { it.copy(pendingMediaStoreDeleteUris = uris) }
    }

    /**
     * Imposta gli URI MediaStore in attesa del dialog createWriteRequest
     * (Android 11+). Chiamato dall'UI prima di lanciare l'IntentSender.
     */
    fun setPendingWriteAccessUris(uris: List<Uri>) {
        _state.update { it.copy(pendingWriteAccessUris = uris) }
    }

    /**
     * Marca un dialog di sistema come "in corso" (lanciato, in attesa di
     * risultato). L'UI usa pendingSystemDialogType per bloccare il
     * LaunchedEffect ed evitare loop.
     */
    fun setPendingSystemDialog(uris: List<Uri>, type: SystemDialogType) {
        _state.update { it.copy(
            pendingSystemDialogUris = uris,
            pendingSystemDialogType = type,
        ) }
    }

    /**
     * Resetta il dialog di sistema in corso (dopo il callback).
     */
    fun clearPendingSystemDialog() {
        _state.update { it.copy(
            pendingSystemDialogUris = emptyList(),
            pendingSystemDialogType = null,
        ) }
    }

    /**
     * Risultato del dialog MediaStore.createDeleteRequest (Android 11+).
     * Ritetta l'eliminazione sugli URI MediaStore richiesti.
     * I file ancora non eliminati vengono messi in attesa di
     * createWriteRequest (pendingWriteAccessUris) per un secondo tentativo
     * con accesso in scrittura concesso dall'utente.
     */
    fun onMediaStoreDeleteResult(granted: Boolean) {
        val context = getApplication<Application>()
        _state.update { current ->
            val targetUris = current.pendingMediaStoreDeleteUris.ifEmpty { current.pendingDeleteUris }
            val deleted = mutableListOf<Uri>()
            val stillFailed = mutableListOf<Uri>()
            targetUris.forEach { uri ->
                when (SourceDeleter.deleteSource(context, uri)) {
                    is SourceDeleter.DeleteResult.Deleted -> deleted += uri
                    is SourceDeleter.DeleteResult.Failed -> stillFailed += uri
                }
            }
            val deletedSet = deleted.toSet()
            val untouchedPending = current.pendingDeleteUris.filterNot { it in targetUris }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && stillFailed.isNotEmpty()) {
                // Metti i falliti in attesa di createWriteRequest
                current.copy(
                    pendingDeleteUris = stillFailed + untouchedPending,
                    pendingMediaStoreDeleteUris = emptyList(),
                    pendingWriteAccessUris = stillFailed,
                    pendingSystemDialogUris = emptyList(),
                    pendingSystemDialogType = null,
                    selectedUris = current.selectedUris.filterNot { it in deletedSet },
                    selectedFiles = current.selectedFiles.filterNot { it.uri in deletedSet },
                )
            } else {
                // Android < 11 o nessun fallimento: finalizza
                finalizeDeleteFlow(current.copy(
                    pendingDeleteUris = emptyList(),
                    pendingMediaStoreDeleteUris = emptyList(),
                    pendingSystemDialogUris = emptyList(),
                    pendingSystemDialogType = null,
                    failedDeleteUris = current.failedDeleteUris + stillFailed + untouchedPending,
                    selectedUris = current.selectedUris.filterNot { it in deletedSet },
                    selectedFiles = current.selectedFiles.filterNot { it.uri in deletedSet },
                ))
            }
        }
    }

    /**
     * Risultato del dialog MediaStore.createWriteRequest (Android 11+).
     * Dopo che l'utente ha concesso accesso in scrittura, ritenta
     * l'eliminazione dei file. Quelli ancora falliti vengono segnalati.
     */
    fun onWriteAccessResult(granted: Boolean) {
        val context = getApplication<Application>()
        _state.update { current ->
            val targetUris = current.pendingWriteAccessUris.ifEmpty { current.pendingDeleteUris }
            val deleted = mutableListOf<Uri>()
            val stillFailed = mutableListOf<Uri>()
            targetUris.forEach { uri ->
                when (SourceDeleter.deleteSource(context, uri)) {
                    is SourceDeleter.DeleteResult.Deleted -> deleted += uri
                    is SourceDeleter.DeleteResult.Failed -> stillFailed += uri
                }
            }
            val deletedSet = deleted.toSet()
            val untouchedPending = current.pendingDeleteUris.filterNot { it in targetUris }
            val allFailed = stillFailed + untouchedPending
            finalizeDeleteFlow(current.copy(
                pendingDeleteUris = emptyList(),
                pendingWriteAccessUris = emptyList(),
                pendingSystemDialogUris = emptyList(),
                pendingSystemDialogType = null,
                failedDeleteUris = current.failedDeleteUris + allFailed,
                selectedUris = current.selectedUris.filterNot { it in deletedSet },
                selectedFiles = current.selectedFiles.filterNot { it.uri in deletedSet },
            ))
        }
    }

    /**
     * Prepara il flujo di eliminazione per Android 11+.
     *
     * 1. Prova a eliminare tutti i pendingDeleteUris con SourceDeleter
     *    (copre URI SAF con DocumentsContract.deleteDocument).
     * 2. Gli URI non-MediaStore non eliminabili vengono segnalati come
     *    falliti definitivamente.
     * 3. Gli URI MediaStore rimanenti restano in pendingDeleteUris e
     *    vengono restituiti per il dialog di sistema createDeleteRequest.
     *
     * @return [SystemDeletePlan] con gli URI MediaStore da passare a
     *   createDeleteRequest, o lista vuota se non ce ne sono.
     */
    fun prepareSystemDelete(): SystemDeletePlan {
        val context = getApplication<Application>()
        val current = _state.value
        val deletedNow = mutableListOf<Uri>()
        val remainingUris = mutableListOf<Uri>()

        current.pendingDeleteUris.forEach { uri ->
            when (SourceDeleter.deleteSource(context, uri)) {
                is SourceDeleter.DeleteResult.Deleted -> deletedNow += uri
                is SourceDeleter.DeleteResult.Failed -> remainingUris += uri
            }
        }

        if (deletedNow.isNotEmpty()) {
            val deletedSet = deletedNow.toSet()
            _state.update { state ->
                state.copy(
                    pendingDeleteUris = state.pendingDeleteUris.filterNot { it in deletedSet },
                    selectedUris = state.selectedUris.filterNot { it in deletedSet },
                    selectedFiles = state.selectedFiles.filterNot { it.uri in deletedSet },
                )
            }
        }

        // Dividi i rimanenti in MediaStore e non-MediaStore
        val mediaStoreUris = remainingUris.mapNotNull { uri ->
            SourceDeleter.toMediaStoreUri(context, uri)
        }.distinct()
        val nonMediaStoreUris = remainingUris.filter { uri ->
            SourceDeleter.toMediaStoreUri(context, uri) == null
        }

        // Aggiorna lo stato: i non-MediaStore falliti vanno in failedDeleteUris,
        // i MediaStore restano in pendingDeleteUris per il dialog di sistema.
        _state.update { state ->
            val newPending = remainingUris.filter { uri ->
                SourceDeleter.toMediaStoreUri(context, uri) != null
            }
            when {
                // Nessun URI MediaStore: finalizza subito con i falliti
                newPending.isEmpty() -> {
                    finalizeDeleteFlow(state.copy(
                        pendingDeleteUris = emptyList(),
                        failedDeleteUris = state.failedDeleteUris + nonMediaStoreUris,
                    ))
                }
                // Ci sono URI MediaStore: restano in pending per il dialog
                else -> {
                    state.copy(
                        pendingDeleteUris = newPending,
                        failedDeleteUris = if (nonMediaStoreUris.isNotEmpty())
                            state.failedDeleteUris + nonMediaStoreUris
                        else
                            state.failedDeleteUris,
                    )
                }
            }
        }

        return SystemDeletePlan(
            mediaStoreUris = mediaStoreUris,
            alreadyDeleted = deletedNow,
        )
    }

    /**
     * Piano d'azione per l'eliminazione tramite dialog di sistema.
     */
    data class SystemDeletePlan(
        val mediaStoreUris: List<Uri>,
        val alreadyDeleted: List<Uri>,
    )

    private fun finalizeDeleteFlow(current: CompressionUiState): CompressionUiState {
        if (current.pendingDeleteUris.isNotEmpty() || current.pendingDeleteConsents.isNotEmpty()) {
            return current
        }
        return current.copy(
            failedDeleteUris = emptyList(),
            pendingSystemDialogUris = emptyList(),
            pendingSystemDialogType = null,
            // Canale dedicato: l'errore di eliminazione viene mostrato nella
            // scheda dei risultati. errorMessage resta riservato agli errori di
            // processamento, che restano visibili nel banner in alto.
            deleteErrorMessage = if (current.failedDeleteUris.isNotEmpty()) {
                getApplication<Application>().getString(R.string.delete_originals_failed)
            } else {
                current.deleteErrorMessage
            },
        )
    }

    fun updatePreserveSourceDate(preserve: Boolean) {
        _state.update { 
            val updatedConfig = it.imageConfig.copy(preserveSourceDate = preserve)
            it.copy(
                imageConfig = updatedConfig,
                preserveSourceDate = preserve
            )
        }
    }

    fun updateImageConfig(config: ImageCompressionConfig) {
        val previous = _state.value.imageConfig
        val prevFingerprint = previous.previewFingerprint()
        val nextFingerprint = config.previewFingerprint()

        _state.update {
            it.copy(
                imageConfig = config,
                results = emptyList(),
                processedCount = 0,
                lastResult = null,
                errorMessage = null,
                deleteErrorMessage = null
            )
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
        clearLivePreview()
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
                deleteErrorMessage = null,
                results = emptyList(),
                lastResult = null,
                totalOriginalBytes = 0,
                totalCompressedBytes = 0,
                isExifEditorVisible = false,
                isCropEditorVisible = false,
                imageConfig = it.imageConfig.withoutVisualEdits().copy(outputFileName = null)
            )
        }
    }

    fun setDestinationFolder(uri: Uri?) {
        _state.update { 
            it.copy(
                destinationFolder = uri,
                results = emptyList(),
                processedCount = 0,
                lastResult = null,
                errorMessage = null,
                deleteErrorMessage = null
            ) 
        }
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
                deleteErrorMessage = null,
                totalCount = uris.size,
                processedCount = 0,
                totalOriginalBytes = 0,
                totalCompressedBytes = 0,
                results = emptyList(),
                lastResult = null,
                imageConfig = current.imageConfig.withoutVisualEdits().copy(
                    targetSizeBytes = null,
                    exifData = exifData,
                    originalExifData = exifData,
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
            val canEdit = state.sourceCategory == SourceCategory.IMAGE 
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

    fun toggleColorFilterEditor() {
        _state.update { state -> state.copy(isColorFilterEditorVisible = !state.isColorFilterEditorVisible) }
    }

    fun updateColorFilterConfig(config: com.kompact.model.ColorFilterConfig?) {
        _state.update { current ->
            current.copy(imageConfig = current.imageConfig.copy(colorFilterConfig = config))
        }
        scheduleLivePreview()
    }

    fun addRotation(degrees: Float = 90f) {
        _state.update { current ->
            val currentRot = current.imageConfig.rotationDegrees
            current.copy(imageConfig = current.imageConfig.copy(rotationDegrees = (currentRot + degrees) % 360f))
        }
        scheduleLivePreview()
    }

    fun toggleResizeEditor() {
        _state.update { state ->
            val canResize = state.sourceCategory == SourceCategory.IMAGE
            val nextVisible = if (canResize) !state.isResizeEditorVisible else false
            state.copy(isResizeEditorVisible = nextVisible)
        }
    }

    fun updateResizeConfig(config: com.kompact.model.ResizeConfig?) {
        _state.update { current ->
            current.copy(imageConfig = current.imageConfig.copy(resizeConfig = config))
        }
        scheduleLivePreview()
    }

    fun toggleBackgroundEditor() {
        _state.update { state ->
            val canEditBg = state.sourceCategory == SourceCategory.IMAGE
            val nextVisible = if (canEditBg) !state.isBackgroundEditorVisible else false
            state.copy(isBackgroundEditorVisible = nextVisible)
        }
    }

    fun updateBackgroundConfig(config: com.kompact.model.BackgroundConfig?) {
        _state.update { current ->
            current.copy(imageConfig = current.imageConfig.copy(backgroundConfig = config))
        }
        scheduleLivePreview()
    }

    fun resetImageEdits() {
        _state.update { current ->
            // Ripristina i dati EXIF originali dell'immagine sorgente
            val originalExif = current.originalExifData ?: current.imageConfig.originalExifData
            // Reset completo: edit visivi, compressione, formato, EXIF modificati.
            // Rimangono solo le preferenze utente (preserveSourceDate) e i file selezionati.
            current.copy(
                imageConfig = ImageCompressionConfig(
                    exifData = originalExif,
                    originalExifData = originalExif,
                    preserveSourceDate = current.imageConfig.preserveSourceDate,
                ),
                exifData = originalExif,
                stripExif = false,
                results = emptyList(),
                processedCount = 0,
                lastResult = null,
                errorMessage = null,
                deleteErrorMessage = null,
                totalOriginalBytes = 0,
                totalCompressedBytes = 0,
                isExifEditorVisible = false,
                isCropEditorVisible = false,
                isTextEditorVisible = false,
                isResizeEditorVisible = false,
                isBackgroundEditorVisible = false,
                isColorFilterEditorVisible = false,
                isDrawEditorVisible = false,
                isAdjustEditorVisible = false,
            )
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
                    deleteErrorMessage = null,
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
            val deletedSources = mutableSetOf<Uri>()
            val undeletableSources = mutableListOf<Uri>()
            val deleteConsents = mutableListOf<PendingDeleteConsent>()

            if (snapshot.deleteOriginals) {
                val context = getApplication<Application>()
                results
                    .asSequence()
                    .filter { it.success && it.output != null && it.source != it.output }
                    .map { it.source }
                    .distinct()
                    .forEach { sourceUri ->
                        // SourceDeleter applica in ordine: DocumentsContract,
                        // DocumentFile, ContentResolver, file://, verifica
                        // esistenza. Su Android 10 restituisce un
                        // RecoverableSecurityException come consent.
                        when (val result = SourceDeleter.deleteSource(context, sourceUri)) {
                            is SourceDeleter.DeleteResult.Deleted -> {
                                deletedSources += sourceUri
                            }
                            is SourceDeleter.DeleteResult.Failed -> {
                                // Android 10: RecoverableSecurityException →
                                // salva il consent per il dialog di sistema.
                                result.recoverableSender?.let { sender ->
                                    deleteConsents += PendingDeleteConsent(
                                        source = sourceUri,
                                        intentSender = sender,
                                    )
                                }
                                // Senza recoverable sender, il file va in
                                // undeletableSources: l'UI proverà con
                                // MediaStore.createWriteRequest (Android 11+)
                                // o MediaStore.createDeleteRequest.
                                if (result.recoverableSender == null) {
                                    undeletableSources += sourceUri
                                }
                            }
                        }
                    }
            }

            _state.update {
                it.copy(
                    isProcessing = false,
                    results = results,
                    processedCount = results.size,
                    totalCount = results.size,
                    totalOriginalBytes = finalOriginal,
                    totalCompressedBytes = totalCompressedBytes,
                    lastResult = results.lastOrNull()?.copy(
                        message = getApplication<Application>().getString(R.string.processing_completed, finalOriginal.asReadableBytes(), totalCompressedBytes.asReadableBytes())
                    ),
                    errorMessage = if (hasSuccess) null else results.lastOrNull()?.message,
                    selectedUris = it.selectedUris.filterNot { uri -> uri in deletedSources },
                    selectedFiles = it.selectedFiles.filterNot { file -> file.uri in deletedSources },
                    pendingDeleteUris = (undeletableSources + deleteConsents.map { consent -> consent.source }).distinct(),
                    pendingDeleteConsents = deleteConsents,
                )
            }
            clearLivePreview()
        }
    }

    private fun scheduleLivePreview() {
        previewDebounceJob?.cancel()
        previewDebounceJob = viewModelScope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
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
                }.onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    e.printStackTrace()
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
                // Nota: NON scrivere batchEstimate in imageConfig.targetSizeBytes:
                // con goal TARGET_SIZE quello è il target scelto dall'utente e
                // sovrascriverlo con la stima creerebbe un loop a spirale
                // (stima -> nuovo target -> nuova stima più bassa -> ...).
                // La stima vive solo in livePreview.estimatedBatchBytes.
                state.copy(
                    livePreview = LivePreviewState(
                        kind = PreviewKind.IMAGE,
                        previews = previewResults,
                        estimatedBatchBytes = batchEstimate,
                        isGenerating = false,
                        errorMessage = null,
                    )
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
        previewDebounceJob?.cancel()
        previewJob?.cancel()
        previewGenerator.clear()
        _state.update { it.copy(livePreview = LivePreviewState()) }
    }

    override fun onCleared() {
        super.onCleared()
        currentJob?.cancel()
        previewDebounceJob?.cancel()
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
        val resizeConfig: com.kompact.model.ResizeConfig?,
        val colorFilterConfig: com.kompact.model.ColorFilterConfig?,
        val backgroundConfig: com.kompact.model.BackgroundConfig?,
        val texts: List<com.kompact.model.TextOverlayConfig>,
        val drawConfig: com.kompact.model.DrawConfig?,
        val adjustConfig: com.kompact.model.AdjustConfig?,
        val rotationDegrees: Float,
        val stripExif: Boolean,
        val preserveSourceDate: Boolean,
    )

    private fun ImageCompressionConfig.previewFingerprint(): PreviewFingerprint = PreviewFingerprint(
        goal = goal,
        percentage = percentage,
        targetSizeBytes = targetSizeBytes,
        format = format,
        exifData = exifData,
        cropBounds = cropBounds,
        resizeConfig = resizeConfig,
        colorFilterConfig = colorFilterConfig,
        backgroundConfig = backgroundConfig,
        texts = texts,
        drawConfig = drawConfig,
        adjustConfig = adjustConfig,
        rotationDegrees = rotationDegrees,
        stripExif = stripExif,
        preserveSourceDate = preserveSourceDate
    )

    private fun classifySelection(files: List<SelectedFileDescriptor>): SelectionClassification {
        if (files.isEmpty()) {
            return SelectionClassification(SourceCategory.NONE, CompressionType.IMAGE, canProcess = true, message = null)
        }
        val categories = files.map { it.detectCategory() }.toSet()
        if (categories.size > 1) {
            return SelectionClassification(SourceCategory.MIXED, null, canProcess = false, message = getApplication<Application>().getString(R.string.select_files_same_type))
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
            SourceCategory.MIXED -> SelectionClassification(SourceCategory.MIXED, null, canProcess = false, message = getApplication<Application>().getString(R.string.select_files_same_type))
        }
    }

    private fun SelectedFileDescriptor.detectCategory(): SourceCategory {
        val normalizedMime = mimeType?.lowercase(Locale.getDefault()).orEmpty()
        val extension = displayName.substringAfterLast('.', "").lowercase(Locale.getDefault())
        return when {
            normalizedMime.startsWith("image/") -> SourceCategory.IMAGE
            normalizedMime in RAW_MIME_TYPES -> SourceCategory.IMAGE
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
        
        return try {
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
            SelectedFileDescriptor(
                uri = uri,
                displayName = displayName,
                sizeBytes = size,
                mimeType = mimeType,
            )
        } catch (e: Exception) {
            // Fallback for URIs that can't be described via DocumentFile
            SelectedFileDescriptor(
                uri = uri,
                displayName = uri.lastPathSegment ?: "File",
                sizeBytes = 0L,
                mimeType = null,
            )
        }
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

    fun navigateToContact() {
        _state.update { it.copy(currentScreen = AppScreen.CONTACT) }
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
    fun toggleTextEditor() {
        _state.update { state ->
            val canText = state.sourceCategory == com.kompact.model.SourceCategory.IMAGE && state.selectedFiles.size == 1
            val nextVisible = if (canText) !state.isTextEditorVisible else false
            state.copy(isTextEditorVisible = nextVisible)
        }
    }

    fun updateTexts(texts: List<com.kompact.model.TextOverlayConfig>) {
        _state.update { current ->
            val updatedConfig = current.imageConfig.copy(texts = texts)
            current.copy(imageConfig = updatedConfig)
        }
        scheduleLivePreview()
    }

    fun toggleDrawEditor() {
        _state.update { state ->
            val canDraw = state.sourceCategory == com.kompact.model.SourceCategory.IMAGE && state.selectedFiles.size == 1
            val nextVisible = if (canDraw) !state.isDrawEditorVisible else false
            state.copy(isDrawEditorVisible = nextVisible)
        }
    }

    fun updateDrawConfig(config: com.kompact.model.DrawConfig?) {
        _state.update { current ->
            val updatedConfig = current.imageConfig.copy(drawConfig = config)
            current.copy(imageConfig = updatedConfig)
        }
        scheduleLivePreview()
    }

    fun toggleAdjustEditor() {
        _state.update { state ->
            val canAdjust = state.sourceCategory == com.kompact.model.SourceCategory.IMAGE && state.selectedFiles.size == 1
            val nextVisible = if (canAdjust) !state.isAdjustEditorVisible else false
            state.copy(isAdjustEditorVisible = nextVisible)
        }
    }

    fun updateAdjustConfig(config: com.kompact.model.AdjustConfig?) {
        _state.update { current ->
            val updatedConfig = current.imageConfig.copy(adjustConfig = config)
            current.copy(imageConfig = updatedConfig)
        }
        scheduleLivePreview()
    }
}

