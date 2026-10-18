package com.kompact

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.kompact.util.RAW_MIME_TYPES
import com.kompact.util.SourceDeleter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.kompact.ui.CompressionViewModel
import com.kompact.ui.KompactScreen
import com.kompact.ui.theme.KompactTheme

private val SUPPORTED_MIME_TYPES = arrayOf(
    "image/*",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
)

class MainActivity : AppCompatActivity() {

    private val viewModel: CompressionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        setContent {
            val state by viewModel.state.collectAsState()
            KompactTheme(themeMode = state.themeMode) {
                val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if (result.resultCode == RESULT_OK) {
                        result.data?.clipData?.let { clipData ->
                            val uris = mutableListOf<Uri>()
                            for (i in 0 until clipData.itemCount) {
                                clipData.getItemAt(i)?.uri?.let { uris.add(it) }
                            }
                            if (uris.isNotEmpty()) {
                                viewModel.onUrisSelected(uris)
                            }
                        } ?: run {
                            result.data?.data?.let { uri ->
                                viewModel.onUrisSelected(listOf(uri))
                            }
                        }
                    }
                }
                val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                    if (uri != null) {
                        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        runCatching {
                            contentResolver.takePersistableUriPermission(uri, flags)
                        }
                        viewModel.setDestinationFolder(uri)
                    }
                }
                val defaultDestinationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                    if (uri != null) {
                        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        runCatching {
                            contentResolver.takePersistableUriPermission(uri, flags)
                        }
                        viewModel.setDefaultDestinationFolder(uri.toString())
                    }
                }
                // Launcher unificato per tutti i dialog di sistema di eliminazione:
                // - RecoverableSecurityException (Android 10)
                // - MediaStore.createDeleteRequest (Android 11+)
                // - MediaStore.createWriteRequest (Android 11+)
                // - MediaStore.createTrashRequest (Android 12+)
                val deleteRequestLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                    val granted = result.resultCode == RESULT_OK
                    val current = viewModel.state.value
                    when (current.pendingSystemDialogType) {
                        // Android 11+: createDeleteRequest (batch)
                        com.kompact.model.SystemDialogType.DELETE_REQUEST -> {
                            viewModel.onMediaStoreDeleteResult(granted)
                        }
                        // Android 11+: createWriteRequest / createTrashRequest
                        com.kompact.model.SystemDialogType.WRITE_REQUEST,
                        com.kompact.model.SystemDialogType.TRASH_REQUEST -> {
                            viewModel.onWriteAccessResult(granted)
                        }
                        // Android 10: recoverable consent (uno alla volta)
                        null -> {
                            if (current.pendingDeleteConsents.isNotEmpty()) {
                                viewModel.onConsentResult(granted)
                            } else {
                                viewModel.onPendingDeleteResolved(emptyList())
                            }
                        }
                    }
                }
                val storagePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
                    if (grants.values.all { it }) {
                        viewModel.retryPendingDelete()
                    } else {
                        viewModel.onPendingDeleteFailed()
                    }
                }
                val pendingDeleteUris = state.pendingDeleteUris
                val pendingDeleteConsents = state.pendingDeleteConsents
                val pendingWriteAccessUris = state.pendingWriteAccessUris
                val pendingSystemDialogType = state.pendingSystemDialogType
                LaunchedEffect(
                    pendingDeleteUris,
                    pendingDeleteConsents,
                    pendingWriteAccessUris,
                    pendingSystemDialogType,
                ) {
                    // Se un dialog di sistema è in corso, aspetta il risultato
                    if (pendingSystemDialogType != null) return@LaunchedEffect
                    if (pendingDeleteUris.isEmpty() && pendingDeleteConsents.isEmpty() && pendingWriteAccessUris.isEmpty()) {
                        return@LaunchedEffect
                    }

                    // Android 10: gestisci i recoverable consent uno alla volta
                    if (pendingDeleteConsents.isNotEmpty()) {
                        deleteRequestLauncher.launch(
                            IntentSenderRequest.Builder(pendingDeleteConsents.first().intentSender).build()
                        )
                        return@LaunchedEffect
                    }

                    // Se ci sono URI in attesa di createWriteRequest, lancialo
                    if (pendingWriteAccessUris.isNotEmpty()) {
                        val writeSender = SourceDeleter.createWriteAccessRequest(
                            contentResolver, pendingWriteAccessUris
                        )
                        val trashSender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            SourceDeleter.createTrashRequest(contentResolver, pendingWriteAccessUris)
                        } else {
                            null
                        }
                        when {
                            writeSender != null -> {
                                viewModel.setPendingSystemDialog(
                                    pendingWriteAccessUris,
                                    com.kompact.model.SystemDialogType.WRITE_REQUEST
                                )
                                deleteRequestLauncher.launch(
                                    IntentSenderRequest.Builder(writeSender).build()
                                )
                            }
                            trashSender != null -> {
                                viewModel.setPendingSystemDialog(
                                    pendingWriteAccessUris,
                                    com.kompact.model.SystemDialogType.TRASH_REQUEST
                                )
                                deleteRequestLauncher.launch(
                                    IntentSenderRequest.Builder(trashSender).build()
                                )
                            }
                            else -> {
                                // Non è possibile creare il dialog: finalizza con fallimento
                                viewModel.onWriteAccessResult(false)
                            }
                        }
                        return@LaunchedEffect
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        // Android 11+: prova a eliminare ciò che si può con
                        // SourceDeleter, poi usa i dialog di sistema per il resto.
                        val plan = viewModel.prepareSystemDelete()

                        // Se prepareSystemDelete ha già risolto tutto, esci
                        if (viewModel.state.value.pendingDeleteUris.isEmpty()) return@LaunchedEffect

                        if (plan.mediaStoreUris.isNotEmpty()) {
                            // Strategia 1: createDeleteRequest (batch, dialog di sistema)
                            val deleteSender = SourceDeleter.createDeleteRequest(
                                contentResolver, plan.mediaStoreUris
                            )
                            if (deleteSender != null) {
                                viewModel.setPendingMediaStoreDeleteUris(plan.mediaStoreUris)
                                viewModel.setPendingSystemDialog(
                                    plan.mediaStoreUris,
                                    com.kompact.model.SystemDialogType.DELETE_REQUEST
                                )
                                deleteRequestLauncher.launch(
                                    IntentSenderRequest.Builder(deleteSender).build()
                                )
                                return@LaunchedEffect
                            }

                            // Strategia 2: createWriteRequest (accesso in scrittura)
                            val writeSender = SourceDeleter.createWriteAccessRequest(
                                contentResolver, plan.mediaStoreUris
                            )
                            if (writeSender != null) {
                                viewModel.setPendingWriteAccessUris(plan.mediaStoreUris)
                                viewModel.setPendingSystemDialog(
                                    plan.mediaStoreUris,
                                    com.kompact.model.SystemDialogType.WRITE_REQUEST
                                )
                                deleteRequestLauncher.launch(
                                    IntentSenderRequest.Builder(writeSender).build()
                                )
                                return@LaunchedEffect
                            }

                            // Strategia 3 (Android 12+): cestino invece di delete
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val trashSender = SourceDeleter.createTrashRequest(
                                    contentResolver, plan.mediaStoreUris
                                )
                                if (trashSender != null) {
                                    viewModel.setPendingWriteAccessUris(plan.mediaStoreUris)
                                    viewModel.setPendingSystemDialog(
                                        plan.mediaStoreUris,
                                        com.kompact.model.SystemDialogType.TRASH_REQUEST
                                    )
                                    deleteRequestLauncher.launch(
                                        IntentSenderRequest.Builder(trashSender).build()
                                    )
                                    return@LaunchedEffect
                                }
                            }
                        }

                        // Nessuna strategia di sistema disponibile: ritenta il
                        // delete diretto (potrebbe esserci un permesso concesso).
                        viewModel.retryPendingDelete()
                    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        // Android 9 e sotto: legacy storage, serve WRITE_EXTERNAL_STORAGE
                        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                            viewModel.retryPendingDelete()
                        } else {
                            storagePermissionLauncher.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                        }
                    } else {
                        // Android 10 leftovers senza recoverable consent
                        viewModel.onPendingDeleteFailed()
                    }
                }
                KompactScreen(
                    state = state,
                    onPickFiles = {
                        // Use ACTION_GET_CONTENT with chooser to allow selection from gallery apps
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "image/*"
                            // EXTRA_MIME_TYPES deve includere "image/*": alcuni file
                            // manager stock (Samsung My Files, AOSP DocumentsUI, ecc.)
                            // usano EXTRA_MIME_TYPES come filtro esclusivo ignorando
                            // il campo type. Senza "image/*" i file JPEG/PNG escono
                            // grigi e non selezionabili. I MIME Raw extra permettono
                            // di selezionare file Raw anche quando il provider non
                            // mappa l'estensione a un image/x-* (es. octet-stream).
                            putExtra(Intent.EXTRA_MIME_TYPES, (setOf("image/*") + RAW_MIME_TYPES).toTypedArray())
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                            addCategory(Intent.CATEGORY_OPENABLE)
                        }
                        val chooser = Intent.createChooser(intent, "Select images from...")
                        pickLauncher.launch(chooser)
                    },
                    onCompressClick = { viewModel.compress() },
                    onImageConfigChange = { viewModel.updateImageConfig(it) },
                    onRemoveFile = { viewModel.removeFile(it) },
                    onClearSelection = { viewModel.clearSelection() },
                    onPickDestination = { 
                        val initialUri = state.defaultDestinationFolder?.let { Uri.parse(it) }
                        folderLauncher.launch(initialUri)
                    },
                    onClearDestination = { viewModel.clearDestinationFolder() },
                    onExifDataChange = { viewModel.updateExifData(it) },
                    onToggleTextEditor = { viewModel.toggleTextEditor() },
                    onTextsChange = { viewModel.updateTexts(it) },
                    onToggleDrawEditor = { viewModel.toggleDrawEditor() },
                    onDrawConfigChange = { viewModel.updateDrawConfig(it) },
                    onToggleAdjustEditor = { viewModel.toggleAdjustEditor() },
                    onAdjustConfigChange = { viewModel.updateAdjustConfig(it) },
                    onToggleExifEditor = { viewModel.toggleExifEditor() },
                    onToggleCropEditor = { viewModel.toggleCropEditor() },
                    onCropBoundsChange = { viewModel.updateCropBounds(it) },
                    onToggleResizeEditor = { viewModel.toggleResizeEditor() },
                    onResizeConfigChange = { viewModel.updateResizeConfig(it) },
                    onBackgroundConfigChange = { viewModel.updateBackgroundConfig(it) },
                    onToggleColorFilterEditor = { viewModel.toggleColorFilterEditor() },
                    onColorFilterChange = { viewModel.updateColorFilterConfig(it) },
                    onRotate = { viewModel.addRotation(it) },
                    onResetEditsClick = { viewModel.resetImageEdits() },
                    onNavigateToSettings = { viewModel.navigateToSettings() },
                    onNavigateToHome = { viewModel.navigateToHome() },
                    onNavigateToAbout = { viewModel.navigateToAbout() },
                    onNavigateToContact = { viewModel.navigateToContact() },
                    onNavigateToDonate = { viewModel.navigateToDonate() },
                    onThemeModeChange = { viewModel.setThemeMode(it) },
                    onPickDefaultDestination = { defaultDestinationLauncher.launch(null) },
                    onClearDefaultDestination = { viewModel.clearDefaultDestinationFolder() },
                    onDeleteOriginalsChange = { viewModel.updateDeleteOriginals(it) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let { uri ->
                    // Grant read permission for the URI received via intent
                    try {
                        intent.flags = intent.flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (e: Exception) {
                        // Ignore if permission already granted or not supported
                    }
                    viewModel.onUrisSelected(listOf(uri))
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let { uris ->
                    if (uris.isNotEmpty()) {
                        // Grant read permission for all URIs received via intent
                        uris.forEach { uri ->
                            try {
                                intent.flags = intent.flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            } catch (e: Exception) {
                                // Ignore if permission already granted or not supported
                            }
                        }
                        viewModel.onUrisSelected(uris)
                    }
                }
            }
        }
    }
}
