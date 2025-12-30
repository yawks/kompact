package com.kompact.ui

import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import androidx.compose.runtime.saveable.rememberSaveable
import com.kompact.ui.theme.KompactTheme
import com.kompact.util.asReadableBytes
import com.kompact.util.parseBytes
import com.kompact.model.AppScreen
import com.kompact.model.CompressionType
import com.kompact.model.CompressionUiState
import com.kompact.model.ExifData
import com.kompact.model.ImageCompressionConfig
import com.kompact.model.ImageOutputFormat
import com.kompact.model.LivePreviewState
import com.kompact.model.PreviewKind
import com.kompact.model.SelectedFileDescriptor
import com.kompact.model.SourceCategory
import com.kompact.model.CompressionResult
import com.kompact.model.CropBounds
import com.kompact.model.ImagePreviewPayload
import com.kompact.model.ThemeMode
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import com.kompact.model.CompressionGoal
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun KompactScreen(
    state: com.kompact.model.CompressionUiState,
    onPickFiles: () -> Unit,
    onCompressClick: () -> Unit,
    onImageConfigChange: (com.kompact.model.ImageCompressionConfig) -> Unit,
    onRemoveFile: (android.net.Uri) -> Unit,
    onClearSelection: () -> Unit,
    onPickDestination: () -> Unit,
    onClearDestination: () -> Unit,
    onExifDataChange: (com.kompact.model.ExifData?) -> Unit,
    onToggleExifEditor: () -> Unit,
    onToggleCropEditor: () -> Unit,
    onCropBoundsChange: (com.kompact.model.CropBounds?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToDonate: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onPickDefaultDestination: () -> Unit,
    onClearDefaultDestination: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text("Kompact", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall)
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text("Home") },
                    selected = state.currentScreen == AppScreen.HOME,
                    onClick = {
                        onNavigateToHome()
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = state.currentScreen == AppScreen.SETTINGS,
                    onClick = {
                        onNavigateToSettings()
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("About") },
                    selected = state.currentScreen == AppScreen.ABOUT,
                    onClick = {
                        onNavigateToAbout()
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("Donate") },
                    selected = state.currentScreen == AppScreen.DONATE,
                    onClick = {
                        onNavigateToDonate()
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                val uriHandler = LocalUriHandler.current
                NavigationDrawerItem(
                    label = { Text("Report Issue") },
                    selected = false,
                    onClick = {
                        uriHandler.openUri("https://git.naxod.com/luca/Kompact/issues")
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Filled.BugReport, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    title = {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(text = "Kompact", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "Compress images easily",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { innerPadding ->
            when (state.currentScreen) {
                AppScreen.HOME -> HomeScreen(
                    state = state,
                    onPickFiles = onPickFiles,
                    onCompressClick = onCompressClick,
                    onImageConfigChange = onImageConfigChange,
                    onRemoveFile = onRemoveFile,
                    onClearSelection = onClearSelection,
                    onPickDestination = onPickDestination,
                    onClearDestination = onClearDestination,
                    onExifDataChange = onExifDataChange,
                    onToggleExifEditor = onToggleExifEditor,
                    onToggleCropEditor = onToggleCropEditor,
                    onCropBoundsChange = onCropBoundsChange,
                    modifier = modifier.padding(innerPadding)
                )
                AppScreen.SETTINGS -> SettingsScreen(
                    defaultDestinationFolder = state.defaultDestinationFolder,
                    themeMode = state.themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onPickDefaultDestination = onPickDefaultDestination,
                    onClearDefaultDestination = onClearDefaultDestination,
                    modifier = modifier.padding(innerPadding)
                )
                AppScreen.ABOUT -> AboutScreen(modifier = modifier.padding(innerPadding))
                AppScreen.DONATE -> DonateScreen(modifier = modifier.padding(innerPadding))
            }
        }
    }
}

@Composable
fun HomeScreen(
    state: CompressionUiState,
    onPickFiles: () -> Unit,
    onCompressClick: () -> Unit,
    onImageConfigChange: (ImageCompressionConfig) -> Unit,
    onRemoveFile: (Uri) -> Unit,
    onClearSelection: () -> Unit,
    onPickDestination: () -> Unit,
    onClearDestination: () -> Unit,
    onExifDataChange: (ExifData?) -> Unit,
    onToggleExifEditor: () -> Unit,
    onToggleCropEditor: () -> Unit,
    onCropBoundsChange: (CropBounds?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstImageDescriptor = remember(state.selectedFiles) {
        state.selectedFiles.firstOrNull { it.mimeType?.startsWith("image/") == true }
    }
    val defaultFormatLabel = remember(firstImageDescriptor) {
        inferDefaultFormatLabel(firstImageDescriptor)
    }
    val singleImageSelected = state.sourceCategory == SourceCategory.IMAGE && state.selectedFiles.size == 1
    val exifEditingEnabled = singleImageSelected
    val originalFormat = remember(firstImageDescriptor) {
        inferOriginalFormat(firstImageDescriptor)
    }
    val defaultDestinationLabel = remember(
        state.defaultDestinationFolder,
        state.defaultPicturesLabel,
    ) {
        state.defaultDestinationFolder?.let { getFolderDisplayName(Uri.parse(it)) } ?: state.defaultPicturesLabel.ifBlank { "Pictures / Kompact" }
    }
    val defaultFileName = remember(state.selectedFiles) {
        if (state.selectedFiles.size == 1) {
            val file = state.selectedFiles.first()
            file.displayName.substringBeforeLast('.', "").ifBlank { file.displayName }
        } else null
    }
    val referenceBytes = state.selectedFiles.sumOf { it.sizeBytes }.coerceAtLeast(1L)
    val showLivePreviewCard = remember(state.livePreview) {
        state.livePreview.kind != PreviewKind.NONE ||
            state.livePreview.isGenerating ||
            state.livePreview.errorMessage != null
    }
    val cropApplied = remember(state.imageConfig.cropBounds) {
        state.imageConfig.cropBounds?.isFull() == false
    }
    LaunchedEffect(state.selectedFiles) {
        if (state.selectedFiles.isNotEmpty()) {
            when (state.sourceCategory) {
                SourceCategory.IMAGE -> onImageConfigChange(state.imageConfig.copy(percentage = 0, goal = CompressionGoal.PERCENTAGE, targetSizeBytes = null))
                else -> {}
            }
        }
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SourceCard(
                files = state.selectedFiles,
                selectionMessage = state.selectionMessage,
                onRemoveFile = onRemoveFile,
                onClearSelection = onClearSelection,
                onPickFiles = onPickFiles,
                errorMessage = state.errorMessage,
            )
        }
        if (showLivePreviewCard) {
            item {
                LivePreviewCard(
                    livePreview = state.livePreview,
                    canCrop = singleImageSelected,
                    isCropEditorVisible = state.isCropEditorVisible,
                    isCropApplied = cropApplied,
                    onCropClick = onToggleCropEditor,
                )
            }
        }
        if (state.selectedFiles.isNotEmpty()) {
            item {
                OutputCard(
                    category = state.sourceCategory,
                    imageConfig = state.imageConfig,
                    onImageConfigChange = onImageConfigChange,
                    defaultFormatLabel = defaultFormatLabel,
                    originalFormat = originalFormat,
                    defaultFileName = defaultFileName,
                    destinationFolder = state.destinationFolder,
                    defaultDestinationLabel = defaultDestinationLabel,
                    onPickDestination = onPickDestination,
                    onClearDestination = onClearDestination,
                    referenceBytes = referenceBytes,
                    liveEstimateBytes = state.livePreview.estimatedBatchBytes.takeIf { state.livePreview.kind == PreviewKind.IMAGE && it > 0L },
                    selectedFilesCount = state.selectedFiles.size,
                    exifEditingEnabled = exifEditingEnabled,
                    isEditingExif = state.isExifEditorVisible,
                    onToggleExifEditor = onToggleExifEditor,
                )
            }
            item {
                PrimaryActionBar(
                    isProcessing = state.isProcessing,
                    canCompress = state.canProcessSelection && !state.isProcessing,
                    onCompressClick = onCompressClick
                )
            }
        }
        if (state.isProcessing || state.processedCount > 0) {
            item { ProgressCard(state = state) }
        }
        if (state.results.isNotEmpty()) {
            item { ResultsCard(state.results) }
        }
    }

    if (state.isExifEditorVisible && exifEditingEnabled) {
        ExifEditorDialog(
            exifData = state.exifData,
            originalExifData = state.originalExifData,
            onExifDataChange = onExifDataChange,
            onDismiss = onToggleExifEditor,
        )
    }

    if (state.isCropEditorVisible && singleImageSelected) {
        CropEditorDialog(
            imageUri = state.selectedFiles.firstOrNull()?.uri,
            cropBounds = state.imageConfig.cropBounds,
            onCropBoundsChange = onCropBoundsChange,
            onDismiss = onToggleCropEditor,
        )
    }
}

@Composable
fun SettingsScreen(
    defaultDestinationFolder: String?,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onPickDefaultDestination: () -> Unit,
    onClearDefaultDestination: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium) }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Default destination", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = defaultDestinationFolder?.let { getFolderDisplayName(Uri.parse(it)) } ?: "Not set",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onPickDefaultDestination) {
                            Icon(Icons.Filled.Folder, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Destination folder")
                        }
                        TextButton(onClick = onClearDefaultDestination, enabled = defaultDestinationFolder != null) {
                            Text("Reset default")
                        }
                    }
                }
            }
        }

        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Theme", style = MaterialTheme.typography.titleMedium)

                    fun labelFor(mode: ThemeMode): String = when (mode) {
                        ThemeMode.SYSTEM -> "System"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                    }

                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onThemeModeChange(mode) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = { onThemeModeChange(mode) }
                            )
                            Text(labelFor(mode), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("About Kompact", style = MaterialTheme.typography.headlineMedium) }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mission", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Kompact is an offline file compressor designed to run entirely on-device. Your files never leave your phone, ensuring complete privacy.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current Features", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "• Precision Compression: Target specific file sizes or quality percentages.\n" +
                        "• Format Conversion: Switch easily between JPG, PNG, WebP, and more.\n" +
                        "• Smart Editing: Crop images and manage EXIF metadata (view, edit, or strip) before processing.\n" +
                        "• Live Preview: Compare original vs. compressed results in real-time.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Roadmap", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "We are working to make Kompact the ultimate optimizer. Future updates will bring support for PDF documents, Audio tracks, and Video files.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Open Source", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "This project is open-source under the Apache 2.0 license. You can inspect the code, report issues, or contribute on our repository.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val uriHandler = LocalUriHandler.current
                    TextButton(onClick = { uriHandler.openUri("https://git.naxod.com/luca/Kompact") }) {
                        Text("Visit Repository")
                    }
                }
            }
        }
    }
}

@Composable
fun DonateScreen(modifier: Modifier = Modifier) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Support Development", style = MaterialTheme.typography.headlineMedium) }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A Personal Note", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "I built Kompact because I believe essential tools should be simple, private, and free of ads or tracking. I work on this in my spare time, driven by the idea of making a useful utility for everyone.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Support", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "If Kompact has saved you space or time, please consider supporting its development. Your donation directly helps me dedicate more hours to adding features like PDF, Audio and Video support, and keeps the project independent and sustainable.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = { uriHandler.openUri("https://liberapay.com/naxod/") }) {
                        Icon(Icons.Default.Favorite, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Donate")
                    }
                }
            }
        }
    }
}


@Composable
fun SourceCard(
    files: List<SelectedFileDescriptor>,
    selectionMessage: String?,
    onRemoveFile: (Uri) -> Unit,
    onClearSelection: () -> Unit,
    onPickFiles: () -> Unit,
    errorMessage: String?,
) {
    Card {
        val helperText = remember(files) {
            if (files.isEmpty()) "Select images" else "${files.size} item(s) selected"
        }
        val selectLabel = if (files.isEmpty()) "Select files" else "Add more"
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Files", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = helperText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onPickFiles) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(selectLabel)
                }
                TextButton(
                    onClick = onClearSelection,
                    enabled = files.isNotEmpty()
                ) {
                    Text("Clear all")
                }
            }
            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (selectionMessage != null) {
                Text(selectionMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (files.isEmpty()) {
                SourceEmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(count = files.size) { index ->
                        val file = files[index]
                        if (file.mimeType?.startsWith("image/") == true) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = file.uri,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(file.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(file.sizeBytes.asReadableBytes(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { onRemoveFile(file.uri) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove")
                                    }
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(file.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(file.sizeBytes.asReadableBytes(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onRemoveFile(file.uri) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        Text("Select images to compress", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@Composable
fun PrimaryActionBar(
    isProcessing: Boolean,
    canCompress: Boolean,
    onCompressClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = if (isProcessing) "Processing..." else "Ready to process",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Button(
                onClick = onCompressClick,
                enabled = canCompress,
                modifier = Modifier.height(48.dp)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Working…")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start")
                }
            }
        }
    }
}

@Composable
fun OutputCard(
    category: SourceCategory,
    imageConfig: ImageCompressionConfig,
    onImageConfigChange: (ImageCompressionConfig) -> Unit,
    defaultFormatLabel: String?,
    originalFormat: ImageOutputFormat?,
    defaultFileName: String?,
    destinationFolder: Uri?,
    defaultDestinationLabel: String,
    onPickDestination: () -> Unit,
    onClearDestination: () -> Unit,
    referenceBytes: Long,
    liveEstimateBytes: Long?,
    selectedFilesCount: Int,
    exifEditingEnabled: Boolean,
    isEditingExif: Boolean,
    onToggleExifEditor: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Output", style = MaterialTheme.typography.titleMedium)
                if (exifEditingEnabled) {
                    TextButton(onClick = onToggleExifEditor) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isEditingExif) "Close EXIF" else "Edit EXIF")
                    }
                }
            }
            if (category == SourceCategory.IMAGE) {
                ImageConfigSection(
                    config = imageConfig,
                    onConfigChange = onImageConfigChange,
                    defaultFormatLabel = defaultFormatLabel,
                    originalFormat = originalFormat,
                    defaultFileName = defaultFileName,
                    referenceBytes = referenceBytes,
                    liveEstimateBytes = liveEstimateBytes,
                    selectedFilesCount = selectedFilesCount,
                )
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val destinationLabel = destinationFolder?.let { getFolderDisplayName(it) } ?: defaultDestinationLabel
                OutlinedButton(
                    onClick = onPickDestination,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Destination folder", style = MaterialTheme.typography.labelSmall)
                        Text(destinationLabel, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                TextButton(onClick = onClearDestination, enabled = destinationFolder != null) {
                    Text("Default")
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ImageConfigSection(
    config: ImageCompressionConfig,
    onConfigChange: (ImageCompressionConfig) -> Unit,
    defaultFormatLabel: String?,
    originalFormat: ImageOutputFormat?,
    defaultFileName: String?,
    referenceBytes: Long,
    liveEstimateBytes: Long?,
    selectedFilesCount: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Format", style = MaterialTheme.typography.titleSmall)
        var expanded by remember { mutableStateOf(false) }
        val formatOptions = remember(originalFormat) {
            ImageOutputFormat.entries.filterNot { format ->
                originalFormat != null && format != ImageOutputFormat.ORIGINAL && format == originalFormat
            }
        }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = formatDisplayLabel(config.format, defaultFormatLabel),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                formatOptions.forEach { format ->
                    DropdownMenuItem(
                        text = { Text(formatDisplayLabel(format, defaultFormatLabel)) },
                        onClick = {
                            onConfigChange(config.copy(format = format))
                            expanded = false
                        }
                    )
                }
            }
        }
        Text("Quality", style = MaterialTheme.typography.titleSmall)
        val configPercentage = when (config.goal) {
            CompressionGoal.PERCENTAGE -> config.percentage
            CompressionGoal.TARGET_SIZE -> if (config.targetSizeBytes == null) config.percentage else (config.targetSizeBytes.toFloat().div(referenceBytes).times(100)).roundToInt()
        }.coerceIn(0, 100)

        var isDragging by remember { mutableStateOf(false) }
        var sliderPercentage by remember { mutableIntStateOf(configPercentage) }
        LaunchedEffect(configPercentage) {
            if (!isDragging) {
                sliderPercentage = configPercentage
            }
        }

        val shownPercentage = if (isDragging) sliderPercentage else configPercentage
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Slider(
                value = shownPercentage.toFloat(),
                onValueChange = { newValue ->
                    isDragging = true
                    sliderPercentage = newValue.roundToInt().coerceIn(0, 100)
                },
                onValueChangeFinished = {
                    val newPercentage = sliderPercentage.coerceIn(0, 100)
                    isDragging = false
                    if (newPercentage == 0) {
                        onConfigChange(config.copy(percentage = 0, targetSizeBytes = null))
                    } else {
                        when (config.goal) {
                            CompressionGoal.PERCENTAGE -> onConfigChange(config.copy(percentage = newPercentage, targetSizeBytes = null))
                            CompressionGoal.TARGET_SIZE -> {
                                val newTarget = (referenceBytes * newPercentage / 100.0).roundToInt().toLong().coerceAtLeast(1)
                                onConfigChange(config.copy(targetSizeBytes = newTarget))
                            }
                        }
                    }
                },
                valueRange = 0f..100f,
                steps = 100,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = if (shownPercentage == 0) "" else shownPercentage.toString(),
                onValueChange = { newValue ->
                    val newPercentage = if (newValue.isBlank()) 0 else newValue.toIntOrNull()?.coerceIn(0, 100) ?: shownPercentage
                    if (newPercentage == 0) {
                        onConfigChange(config.copy(percentage = 0, targetSizeBytes = null))
                    } else {
                        when (config.goal) {
                            CompressionGoal.PERCENTAGE -> onConfigChange(config.copy(percentage = newPercentage, targetSizeBytes = null))
                            CompressionGoal.TARGET_SIZE -> {
                                val newTarget = (referenceBytes * newPercentage / 100.0).roundToInt().toLong().coerceAtLeast(1)
                                onConfigChange(config.copy(targetSizeBytes = newTarget))
                            }
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(80.dp)
            )
        }
        if (liveEstimateBytes != null && selectedFilesCount > 0) {
            val totalEstimate = liveEstimateBytes * selectedFilesCount
            Text("Estimated total: ${totalEstimate.asReadableBytes()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (defaultFileName != null) {
            // Keep a draft independent from preview recalculations; do not re-inject the default name once the user cleared it.
            var fileNameDraft by rememberSaveable(defaultFileName) {
                mutableStateOf(config.outputFileName ?: defaultFileName)
            }

            // If config explicitly carries a custom name, reflect it; otherwise leave the user draft untouched.
            LaunchedEffect(config.outputFileName, defaultFileName) {
                val desired = config.outputFileName
                if (desired != null && desired != fileNameDraft) {
                    fileNameDraft = desired
                }
            }

            val focusManager = LocalFocusManager.current

            // Update local draft immediately, but debounce applying to the shared config
            OutlinedTextField(
                value = fileNameDraft,
                onValueChange = { fileNameDraft = it },
                label = { Text("Output filename") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = {
                    val trimmed = fileNameDraft.trim()
                    val normalized = trimmed.takeIf { it.isNotBlank() }
                    if (normalized != config.outputFileName) {
                        onConfigChange(config.copy(outputFileName = normalized))
                    }
                    focusManager.clearFocus()
                })
            )

            // Debounce: apply changes after a short pause in typing to avoid expensive recompositions
            LaunchedEffect(fileNameDraft) {
                val current = fileNameDraft
                kotlinx.coroutines.delay(350)
                if (current == fileNameDraft) {
                    val trimmed = current.trim()
                    val normalized = trimmed.takeIf { it.isNotBlank() }
                    if (normalized != config.outputFileName) {
                        onConfigChange(config.copy(outputFileName = normalized))
                    }
                }
            }
        }
    }
}

@Composable
fun ExifEditorDialog(
    exifData: ExifData?,
    originalExifData: ExifData?,
    onExifDataChange: (ExifData?) -> Unit,
    onDismiss: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val currentExif = exifData ?: ExifData()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .verticalScroll(scrollState)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Edit EXIF metadata",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close EXIF editor")
                    }
                }
                Text(
                    text = "Changes apply instantly to the selected image.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("Basic Info", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = currentExif.make ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(make = it.takeIf { it.isNotBlank() })) },
                    label = { Text("Make") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.model ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(model = it.takeIf { it.isNotBlank() })) },
                    label = { Text("Model") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.software ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(software = it.takeIf { it.isNotBlank() })) },
                    label = { Text("Software") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Date and Time", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = currentExif.dateTime ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(dateTime = it.takeIf { it.isNotBlank() })) },
                    label = { Text("Date Time") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Location", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = currentExif.latitude?.toString() ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(latitude = it.toDoubleOrNull())) },
                    label = { Text("Lat") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.longitude?.toString() ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(longitude = it.toDoubleOrNull())) },
                    label = { Text("Lon") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.altitude?.toString() ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(altitude = it.toDoubleOrNull())) },
                    label = { Text("Alt") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Camera Settings", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = currentExif.exposureTime ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(exposureTime = it.takeIf { it.isNotBlank() })) },
                    label = { Text("Exp Time") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.fNumber ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(fNumber = it.takeIf { it.isNotBlank() })) },
                    label = { Text("F#") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.iso?.toString() ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(iso = it.toIntOrNull())) },
                    label = { Text("ISO") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.focalLength ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(focalLength = it.takeIf { it.isNotBlank() })) },
                    label = { Text("Focal Len") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.flash?.toString() ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(flash = it.toIntOrNull())) },
                    label = { Text("Flash") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.whiteBalance?.toString() ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(whiteBalance = it.toIntOrNull())) },
                    label = { Text("WB") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.orientation?.toString() ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(orientation = it.toIntOrNull())) },
                    label = { Text("Orientation") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Metadata", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = currentExif.artist ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(artist = it.takeIf { it.isNotBlank() })) },
                    label = { Text("Artist") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.copyright ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(copyright = it.takeIf { it.isNotBlank() })) },
                    label = { Text("Copyright") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.description ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(description = it.takeIf { it.isNotBlank() })) },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.userComment ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(userComment = it.takeIf { it.isNotBlank() })) },
                    label = { Text("User Comment") },
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = { onExifDataChange(ExifData()) }) {
                            Text("Remove metadata")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismiss) {
                            Text("Done")
                        }
                    }
                    Button(onClick = { onExifDataChange(originalExifData) }, enabled = originalExifData != null, modifier = Modifier.fillMaxWidth()) {
                        Text("Restore original")
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CropEditorDialog(
    imageUri: Uri?,
    cropBounds: CropBounds?,
    onCropBoundsChange: (CropBounds?) -> Unit,
    onDismiss: () -> Unit,
) {
    val aspectRatio = rememberImageAspectRatio(imageUri)
    val initialBounds = remember(imageUri, cropBounds) { cropBounds ?: CropBounds.Full }
    var horizontalRange by remember(imageUri, cropBounds) {
        mutableStateOf(initialBounds.left..initialBounds.right)
    }
    var verticalRange by remember(imageUri, cropBounds) {
        mutableStateOf(initialBounds.top..initialBounds.bottom)
    }
    val currentBounds = remember(horizontalRange, verticalRange) {
        CropBounds(
            left = horizontalRange.start,
            top = verticalRange.start,
            right = horizontalRange.endInclusive,
            bottom = verticalRange.endInclusive,
        )
    }
    val minSpan = 0.05f
    fun adjustHorizontalStart(delta: Float) {
        if (delta == 0f) return
        val newStart = (horizontalRange.start + delta).coerceIn(0f, horizontalRange.endInclusive - minSpan)
        horizontalRange = newStart..horizontalRange.endInclusive
    }
    fun adjustHorizontalEnd(delta: Float) {
        if (delta == 0f) return
        val newEnd = (horizontalRange.endInclusive + delta).coerceIn(horizontalRange.start + minSpan, 1f)
        horizontalRange = horizontalRange.start..newEnd
    }
    fun adjustVerticalStart(delta: Float) {
        if (delta == 0f) return
        val newStart = (verticalRange.start + delta).coerceIn(0f, verticalRange.endInclusive - minSpan)
        verticalRange = newStart..verticalRange.endInclusive
    }
    fun adjustVerticalEnd(delta: Float) {
        if (delta == 0f) return
        val newEnd = (verticalRange.endInclusive + delta).coerceIn(verticalRange.start + minSpan, 1f)
        verticalRange = verticalRange.start..newEnd
    }
    fun moveSelection(deltaX: Float, deltaY: Float) {
        if (deltaX == 0f && deltaY == 0f) return
        val width = horizontalRange.endInclusive - horizontalRange.start
        val height = verticalRange.endInclusive - verticalRange.start
        val clampedDx = deltaX.coerceIn(-horizontalRange.start, 1f - horizontalRange.endInclusive)
        val clampedDy = deltaY.coerceIn(-verticalRange.start, 1f - verticalRange.endInclusive)
        val newStartX = (horizontalRange.start + clampedDx).coerceIn(0f, 1f - width)
        val newStartY = (verticalRange.start + clampedDy).coerceIn(0f, 1f - height)
        horizontalRange = newStartX..(newStartX + width)
        verticalRange = newStartY..(newStartY + height)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Crop image",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close crop editor")
                    }
                }
                Text(
                    text = "Drag the guides or use the sliders to define what stays before compression.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (imageUri != null) {
                    val previewPadding = 2.dp
                    val sliderGap = 2.dp
                    val sliderWidth = 100.dp
                    val previewShape = RoundedCornerShape(20.dp)
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = previewPadding)
                    ) {
                        val controlsWidth = sliderWidth.coerceAtMost(maxWidth * 0.25f)
                        val effectiveAspect = (aspectRatio ?: 4f / 3f).coerceIn(0.3f, 3.5f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(sliderGap),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(effectiveAspect)
                            ) {
                                val previewWidth = maxWidth
                                val previewHeight = maxHeight
                                val density = LocalDensity.current
                                val widthPx = with(density) { previewWidth.toPx() }.coerceAtLeast(1f)
                                val heightPx = with(density) { previewHeight.toPx() }.coerceAtLeast(1f)
                                val overlayColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)
                                val borderColor = MaterialTheme.colorScheme.primary
                                val borderWidthPx = with(density) { 2.dp.toPx() }
                                val handleColor = MaterialTheme.colorScheme.primary
                                val handleThickness = 6.dp
                                val handleLength = 48.dp
                                val handleTouchExtra = 12.dp
                                val cornerSize = 20.dp
                                val leftDp = previewWidth * horizontalRange.start
                                val topDp = previewHeight * verticalRange.start
                                val rightDp = previewWidth * horizontalRange.endInclusive
                                val bottomDp = previewHeight * verticalRange.endInclusive
                                val centerXDp = (leftDp + rightDp) / 2f
                                val centerYDp = (topDp + bottomDp) / 2f
                                val selectionWidth = rightDp - leftDp
                                val selectionHeight = bottomDp - topDp
                                fun Modifier.handleDrag(onDrag: (Float, Float) -> Unit): Modifier {
                                    return pointerInput(widthPx, heightPx) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            if (widthPx == 0f || heightPx == 0f) return@detectDragGestures
                                            val deltaX = dragAmount.x / widthPx
                                            val deltaY = dragAmount.y / heightPx
                                            onDrag(deltaX, deltaY)
                                        }
                                    }
                                }

                                @Composable
                                fun DragHandle(
                                    offsetX: Dp,
                                    offsetY: Dp,
                                    width: Dp,
                                    height: Dp,
                                    onDrag: (Float, Float) -> Unit,
                                    content: @Composable () -> Unit,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = offsetX - handleTouchExtra, y = offsetY - handleTouchExtra)
                                            .width(width + handleTouchExtra * 2)
                                            .height(height + handleTouchExtra * 2)
                                            .handleDrag(onDrag),
                                        contentAlignment = Alignment.Center
                                    ) { content() }
                                }

                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(previewShape)
                                ) {
                                    AsyncImage(
                                        model = imageUri,
                                        contentDescription = null,
                                        modifier = Modifier.matchParentSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                                Canvas(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(previewShape)
                                ) {
                                    val leftPx = size.width * horizontalRange.start
                                    val topPx = size.height * verticalRange.start
                                    val rightPx = size.width * horizontalRange.endInclusive
                                    val bottomPx = size.height * verticalRange.endInclusive
                                    drawRect(
                                        color = overlayColor,
                                        size = androidx.compose.ui.geometry.Size(size.width, topPx)
                                    )
                                    drawRect(
                                        color = overlayColor,
                                        topLeft = Offset(0f, bottomPx),
                                        size = androidx.compose.ui.geometry.Size(size.width, size.height - bottomPx)
                                    )
                                    drawRect(
                                        color = overlayColor,
                                        topLeft = Offset(0f, topPx),
                                        size = androidx.compose.ui.geometry.Size(leftPx, bottomPx - topPx)
                                    )
                                    drawRect(
                                        color = overlayColor,
                                        topLeft = Offset(rightPx, topPx),
                                        size = androidx.compose.ui.geometry.Size(size.width - rightPx, bottomPx - topPx)
                                    )
                                    drawRect(
                                        color = borderColor,
                                        topLeft = Offset(leftPx, topPx),
                                        size = androidx.compose.ui.geometry.Size(rightPx - leftPx, bottomPx - topPx),
                                        style = Stroke(width = borderWidthPx)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .offset(x = leftDp, y = topDp)
                                        .width(selectionWidth)
                                        .height(selectionHeight)
                                        .handleDrag { dx, dy -> moveSelection(dx, dy) }
                                )
                                Box(modifier = Modifier.matchParentSize().zIndex(1f)) {
                                    DragHandle(
                                        offsetX = leftDp - handleThickness / 2,
                                        offsetY = centerYDp - handleLength / 2,
                                        width = handleThickness,
                                        height = handleLength,
                                        onDrag = { dx, _ -> adjustHorizontalStart(dx) }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(handleThickness)
                                                .height(handleLength)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(handleColor)
                                        )
                                    }
                                    DragHandle(
                                        offsetX = rightDp - handleThickness / 2,
                                        offsetY = centerYDp - handleLength / 2,
                                        width = handleThickness,
                                        height = handleLength,
                                        onDrag = { dx, _ -> adjustHorizontalEnd(dx) }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(handleThickness)
                                                .height(handleLength)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(handleColor)
                                        )
                                    }
                                    DragHandle(
                                        offsetX = centerXDp - handleLength / 2,
                                        offsetY = topDp - handleThickness / 2,
                                        width = handleLength,
                                        height = handleThickness,
                                        onDrag = { _, dy -> adjustVerticalStart(dy) }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(handleLength)
                                                .height(handleThickness)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(handleColor)
                                        )
                                    }
                                    DragHandle(
                                        offsetX = centerXDp - handleLength / 2,
                                        offsetY = bottomDp - handleThickness / 2,
                                        width = handleLength,
                                        height = handleThickness,
                                        onDrag = { _, dy -> adjustVerticalEnd(dy) }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(handleLength)
                                                .height(handleThickness)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(handleColor)
                                        )
                                    }
                                    DragHandle(
                                        offsetX = leftDp - cornerSize / 2,
                                        offsetY = topDp - cornerSize / 2,
                                        width = cornerSize,
                                        height = cornerSize,
                                        onDrag = { dx, dy ->
                                            adjustHorizontalStart(dx)
                                            adjustVerticalStart(dy)
                                        }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(cornerSize)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(handleColor)
                                        )
                                    }
                                    DragHandle(
                                        offsetX = rightDp - cornerSize / 2,
                                        offsetY = topDp - cornerSize / 2,
                                        width = cornerSize,
                                        height = cornerSize,
                                        onDrag = { dx, dy ->
                                            adjustHorizontalEnd(dx)
                                            adjustVerticalStart(dy)
                                        }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(cornerSize)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(handleColor)
                                        )
                                    }
                                    DragHandle(
                                        offsetX = leftDp - cornerSize / 2,
                                        offsetY = bottomDp - cornerSize / 2,
                                        width = cornerSize,
                                        height = cornerSize,
                                        onDrag = { dx, dy ->
                                            adjustHorizontalStart(dx)
                                            adjustVerticalEnd(dy)
                                        }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(cornerSize)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(handleColor)
                                        )
                                    }
                                    DragHandle(
                                        offsetX = rightDp - cornerSize / 2,
                                        offsetY = bottomDp - cornerSize / 2,
                                        width = cornerSize,
                                        height = cornerSize,
                                        onDrag = { dx, dy ->
                                            adjustHorizontalEnd(dx)
                                            adjustVerticalEnd(dy)
                                        }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(cornerSize)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(handleColor)
                                        )
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .width(controlsWidth)
                                    .fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Vertical", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                                VerticalRangeSlider(
                                    value = verticalRange,
                                    onValueChange = { range ->
                                        val start = range.start.coerceIn(0f, 1f - minSpan)
                                        val end = range.endInclusive.coerceIn(start + minSpan, 1f)
                                        verticalRange = start..end
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    valueRange = 0f..1f,
                                    steps = 100
                                )
                                Text(
                                    text = "${(verticalRange.start * 100).roundToInt()}% - ${(verticalRange.endInclusive * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Select an image to crop",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Horizontal", style = MaterialTheme.typography.titleSmall)
                    RangeSlider(
                        value = horizontalRange,
                        onValueChange = { range ->
                            val start = range.start.coerceIn(0f, 1f - minSpan)
                            val end = range.endInclusive.coerceIn(start + minSpan, 1f)
                            horizontalRange = start..end
                        },
                        valueRange = 0f..1f,
                        steps = 100
                    )
                    Text(
                        text = "${(horizontalRange.start * 100).roundToInt()}% - ${(horizontalRange.endInclusive * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        horizontalRange = 0f..1f
                        verticalRange = 0f..1f
                    }) {
                        Text("Reset")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(onClick = {
                        val normalized = currentBounds.normalized()
                        val applied = normalized.takeUnless { it.isFull() }
                        onCropBoundsChange(applied)
                        onDismiss()
                    }) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    RangeSlider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        modifier = modifier.rotate(-90f)
    )
}

@Composable
private fun rememberImageAspectRatio(imageUri: Uri?): Float? {
    val context = LocalContext.current
    var aspectRatio by remember(imageUri) { mutableStateOf<Float?>(null) }

    LaunchedEffect(imageUri, context) {
        aspectRatio = imageUri?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeStream(stream, null, options)
                        if (options.outWidth > 0 && options.outHeight > 0) {
                            options.outWidth.toFloat() / options.outHeight.toFloat()
                        } else {
                            null
                        }
                    }
                }.getOrNull()
            }
        }
    }
    return aspectRatio
}

@Composable
fun ProgressCard(state: CompressionUiState) {
    val progress = if (state.totalCount == 0) 0f else state.processedCount.toFloat() / max(1, state.totalCount)
    val context = LocalContext.current
    val title = if (state.isProcessing) "Processing..." else "Completed"
    
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            val fileLabel = if (state.totalCount == 1) "file" else "files"
            Text("${state.processedCount}/${state.totalCount} $fileLabel processed", style = MaterialTheme.typography.bodySmall)
            state.lastResult?.let { last ->
                val lastItemName = remember(last.source) {
                    DocumentFile.fromSingleUri(context, last.source)?.name ?: last.source.lastPathSegment ?: ""
                }
                Text("Last item: $lastItemName", style = MaterialTheme.typography.labelSmall)
                Text(last.message, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun ResultsCard(results: List<CompressionResult>) {
    val successfulResults = results.filter { it.success }
    val totalOriginal = successfulResults.sumOf { it.originalSizeBytes }
    val totalCompressed = successfulResults.sumOf { it.compressedSizeBytes }
    var zoomTarget by remember { mutableStateOf<Uri?>(null) }
    var zoomLabel by remember { mutableStateOf<String?>(null) }

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Results", style = MaterialTheme.typography.titleMedium)
            if (successfulResults.isNotEmpty()) {
                Text("Total: ${totalOriginal.asReadableBytes()} → ${totalCompressed.asReadableBytes()}", style = MaterialTheme.typography.bodyMedium)
            }
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results.size) { index ->
                    val result = results[index]
                    ResultRow(
                        result = result,
                        onPreviewClick = { uri, label ->
                            zoomTarget = uri
                            zoomLabel = label
                        }
                    )
                    if (index != results.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (zoomTarget != null) {
        Dialog(onDismissRequest = {
            zoomTarget = null
            zoomLabel = null
        }) {
            Surface(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(zoomLabel ?: "Preview", style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = {
                            zoomTarget = null
                            zoomLabel = null
                        }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 420.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        ZoomableImage(uri = zoomTarget)
                    }
                    TextButton(onClick = {
                        zoomTarget = null
                        zoomLabel = null
                    }, modifier = Modifier.align(Alignment.End)) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun ResultRow(
    result: CompressionResult,
    onPreviewClick: (Uri, String) -> Unit,
) {
    val context = LocalContext.current
    val showPreview = remember(result.source, result.output, result.success) {
        result.success && result.output != null &&
            (DocumentFile.fromSingleUri(context, result.source)?.type?.startsWith("image/") == true)
    }
    val sourceName = remember(result.source) {
        DocumentFile.fromSingleUri(context, result.source)?.name ?: result.source.lastPathSegment ?: "File"
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(sourceName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (showPreview) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Before
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Before", style = MaterialTheme.typography.bodySmall)
                    AsyncImage(
                        model = result.source,
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onPreviewClick(result.source, "Original") },
                        contentScale = ContentScale.Crop
                    )
                    Text(result.originalSizeBytes.asReadableBytes(), style = MaterialTheme.typography.bodySmall)
                }
                // After
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("After", style = MaterialTheme.typography.bodySmall)
                    AsyncImage(
                        model = result.output,
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { result.output?.let { onPreviewClick(it, "Compressed") } },
                        contentScale = ContentScale.Crop
                    )
                    Text(result.compressedSizeBytes.asReadableBytes(), style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (result.success) "Processed to ${result.compressedSizeBytes.asReadableBytes()}" else result.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.success) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun PreviewKompact() {
    KompactTheme {
        KompactScreen(
            state = CompressionUiState(),
            onPickFiles = {},
            onCompressClick = {},
            onImageConfigChange = {},
            onRemoveFile = {},
            onClearSelection = {},
            onPickDestination = {},
            onClearDestination = {},
            onExifDataChange = { _ -> },
            onToggleExifEditor = {},
            onToggleCropEditor = {},
            onCropBoundsChange = { _ -> },
            onNavigateToSettings = {},
            onNavigateToHome = {},
            onNavigateToAbout = {},
            onNavigateToDonate = {},
            onThemeModeChange = {},
            onPickDefaultDestination = {},
            onClearDefaultDestination = {},
        )
    }
}

@Composable
fun LivePreviewCard(
    livePreview: LivePreviewState,
    canCrop: Boolean,
    isCropEditorVisible: Boolean,
    isCropApplied: Boolean,
    onCropClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var zoomTarget by remember { mutableStateOf<Uri?>(null) }
    var zoomLabel by remember { mutableStateOf<String?>(null) }
    Card(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Live Preview", style = MaterialTheme.typography.titleMedium)
                if (canCrop) {
                    TextButton(onClick = onCropClick) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        val label = when {
                            isCropEditorVisible -> "Close Crop"
                            isCropApplied -> "Edit Crop"
                            else -> "Crop"
                        }
                        Text(label)
                    }
                }
            }
            val hasPreviews = livePreview.kind == PreviewKind.IMAGE && livePreview.previews.isNotEmpty()
            val showLoadingOnly = livePreview.isGenerating && !hasPreviews

            Box {
                when {
                    hasPreviews -> {
                        val totalOriginal = livePreview.previews.sumOf { it.originalBytes }
                        val totalEstimated = livePreview.previews.sumOf { it.estimatedBytes }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Total: ${totalOriginal.asReadableBytes()} → ${totalEstimated.asReadableBytes()}", style = MaterialTheme.typography.bodyMedium)
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 400.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(livePreview.previews.size) { index ->
                                    val preview = livePreview.previews[index]
                                    LivePreviewRow(
                                        preview = preview,
                                        onPreviewClick = { uri, label ->
                                            zoomTarget = uri
                                            zoomLabel = label
                                        }
                                    )
                                }
                            }
                        }
                    }
                    livePreview.errorMessage != null -> {
                        Text(livePreview.errorMessage, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    showLoadingOnly -> {
                        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Text("Generating preview...", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                }

                if (livePreview.isGenerating && hasPreviews) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text("Updating preview...", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
    if (zoomTarget != null) {
        Dialog(onDismissRequest = {
            zoomTarget = null
            zoomLabel = null
        }) {
            Surface(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(zoomLabel ?: "Preview", style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = {
                            zoomTarget = null
                            zoomLabel = null
                        }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 420.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        ZoomableImage(uri = zoomTarget)
                    }
                    TextButton(onClick = {
                        zoomTarget = null
                        zoomLabel = null
                    }, modifier = Modifier.align(Alignment.End)) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
fun LivePreviewRow(
    preview: ImagePreviewPayload,
    onPreviewClick: (Uri, String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Before
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Before", style = MaterialTheme.typography.bodySmall)
            AsyncImage(
                model = preview.source,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onPreviewClick(preview.source, "Original") },
                contentScale = ContentScale.Crop
            )
            Text(preview.originalBytes.asReadableBytes(), style = MaterialTheme.typography.bodySmall)
        }
        // After
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("After", style = MaterialTheme.typography.bodySmall)
            AsyncImage(
                model = preview.previewUri,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onPreviewClick(preview.previewUri, "Compressed (${preview.formatExtension.uppercase()})") },
                contentScale = ContentScale.Crop
            )
            Text("${preview.estimatedBytes.asReadableBytes()} (${preview.formatExtension.uppercase()})", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ZoomableImage(
    uri: Uri?,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale *= zoomChange
        scale = scale.coerceIn(0.5f, 3f)
        offset += offsetChange
    }

    AsyncImage(
        model = uri,
        contentDescription = null,
        modifier = modifier
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
            .transformable(state = state)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = 1f
                        offset = Offset.Zero
                    }
                )
            },
        contentScale = ContentScale.Fit
    )
}

private fun inferOriginalFormat(file: SelectedFileDescriptor?): ImageOutputFormat? {
    if (file == null) return null
    val mime = file.mimeType?.lowercase(Locale.ROOT)
    val extension = file.displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val source = mime ?: extension
    return when {
        source.contains("jpeg") || source.contains("jpg") -> ImageOutputFormat.JPG
        source.contains("png") -> ImageOutputFormat.PNG
        source.contains("webp") -> ImageOutputFormat.WEBP
        source.contains("bmp") -> ImageOutputFormat.BMP
        source.contains("gif") -> ImageOutputFormat.GIF
        source.contains("tif") || source.contains("tiff") -> ImageOutputFormat.TIF
        source.contains("heif") -> ImageOutputFormat.HEIF
        source.contains("heic") -> ImageOutputFormat.HEIC
        source.contains("avif") -> ImageOutputFormat.AVIF
        source.contains("jxl") -> ImageOutputFormat.JXL
        else -> null
    }
}

private fun inferDefaultFormatLabel(file: SelectedFileDescriptor?): String? {
    return inferOriginalFormat(file)?.let { format ->
        when (format) {
            ImageOutputFormat.JPG -> "JPG"
            ImageOutputFormat.PNG -> "PNG"
            ImageOutputFormat.WEBP -> "WebP"
            ImageOutputFormat.BMP -> "BMP"
            ImageOutputFormat.GIF -> "GIF"
            ImageOutputFormat.TIF -> "TIF"
            ImageOutputFormat.HEIF -> "HEIF"
            ImageOutputFormat.HEIC -> "HEIC"
            ImageOutputFormat.AVIF -> "AVIF"
            ImageOutputFormat.JXL -> "JXL"
            else -> format.name
        }
    }
}

private fun formatDisplayLabel(format: ImageOutputFormat, defaultFormatLabel: String?): String {
    return when (format) {
        ImageOutputFormat.ORIGINAL -> defaultFormatLabel?.let { "Original ($it)" } ?: "Original"
        else -> format.name
    }
}

private fun getFolderDisplayName(uri: Uri?): String {
    return uri?.let { u ->
        val path = u.path ?: ""
        val cleanedPath = path.replace("primary:", "")
        val segments = cleanedPath.split("/").filter { it.isNotEmpty() }
        when {
            segments.size >= 2 -> "${segments[segments.size - 2]}/${segments.last()}"
            segments.size == 1 -> segments.first()
            else -> "Selected folder"
        }
    } ?: "Not set"
}
