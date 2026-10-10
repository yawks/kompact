package com.kompact

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.kompact.util.RAW_MIME_TYPES
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

private fun toMediaStoreUri(uri: Uri): Uri? {
    return when (uri.authority) {
        MediaStore.AUTHORITY -> uri
        "com.android.providers.media.documents" -> {
            val documentId = DocumentsContract.getDocumentId(uri)
            val type = documentId.substringBefore(':', "")
            val id = documentId.substringAfter(':', "").toLongOrNull() ?: return null
            val collection = when (type) {
                "images" -> MediaStore.Images.Media.getContentUri("external")
                "video" -> MediaStore.Video.Media.getContentUri("external")
                "audio" -> MediaStore.Audio.Media.getContentUri("external")
                else -> return null
            }
            ContentUris.withAppendedId(collection, id)
        }
        else -> null
    }
}

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
                val deleteRequestLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                    val current = viewModel.state.value
                    if (current.pendingDeleteConsents.isNotEmpty()) {
                        // Android 10 recoverable consent: retry the delete of the head item.
                        viewModel.onConsentResult(result.resultCode == RESULT_OK)
                    } else {
                        // Android 11+ MediaStore delete request.
                        val deletedOriginals = if (result.resultCode == RESULT_OK) {
                            current.pendingDeleteUris.filter { toMediaStoreUri(it) != null }
                        } else {
                            emptyList()
                        }
                        viewModel.onPendingDeleteResolved(deletedOriginals)
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
                LaunchedEffect(pendingDeleteUris, pendingDeleteConsents) {
                    if (pendingDeleteUris.isEmpty() && pendingDeleteConsents.isEmpty()) return@LaunchedEffect
                    if (pendingDeleteConsents.isNotEmpty()) {
                        // Android 10: one recoverable consent dialog at a time.
                        deleteRequestLauncher.launch(IntentSenderRequest.Builder(pendingDeleteConsents.first().intentSender).build())
                        return@LaunchedEffect
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val requestUris = pendingDeleteUris.mapNotNull { toMediaStoreUri(it) }.distinct()
                        if (requestUris.isNotEmpty()) {
                            val sender = runCatching {
                                MediaStore.createDeleteRequest(contentResolver, requestUris).intentSender
                            }.getOrNull()
                            if (sender != null) {
                                deleteRequestLauncher.launch(IntentSenderRequest.Builder(sender).build())
                                return@LaunchedEffect
                            }
                        }
                        viewModel.onPendingDeleteFailed()
                    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        // Android 9 and below: legacy storage allows a direct delete once the runtime permission is granted.
                        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                            viewModel.retryPendingDelete()
                        } else {
                            storagePermissionLauncher.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                        }
                    } else {
                        // Android 10 leftovers without a recoverable consent: nothing else to try.
                        viewModel.onPendingDeleteFailed()
                    }
                }
                KompactScreen(
                    state = state,
                    onPickFiles = { 
                        // Use ACTION_GET_CONTENT with chooser to allow selection from gallery apps
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "image/*"
                            // Aggiungi i MIME Raw oltre a image/*: permette di
                            // selezionare file Raw anche quando il provider non
                            // mappa l'estensione a un image/x-* (es. octet-stream).
                            putExtra(Intent.EXTRA_MIME_TYPES, RAW_MIME_TYPES.toTypedArray())
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
