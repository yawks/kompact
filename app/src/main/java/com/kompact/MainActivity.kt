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
                KompactScreen(
                    state = state,
                    onPickFiles = { 
                        // Use ACTION_GET_CONTENT with chooser to allow selection from gallery apps
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            type = "image/*"
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
                    viewModel.onUrisSelected(listOf(uri))
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let { uris ->
                    if (uris.isNotEmpty()) {
                        viewModel.onUrisSelected(uris)
                    }
                }
            }
        }
    }
}
