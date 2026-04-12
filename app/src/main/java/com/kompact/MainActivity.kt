package com.kompact

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.kompact.ui.CompressionViewModel
import com.kompact.ui.KompactScreen
import com.kompact.ui.theme.KompactTheme
import android.net.Uri

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
        setContent {
            val state by viewModel.state.collectAsState()
            KompactTheme(themeMode = state.themeMode) {
                val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                    if (uris.isNotEmpty()) {
                        viewModel.onUrisSelected(uris)
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
                KompactScreen(
                    state = state,
                    onPickFiles = { pickLauncher.launch(SUPPORTED_MIME_TYPES) },
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
                    onTextConfigChange = { viewModel.updateTextConfig(it) },
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
                )
            }
        }
    }
}
