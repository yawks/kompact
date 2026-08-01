package com.kompact.ui

import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Build
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.kompact.R
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
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource

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
    onToggleTextEditor: () -> Unit,
    onTextConfigChange: (com.kompact.model.TextOverlayConfig?) -> Unit,
    onToggleExifEditor: () -> Unit,
    onToggleCropEditor: () -> Unit,
    onCropBoundsChange: (com.kompact.model.CropBounds?) -> Unit,
    onToggleResizeEditor: () -> Unit,
    onResizeConfigChange: (com.kompact.model.ResizeConfig?) -> Unit,
    
    onBackgroundConfigChange: (com.kompact.model.BackgroundConfig?) -> Unit,
    onToggleColorFilterEditor: () -> Unit,
    onColorFilterChange: (com.kompact.model.ColorFilterConfig?) -> Unit,
    onRotate: (Float) -> Unit,
    onResetEditsClick: () -> Unit,
    onDeleteOriginalsChange: (Boolean) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToContact: () -> Unit,
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
                Text(stringResource(R.string.app_name), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall)
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_home)) },
                    selected = state.currentScreen == AppScreen.HOME,
                    onClick = {
                        onNavigateToHome()
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_settings)) },
                    selected = state.currentScreen == AppScreen.SETTINGS,
                    onClick = {
                        onNavigateToSettings()
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_about)) },
                    selected = state.currentScreen == AppScreen.ABOUT,
                    onClick = {
                        onNavigateToAbout()
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_donate)) },
                    selected = state.currentScreen == AppScreen.DONATE,
                    onClick = {
                        onNavigateToDonate()
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_contact_us)) },
                    selected = state.currentScreen == AppScreen.CONTACT,
                    onClick = {
                        onNavigateToContact()
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Filled.Email, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                val uriHandler = LocalUriHandler.current
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.nav_report_issue)) },
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
                            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = stringResource(R.string.subtitle),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { onNavigateToDonate() }) {
                            Icon(Icons.Filled.Favorite, contentDescription = stringResource(R.string.nav_donate))
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
                    onToggleTextEditor = onToggleTextEditor,
                    onTextConfigChange = onTextConfigChange,
                    onToggleExifEditor = onToggleExifEditor,
                    onToggleCropEditor = onToggleCropEditor,
                    onCropBoundsChange = onCropBoundsChange,
                    onToggleResizeEditor = onToggleResizeEditor,
                    onResizeConfigChange = onResizeConfigChange,
                    
                    onBackgroundConfigChange = onBackgroundConfigChange,
                    onToggleColorFilterEditor = onToggleColorFilterEditor,
                    onColorFilterChange = onColorFilterChange,
                    onRotate = onRotate,
                    onResetEditsClick = onResetEditsClick,
                    onDeleteOriginalsChange = onDeleteOriginalsChange,
                    modifier = modifier.padding(innerPadding)
                )
                AppScreen.SETTINGS -> SettingsScreen(
                    defaultDestinationFolder = state.defaultDestinationFolder,
                    defaultDownloadsLabel = state.defaultDownloadsLabel,
                    themeMode = state.themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onPickDefaultDestination = onPickDefaultDestination,
                    onClearDefaultDestination = onClearDefaultDestination,
                    modifier = modifier.padding(innerPadding)
                )
                AppScreen.ABOUT -> AboutScreen(modifier = modifier.padding(innerPadding))
                AppScreen.CONTACT -> ContactScreen(modifier = modifier.padding(innerPadding))
                AppScreen.DONATE -> DonateScreen(modifier = modifier.padding(innerPadding))
                else -> {}
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
    onToggleTextEditor: () -> Unit,
    onTextConfigChange: (com.kompact.model.TextOverlayConfig?) -> Unit,
    onToggleExifEditor: () -> Unit,
    onToggleCropEditor: () -> Unit,
    onCropBoundsChange: (CropBounds?) -> Unit,
    onToggleResizeEditor: () -> Unit,
    onResizeConfigChange: (com.kompact.model.ResizeConfig?) -> Unit,
    
    onBackgroundConfigChange: (com.kompact.model.BackgroundConfig?) -> Unit,
    onToggleColorFilterEditor: () -> Unit,
    onColorFilterChange: (com.kompact.model.ColorFilterConfig?) -> Unit,
    onRotate: (Float) -> Unit,
    onResetEditsClick: () -> Unit,
    onDeleteOriginalsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isRotateEditorVisible by remember { mutableStateOf(false) }
    val firstImageDescriptor = remember(state.selectedFiles) {
        state.selectedFiles.firstOrNull { it.mimeType?.startsWith("image/") == true }
    }
    val defaultFormatLabel = remember(firstImageDescriptor) {
        inferDefaultFormatLabel(firstImageDescriptor)
    }
    val singleImageSelected = state.sourceCategory == SourceCategory.IMAGE && state.selectedFiles.size == 1
    val exifEditingEnabled = state.sourceCategory == SourceCategory.IMAGE // Modificato per abilitare il tasto in batch
    val originalFormat = remember(firstImageDescriptor) {
        inferOriginalFormat(firstImageDescriptor)
    }
    val defaultDirResource = "Default Dir"
    val defaultDestinationLabel = remember(
        state.defaultDestinationFolder,
        state.defaultDownloadsLabel,
        defaultDirResource
    ) {
        state.defaultDestinationFolder?.let { getFolderDisplayName(Uri.parse(it)) } ?: state.defaultDownloadsLabel.ifBlank { defaultDirResource }
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
        item {
            Box {
                Box(modifier = if (state.selectedFiles.isEmpty()) Modifier.alpha(0.38f) else Modifier) {
                    LivePreviewCard(
                        livePreview = state.livePreview,
                        canCrop = singleImageSelected || state.selectedFiles.isEmpty(),
                        isCropEditorVisible = state.isCropEditorVisible,
                        isCropApplied = cropApplied,
                        onCropClick = onToggleCropEditor,
                        canResize = state.sourceCategory == SourceCategory.IMAGE || state.selectedFiles.isEmpty(),
                        isResizeEditorVisible = state.isResizeEditorVisible,
                        isResizeApplied = state.imageConfig.resizeConfig != null,
                        onResizeClick = onToggleResizeEditor,
                        canText = false, // DISABLED: Text editor needs fixing
                        isTextEditorVisible = state.isTextEditorVisible,
                        isTextApplied = state.imageConfig.textConfig != null,
                        onTextClick = onToggleTextEditor,
                        onRotateClick = { isRotateEditorVisible = true },
                        onColorFilterClick = onToggleColorFilterEditor,
                        exifEditingEnabled = exifEditingEnabled,
                        isEditingExif = state.isExifEditorVisible,
                        onToggleExifEditor = onToggleExifEditor,
                        onResetEditsClick = onResetEditsClick,
                        isMultipleImages = state.selectedFiles.size > 1, // Passa se sono multiple
                        stripExif = state.imageConfig.stripExif,         // Passa lo stato attuale
                    )
                }
                if (state.selectedFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {}
                    )
                }
            }
        }
        item {
            Box {
                Box(modifier = if (state.selectedFiles.isEmpty()) Modifier.alpha(0.38f) else Modifier) {
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
                    exifEditingEnabled = exifEditingEnabled || state.selectedFiles.isEmpty(),
                    isEditingExif = state.isExifEditorVisible,
                    onToggleExifEditor = onToggleExifEditor,
                )
                }
                if (state.selectedFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {}
                    )
                }
            }
        }
        
        if (state.selectedFiles.isNotEmpty()) {
            item {
                val isCompleted = state.totalCount > 0 && 
                                  state.processedCount == state.totalCount && 
                                  !state.isProcessing && 
                                  state.results.isNotEmpty()
                if (!isCompleted) {
                    PrimaryActionBar(
                        isProcessing = state.isProcessing,
                        canCompress = state.canProcessSelection && !state.isProcessing,
                        onCompressClick = onCompressClick,
                        deleteOriginals = state.deleteOriginals,
                        onDeleteOriginalsChange = onDeleteOriginalsChange,
                        imageConfig = state.imageConfig,
                        onImageConfigChange = onImageConfigChange,
                    )
                }
            }
        }
        if (state.isProcessing || state.processedCount > 0) {
            item { ProgressCard(state = state) }
        }
        if (state.results.isNotEmpty()) {
            item { ResultsCard(state.results) }
        }
    }

    if (state.isExifEditorVisible && state.sourceCategory == SourceCategory.IMAGE) {
        if (state.selectedFiles.size > 1) {
            RemoveExifDialog(
                stripExif = state.imageConfig.stripExif,
                onStripExifChange = { strip -> 
                    onImageConfigChange(state.imageConfig.copy(stripExif = strip))
                    onToggleExifEditor() 
                },
                onDismiss = onToggleExifEditor
            )
        } else {
            ExifEditorDialog(
                exifData = state.exifData,
                originalExifData = state.originalExifData,
                onExifDataChange = onExifDataChange,
                stripExif = state.imageConfig.stripExif,
                onStripExifChange = { strip -> onImageConfigChange(state.imageConfig.copy(stripExif = strip)) },
                onDismiss = onToggleExifEditor,
            )
        }
    }

    if (state.isCropEditorVisible && singleImageSelected) {
        CropEditorDialog(
            imageUri = state.selectedFiles.firstOrNull()?.uri,
            cropBounds = state.imageConfig.cropBounds,
            onCropBoundsChange = onCropBoundsChange,
            onDismiss = onToggleCropEditor,
        )
    }

    if (state.isResizeEditorVisible && state.sourceCategory == SourceCategory.IMAGE) {
        ResizeEditorDialog(
            config = state.imageConfig.resizeConfig,
            previewUri = state.selectedFiles.firstOrNull()?.uri,
            onConfigChange = onResizeConfigChange,
            onDismiss = onToggleResizeEditor,
        )
    }

    

    if (state.isColorFilterEditorVisible && state.sourceCategory == SourceCategory.IMAGE) {
        val showJpegWarning = state.imageConfig.format == com.kompact.model.ImageOutputFormat.JPG
        FiltersEditorDialog(
            currentColorConfig = state.imageConfig.colorFilterConfig,
            currentBackgroundConfig = state.imageConfig.backgroundConfig,
            previewUri = state.selectedFiles.firstOrNull()?.uri,
            showJpegWarning = showJpegWarning,
            onConfirm = { colorCfg, bgCfg ->
                onColorFilterChange(colorCfg)
                onBackgroundConfigChange(bgCfg)
            },
            onDismiss = onToggleColorFilterEditor
        )
    }

    if (state.isTextEditorVisible && state.sourceCategory == SourceCategory.IMAGE) {
        val uri = state.selectedFiles.firstOrNull()?.uri
        if (uri != null) {
            TextEditorDialog(
                imageUri = uri,
                textConfig = state.imageConfig.textConfig,
                onTextConfigChange = { config ->
                    onTextConfigChange(config)
                    onToggleTextEditor() // close it
                },
                onDismiss = onToggleTextEditor
            )
        }
    }

    if (isRotateEditorVisible && state.sourceCategory == SourceCategory.IMAGE) {
        RotateEditorDialog(
            currentRotation = state.imageConfig.rotationDegrees,
            previewUri = state.selectedFiles.firstOrNull()?.uri,
            colorFilter = state.imageConfig.colorFilterConfig,
            onConfirm = { newRotation -> 
                val absoluteRotation = if (newRotation < 0f) newRotation + 360f else newRotation
                onRotate(absoluteRotation - state.imageConfig.rotationDegrees)
            },
            onDismiss = { isRotateEditorVisible = false }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(


    defaultDestinationFolder: String?,
    defaultDownloadsLabel: String,
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
        item { Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium) }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)

                    val defaultLanguage = stringResource(R.string.default_text)
                    val locales = remember {
                        listOf(
                            "" to defaultLanguage,
                            "en" to "English",
                            "ca" to "Català",
                            "cs" to "Čeština",
                            "de" to "Deutsch",
                            "es" to "Español",
                            "fr" to "Français",
                            "hr" to "Hrvatski",
                            "id" to "Indonesia",
                            "it" to "Italiano",
                            "hu" to "Magyar",
                            "nl" to "Nederlands",
                            "pl" to "Polski",
                            "pt" to "Português",
                            "ro" to "Română",
                            "fi" to "Suomi",
                            "sv" to "Svenska",
                            "vi" to "Tiếng Việt",
                            "tr" to "Türkçe",
                            "el" to "Ελληνικά",
                            "bg" to "Български",
                            "ru" to "Русский",
                            "sr" to "Српски",
                            "uk" to "Українська",
                            "ar" to "العربية",
                            "fa" to "فارسی",
                            "hi" to "हिन्दी",
                            "bn" to "বাংলা",
                            "ja" to "日本語",
                            "zh-CN" to "中文 (简体)",
                            "ko" to "한국어"
                        )
                    }

                    var expanded by remember { mutableStateOf(false) }
                    val currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
                    val currentTags = currentLocales.toLanguageTags()
                    val currentLang = if (currentTags.isBlank()) "" else currentTags.split("-")[0]
                    val selectedText = locales.find { it.first == currentLang }?.second ?: stringResource(R.string.default_text)
                    val isRtl = currentLang in listOf("ar", "fa")
                    val layoutDir = if (isRtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr

                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides layoutDir) {
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                            OutlinedTextField(
                                value = selectedText,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                locales.forEach { (tag, label) ->
                                    val itemDir = if (tag in listOf("ar", "fa")) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
                                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides itemDir) {
                                        DropdownMenuItem(
                                            text = { Text(if (tag == "") "Lang Default" else label) },
                                            onClick = {
                                                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags(tag))
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.default_dest), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = defaultDestinationFolder?.let { getFolderDisplayName(Uri.parse(it)) } ?: defaultDownloadsLabel.ifBlank { "Default Dir" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onPickDefaultDestination) {
                            Icon(Icons.Filled.Folder, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.dest_folder))
                        }
                        TextButton(onClick = onClearDefaultDestination, enabled = defaultDestinationFolder != null) {
                            Text(stringResource(R.string.reset_default))
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.theme_title), style = MaterialTheme.typography.titleMedium)

                    @Composable
                    fun labelFor(mode: ThemeMode): String = when (mode) {
                        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                        ThemeMode.DARK -> stringResource(R.string.theme_dark)
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
        item { Text(stringResource(R.string.about_title), style = MaterialTheme.typography.headlineMedium) }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.mission), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.kompact_is_an_offline_file_compressor_designed_to_run_entirely_on_device_your_files_never_leave_your_phone_ensuring_complete_privacy),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.current_features), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.precision_compression_target_specific_file_sizes_or_quality_percentages_n) +
                        "• Format Conversion: Switch easily between JPG, PNG, WebP, and more.\n" +
                        "• Live Preview: Compare original vs. compressed results in real-time.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.roadmap), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.we_are_working_to_make_kompact_the_ultimate_optimizer_future_updates_will_bring_support_for_pdf_documents_audio_tracks_and_video_files),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.open_source), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.this_project_is_open_source_under_the_apache_2_0_license_you_can_inspect_the_code_report_issues_or_contribute_on_our_repository),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val uriHandler = LocalUriHandler.current
                    TextButton(onClick = { uriHandler.openUri("https://git.naxod.com/luca/Kompact") }) {
                        Text(stringResource(R.string.visit_repository))
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
        item { Text(stringResource(R.string.support_development), style = MaterialTheme.typography.headlineMedium) }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.a_personal_note), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.i_built_kompact_because_i_believe_essential_tools_should_be_simple_private_and_free_of_ads_or_tracking_i_work_on_this_in_my_spare_time_driven_by_the_idea_of_making_a_useful_utility_for_everyone),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.support), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.if_kompact_has_saved_you_space_or_time_please_consider_supporting_its_development_your_donation_directly_helps_me_dedicate_more_hours_to_adding_features_like_pdf_audio_and_video_support_and_keeps_the_project_independent_and_sustainable),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = { uriHandler.openUri("https://liberapay.com/naxod/") }) {
                        Icon(Icons.Default.Favorite, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.nav_donate))
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(stringResource(R.string.files), style = MaterialTheme.typography.titleMedium)
                    if (files.isNotEmpty()) {
                        Text(
                            text = "${files.size} ${stringResource(R.string.items_selected)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (files.isNotEmpty()) {
                        OutlinedButton(onClick = onPickFiles) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.add_more), textAlign = TextAlign.Center)
                        }
                    }
                    TextButton(
                        onClick = onClearSelection,
                        enabled = files.isNotEmpty()
                    ) {
                        Text(stringResource(R.string.clear_all), textAlign = TextAlign.Center)
                    }
                }
            }
            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (selectionMessage != null) {
                Text(selectionMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (files.isEmpty()) {
                SourceEmptyState(onPickFiles)
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
private fun SourceEmptyState(onPickFiles: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        Button(onClick = onPickFiles) {
            Icon(Icons.Default.CloudUpload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.select_images_to_compress), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun PrimaryActionBar(
    isProcessing: Boolean,
    canCompress: Boolean,
    onCompressClick: () -> Unit,
    deleteOriginals: Boolean,
    onDeleteOriginalsChange: (Boolean) -> Unit,
    imageConfig: com.kompact.model.ImageCompressionConfig,
    onImageConfigChange: (com.kompact.model.ImageCompressionConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.delete_originals_title)) },
            text = { Text(stringResource(R.string.delete_originals_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteOriginalsChange(true)
                        showDeleteConfirmDialog = false
                    }
                ) {
                    Text(stringResource(R.string.yes_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

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
                val processingText = stringResource(R.string.processing_dots)
                val readyText = stringResource(R.string.ready_to_process)
                Text(
                    text = if (isProcessing) processingText else readyText,
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clickable(enabled = !isProcessing) {
                            if (deleteOriginals) {
                                onDeleteOriginalsChange(false)
                            } else {
                                showDeleteConfirmDialog = true
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = deleteOriginals,
                        onCheckedChange = null,
                        enabled = !isProcessing,
                    )
                    Text(
                        text = stringResource(R.string.delete_original_files),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clickable(enabled = !isProcessing) {
                            onImageConfigChange(imageConfig.copy(preserveSourceDate = !imageConfig.preserveSourceDate))
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = imageConfig.preserveSourceDate,
                        onCheckedChange = null,
                        enabled = !isProcessing,
                    )
                    Text(
                        text = stringResource(R.string.preserve_source_date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = onCompressClick,
                enabled = canCompress,
                modifier = Modifier.height(48.dp)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.working))
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.start))
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
                Text(stringResource(R.string.output), style = MaterialTheme.typography.titleMedium)
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
                        Text(stringResource(R.string.dest_folder), style = MaterialTheme.typography.labelSmall)
                        Text(destinationLabel, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                TextButton(onClick = onClearDestination, enabled = destinationFolder != null) {
                    Text(stringResource(R.string.default_text))
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
        Text(stringResource(R.string.format), style = MaterialTheme.typography.titleSmall)
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
        Text(stringResource(R.string.quality), style = MaterialTheme.typography.titleSmall)
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
            Text("${stringResource(R.string.estimated_total)} ${totalEstimate.asReadableBytes()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                label = { Text(stringResource(R.string.output_filename)) },
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

            AnimatedVisibility(visible = selectedFilesCount > 0, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onConfigChange(config.copy(stripExif = !config.stripExif)) }
                ) {
                    Checkbox(
                        checked = config.stripExif,
                        onCheckedChange = { onConfigChange(config.copy(stripExif = it)) }
                    )
                    Text("Strip all EXIF metadata (Privacy)", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun RemoveExifDialog(
    stripExif: Boolean,
    onStripExifChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = stringResource(R.string.edit_exif_metadata), 
                style = MaterialTheme.typography.titleMedium
            ) 
        },
        text = { 
            Text(
                text = if (stripExif) 
                    "Do you want to restore EXIF metadata for the selected images?" 
                else 
                    "Do you want to strip all EXIF metadata from the selected images?"
            ) 
        },
        confirmButton = {
            Button(
                onClick = { 
                    onStripExifChange(!stripExif) 
                }
            ) { 
                Text(if (stripExif) "Restore EXIF" else "Remove all EXIF") 
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { 
                Text(stringResource(R.string.cancel)) 
            }
        }
    )
}

@Composable
fun ExifEditorDialog(
    exifData: ExifData?,
    originalExifData: ExifData?,
    onExifDataChange: (ExifData?) -> Unit,
    stripExif: Boolean = false,
    onStripExifChange: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val currentExif = exifData ?: ExifData()

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.edit_exif_metadata),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close EXIF editor")
                    }
                }
                Text(
                    text = stringResource(R.string.changes_apply_instantly_to_the_selected_image),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(stringResource(R.string.basic_info), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = currentExif.make ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(make = it.takeIf { it.isNotBlank() })) },
                    label = { Text(stringResource(R.string.make)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.model ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(model = it.takeIf { it.isNotBlank() })) },
                    label = { Text(stringResource(R.string.model)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.software ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(software = it.takeIf { it.isNotBlank() })) },
                    label = { Text(stringResource(R.string.software)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(stringResource(R.string.date_and_time), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = currentExif.dateTime ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(dateTime = it.takeIf { it.isNotBlank() })) },
                    label = { Text(stringResource(R.string.date_time)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(stringResource(R.string.location), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = currentExif.latitude?.toString() ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(latitude = it.toDoubleOrNull())) },
                    label = { Text(stringResource(R.string.lat)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.longitude?.toString() ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(longitude = it.toDoubleOrNull())) },
                    label = { Text(stringResource(R.string.lon)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.altitude?.toString() ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(altitude = it.toDoubleOrNull())) },
                    label = { Text(stringResource(R.string.alt)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.position ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(position = it.takeIf { it.isNotBlank() })) },
                    label = { Text(stringResource(R.string.position)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(stringResource(R.string.camera_settings), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = currentExif.exposureTime ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(exposureTime = it.takeIf { it.isNotBlank() })) },
                    label = { Text(stringResource(R.string.exp_time)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.fNumber ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(fNumber = it.takeIf { it.isNotBlank() })) },
                    label = { Text(stringResource(R.string.f)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.iso?.toString() ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(iso = it.toIntOrNull())) },
                    label = { Text(stringResource(R.string.iso)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.focalLength ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(focalLength = it.takeIf { it.isNotBlank() })) },
                    label = { Text(stringResource(R.string.focal_len)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.flash?.toString() ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(flash = it.toIntOrNull())) },
                    label = { Text(stringResource(R.string.flash)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.whiteBalance?.toString() ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(whiteBalance = it.toIntOrNull())) },
                    label = { Text(stringResource(R.string.wb)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.orientation?.toString() ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(orientation = it.toIntOrNull())) },
                    label = { Text(stringResource(R.string.orientation)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(stringResource(R.string.metadata), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = currentExif.artist ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(artist = it.takeIf { it.isNotBlank() })) },
                    label = { Text(stringResource(R.string.artist)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.copyright ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(copyright = it.takeIf { it.isNotBlank() })) },
                    label = { Text(stringResource(R.string.copyright)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.description ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(description = it.takeIf { it.isNotBlank() })) },
                    label = { Text(stringResource(R.string.description)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = currentExif.userComment ?: "",
                    onValueChange = { onExifDataChange(currentExif.copy(userComment = it.takeIf { it.isNotBlank() })) },
                    label = { Text(stringResource(R.string.user_comment)) },
                    modifier = Modifier.fillMaxWidth()
                )

                }
                androidx.compose.material3.HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = { onExifDataChange(ExifData()) }) {
                            Text(stringResource(R.string.remove_metadata))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Button(onClick = onDismiss) {
                            Text(stringResource(R.string.done))
                        }
                    }
                    Button(onClick = { onExifDataChange(originalExifData) }, enabled = originalExifData != null, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.restore_original))
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
    var fixedAspectRatio by remember(imageUri) { mutableStateOf<Float?>(null) }
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
        fixedAspectRatio = null
        if (delta == 0f) return
        val newStart = (horizontalRange.start + delta).coerceIn(0f, horizontalRange.endInclusive - minSpan)
        horizontalRange = newStart..horizontalRange.endInclusive
    }
    fun adjustHorizontalEnd(delta: Float) {
        fixedAspectRatio = null
        if (delta == 0f) return
        val newEnd = (horizontalRange.endInclusive + delta).coerceIn(horizontalRange.start + minSpan, 1f)
        horizontalRange = horizontalRange.start..newEnd
    }
    fun adjustVerticalStart(delta: Float) {
        fixedAspectRatio = null
        if (delta == 0f) return
        val newStart = (verticalRange.start + delta).coerceIn(0f, verticalRange.endInclusive - minSpan)
        verticalRange = newStart..verticalRange.endInclusive
    }
    fun adjustVerticalEnd(delta: Float) {
        fixedAspectRatio = null
        if (delta == 0f) return
        val newEnd = (verticalRange.endInclusive + delta).coerceIn(verticalRange.start + minSpan, 1f)
        verticalRange = verticalRange.start..newEnd
    }

    fun adjustCorner(dx: Float, dy: Float, isLeft: Boolean, isTop: Boolean) {
        val currentAspect = fixedAspectRatio
        if (currentAspect == null || aspectRatio == null) {
            if (isLeft) adjustHorizontalStart(dx) else adjustHorizontalEnd(dx)
            if (isTop) adjustVerticalStart(dy) else adjustVerticalEnd(dy)
            return
        }

        val signX = if (isLeft) -1f else 1f
        val signY = if (isTop) -1f else 1f
        val dW = dx * signX
        val dH = dy * signY
        
        val dW_px = dW * aspectRatio
        val dH_px = dH * 1f
        val drivesWidth = kotlin.math.abs(dW_px) > kotlin.math.abs(dH_px)

        var finalDx = 0f
        var finalDy = 0f

        if (drivesWidth) {
            finalDx = dx
            val spanX = horizontalRange.endInclusive - horizontalRange.start
            val spanY = verticalRange.endInclusive - verticalRange.start
            val newSpanX = spanX + dW
            val newSpanY = newSpanX * aspectRatio / currentAspect
            val diffY = newSpanY - spanY
            finalDy = diffY * signY
        } else {
            finalDy = dy
            val spanX = horizontalRange.endInclusive - horizontalRange.start
            val spanY = verticalRange.endInclusive - verticalRange.start
            val newSpanY = spanY + dH
            val newSpanX = newSpanY * currentAspect / aspectRatio
            val diffX = newSpanX - spanX
            finalDx = diffX * signX
        }

        val startH = horizontalRange.start
        val endH = horizontalRange.endInclusive
        val startV = verticalRange.start
        val endV = verticalRange.endInclusive

        val tempStartH = if (isLeft) startH + finalDx else startH
        val tempEndH = if (!isLeft) endH + finalDx else endH
        val tempStartV = if (isTop) startV + finalDy else startV
        val tempEndV = if (!isTop) endV + finalDy else endV

        if (tempStartH < 0f || tempEndH > 1f || (tempEndH - tempStartH) < minSpan ||
            tempStartV < 0f || tempEndV > 1f || (tempEndV - tempStartV) < minSpan) {
            return
        }

        horizontalRange = tempStartH..tempEndH
        verticalRange = tempStartV..tempEndV
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

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.crop_image),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close crop editor")
                    }
                }
                Text(
                    text = stringResource(R.string.drag_the_guides_or_use_the_sliders_to_define_what_stays_before_compression),
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
                        val outerMaxWidth = maxWidth
                        val controlsWidth = sliderWidth.coerceAtMost(outerMaxWidth * 0.25f)
                        val effectiveAspect = (aspectRatio ?: 4f / 3f).coerceIn(0.3f, 3.5f)

                        val maxDialogHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.45f
                        val maxPreviewWidth = outerMaxWidth - controlsWidth - sliderGap
                        
                        val fitsWidthFirst = (maxPreviewWidth / effectiveAspect) <= maxDialogHeight
                        val finalPreviewWidth = if (fitsWidthFirst) maxPreviewWidth else maxDialogHeight * effectiveAspect
                        val finalPreviewHeight = if (fitsWidthFirst) maxPreviewWidth / effectiveAspect else maxDialogHeight

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(sliderGap, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .size(finalPreviewWidth, finalPreviewHeight)
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
                                            adjustCorner(dx, dy, isLeft = true, isTop = true)
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
                                            adjustCorner(dx, dy, isLeft = false, isTop = true)
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
                                            adjustCorner(dx, dy, isLeft = true, isTop = false)
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
                                            adjustCorner(dx, dy, isLeft = false, isTop = false)
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
                                VerticalRangeSlider(
                                    value = verticalRange,
                                    length = finalPreviewHeight,
                                    onValueChange = { range ->
                                        fixedAspectRatio = null
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
                        text = stringResource(R.string.select_an_image_to_crop),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RangeSlider(
                        value = horizontalRange,
                        onValueChange = { range ->
                            fixedAspectRatio = null
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

                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val presets = listOf(
                        "1:1" to 1f,
                        "4:3" to 4f/3f,
                        "3:4" to 3f/4f,
                        "16:9" to 16f/9f,
                        "9:16" to 9f/16f
                    )
                    items(presets.size) { idx ->
                        val (label, ratio) = presets[idx]
                        val isSelected = fixedAspectRatio == ratio
                        val onClickAction: () -> Unit = {
                            aspectRatio?.let { imgAR ->
                                fixedAspectRatio = ratio
                                val targetSpanRatio = ratio / imgAR
                                val hSpan = if (targetSpanRatio > 1f) 1f else targetSpanRatio
                                val vSpan = if (targetSpanRatio > 1f) 1f / targetSpanRatio else 1f
                                horizontalRange = ((0.5f - hSpan/2f).coerceAtLeast(0f))..((0.5f + hSpan/2f).coerceAtMost(1f))
                                verticalRange = ((0.5f - vSpan/2f).coerceAtLeast(0f))..((0.5f + vSpan/2f).coerceAtMost(1f))
                            }
                        }
                        if (isSelected) {
                            androidx.compose.material3.Button(onClick = onClickAction) {
                                Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp).padding(end=4.dp))
                                Text(label)
                            }
                        } else {
                            androidx.compose.material3.OutlinedButton(onClick = onClickAction) {
                                Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp).padding(end=4.dp))
                                Text(label)
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        fixedAspectRatio = null
                        horizontalRange = 0f..1f
                        verticalRange = 0f..1f
                    }) {
                        Text(stringResource(R.string.reset))
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(onClick = {
                        val normalized = currentBounds.normalized()
                        val applied = normalized.takeUnless { it.isFull() }
                        onCropBoundsChange(applied)
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.apply))
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    length: Dp,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    androidx.compose.material3.RangeSlider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        modifier = modifier
            .rotate(-90f)
            .requiredWidth(length)
    )
}

@Composable
private fun rememberImageDimensions(imageUri: Uri?): androidx.compose.ui.unit.IntSize? {
    val context = LocalContext.current
    var dimensions by remember(imageUri) { mutableStateOf<androidx.compose.ui.unit.IntSize?>(null) }

    LaunchedEffect(imageUri, context) {
        dimensions = imageUri?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeStream(stream, null, options)
                        if (options.outWidth > 0 && options.outHeight > 0) {
                            androidx.compose.ui.unit.IntSize(options.outWidth, options.outHeight)
                        } else {
                            null
                        }
                    }
                }.getOrNull()
            }
        }
    }
    return dimensions
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
    val processingLabel = stringResource(R.string.processing_dots)
    val completedLabel = stringResource(R.string.completed)
    val title = if (state.isProcessing) processingLabel else completedLabel
    
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            val fileSingular = stringResource(R.string.file_singular)
            val filePlural = stringResource(R.string.file_plural)
            val processedLabel = stringResource(R.string.processed)
            val fileLabel = if (state.totalCount == 1) fileSingular else filePlural
            Text("${state.processedCount}/${state.totalCount} $fileLabel $processedLabel", style = MaterialTheme.typography.bodySmall)
            state.lastResult?.let { last ->
                val lastItemName = remember(last.source) {
                    DocumentFile.fromSingleUri(context, last.source)?.name ?: last.source.lastPathSegment ?: ""
                }
                val lastItemLabel = stringResource(R.string.last_item)
                Text("$lastItemLabel: $lastItemName", style = MaterialTheme.typography.labelSmall)
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
            Text(stringResource(R.string.results), style = MaterialTheme.typography.titleMedium)
            if (successfulResults.isNotEmpty()) {
                Text("${stringResource(R.string.total_label)} ${totalOriginal.asReadableBytes()} → ${totalCompressed.asReadableBytes()}", style = MaterialTheme.typography.bodyMedium)
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
        androidx.compose.ui.window.Dialog(onDismissRequest = {
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
                        Text(stringResource(R.string.close))
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
                    Text(stringResource(R.string.before), style = MaterialTheme.typography.bodySmall)
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
                    Text(stringResource(R.string.after), style = MaterialTheme.typography.bodySmall)
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
            onToggleTextEditor = {},
            onTextConfigChange = { _ -> },
            onToggleCropEditor = {},
            onCropBoundsChange = { _ -> },
            onToggleResizeEditor = {},
            onResizeConfigChange = { _ -> },
            onBackgroundConfigChange = { _ -> },
            onToggleColorFilterEditor = {},
            onColorFilterChange = { _ -> },
            onRotate = { _ -> },
            onResetEditsClick = {},
            onNavigateToSettings = {},
            onNavigateToHome = {},
            onNavigateToAbout = {},
            onNavigateToContact = {},
            onNavigateToDonate = {},
            onThemeModeChange = {},
            onPickDefaultDestination = {},
            onClearDefaultDestination = {},
            onDeleteOriginalsChange = {},

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
    canResize: Boolean,
    isResizeEditorVisible: Boolean,
    isResizeApplied: Boolean,
    onResizeClick: () -> Unit,
    
    onRotateClick: () -> Unit,
    onColorFilterClick: () -> Unit,
    exifEditingEnabled: Boolean = false,
    isEditingExif: Boolean = false,
    onToggleExifEditor: () -> Unit = {},
    onResetEditsClick: () -> Unit,
    isMultipleImages: Boolean = false, // NUOVO
    stripExif: Boolean = false,        // NUOVO
    canText: Boolean = false,
    isTextEditorVisible: Boolean = false,
    isTextApplied: Boolean = false,
    onTextClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var zoomTarget by remember { mutableStateOf<Uri?>(null) }
    var zoomLabel by remember { mutableStateOf<String?>(null) }
    Card(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.live_preview), style = MaterialTheme.typography.titleMedium)
                    TextButton(
                        onClick = onResetEditsClick,
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.reset),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.reset))
                    }
                }
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (canCrop) {
                        TextButton(onClick = onCropClick) {
                            Icon(androidx.compose.material.icons.Icons.Default.Crop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            val label = when {
                                isCropEditorVisible -> stringResource(R.string.crop)
                                isCropApplied -> stringResource(R.string.edit_crop)
                                else -> stringResource(R.string.crop)
                            }
                            Text(label)
                        }
                    }
                    if (canResize) {
                        TextButton(onClick = onResizeClick) {
                            Icon(androidx.compose.material.icons.Icons.Default.PhotoSizeSelectLarge, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            val label = when {
                                isResizeEditorVisible -> stringResource(R.string.resize)
                                isResizeApplied -> stringResource(R.string.edit_resize)
                                else -> stringResource(R.string.resize)
                            }
                            Text(label)
                        }
                    }
                    TextButton(onClick = onRotateClick) {
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.RotateRight, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.rotate))
                    }
                    TextButton(onClick = onColorFilterClick) {
                        Icon(androidx.compose.material.icons.Icons.Default.Palette, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.color_filter))
                    }
                    TextButton(onClick = onToggleExifEditor, enabled = exifEditingEnabled) {
                        Icon(androidx.compose.material.icons.Icons.Default.Settings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        val exifLabel = if (isMultipleImages) {
                            if (stripExif) "Restore EXIF" else "Remove EXIF"
                        } else {
                            "EXIF"
                        }
                        Text(exifLabel)
                    }
                    TextButton(
                        onClick = onTextClick,
                        enabled = canText
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Default.TextFields, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        val label = when {
                            isTextEditorVisible -> stringResource(R.string.text_label)
                            isTextApplied -> stringResource(R.string.edit_text)
                            else -> stringResource(R.string.text_label)
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
                            Text("${stringResource(R.string.total_label)} ${totalOriginal.asReadableBytes()} → ${totalEstimated.asReadableBytes()}", style = MaterialTheme.typography.bodyMedium)
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
                                Text(stringResource(R.string.generating_preview), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
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
                            Text(stringResource(R.string.updating_preview), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
    if (zoomTarget != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {
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
                        Text(stringResource(R.string.close))
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
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        // Before
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.before), style = MaterialTheme.typography.bodySmall)
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

        // Cropped (Optional)
        if (preview.croppedPreviewUri != null) {
            val strCropped = stringResource(R.string.cropped)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.cropped), style = MaterialTheme.typography.bodySmall)
                val context = androidx.compose.ui.platform.LocalContext.current
                val croppedRequest = remember(preview) {
                    coil.request.ImageRequest.Builder(context)
                        .data(preview.croppedPreviewUri)
                        .memoryCacheKey("crop_${System.identityHashCode(preview)}")
                        .diskCacheKey("crop_${System.identityHashCode(preview)}")
                        .build()
                }
                AsyncImage(
                    model = croppedRequest,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onPreviewClick(preview.croppedPreviewUri, strCropped) },
                    contentScale = ContentScale.Crop
                )
                Text("-", style = MaterialTheme.typography.bodySmall)
            }
        }

        // After
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.after), style = MaterialTheme.typography.bodySmall)
            val context = androidx.compose.ui.platform.LocalContext.current
            val request = remember(preview) {
                coil.request.ImageRequest.Builder(context)
                    .data(preview.previewUri)
                    .memoryCacheKey("after_${System.identityHashCode(preview)}")
                    .diskCacheKey("after_${System.identityHashCode(preview)}")
                    .build()
            }
            AsyncImage(
                model = request,
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

    val context = androidx.compose.ui.platform.LocalContext.current
    val request = remember(uri) {
        coil.request.ImageRequest.Builder(context)
            .data(uri)
            .memoryCacheKey(uri?.toString() + "_" + System.currentTimeMillis())
            .diskCacheKey(uri?.toString() + "_" + System.currentTimeMillis())
            .build()
    }

    AsyncImage(
        model = request,
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
                    onDoubleTap = { tapOffset ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val newScale = 2.5f
                            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                            offset = (center - tapOffset) * (newScale - 1f)
                            scale = newScale
                        }
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

@Composable
fun ResizeEditorDialog(
    config: com.kompact.model.ResizeConfig?,
    previewUri: Uri?,
    onConfigChange: (com.kompact.model.ResizeConfig?) -> Unit,
    onDismiss: () -> Unit
) {
    var isPercentage by remember { mutableStateOf(config?.isPercentage ?: true) }
    var currentPct by remember { mutableFloatStateOf(if (isPercentage) (config?.width?.toFloat() ?: 100f) else 100f) }

    var widthStr by remember { mutableStateOf(if (!isPercentage) config?.width?.takeIf { it > 0 }?.toString() ?: "" else "") }
    var heightStr by remember { mutableStateOf(if (!isPercentage) config?.height?.takeIf { it > 0 }?.toString() ?: "" else "") }
    
    var maintainAspect by remember { mutableStateOf(config?.maintainAspectRatio ?: true) }
    
    val imageDimensions = rememberImageDimensions(previewUri)
    val aspectRatio = rememberImageAspectRatio(previewUri)

    LaunchedEffect(imageDimensions) {
        if (imageDimensions != null && widthStr.isEmpty() && heightStr.isEmpty()) {
            val scale = if (isPercentage) currentPct / 100f else 1f
            widthStr = (imageDimensions.width * scale).roundToInt().toString()
            heightStr = (imageDimensions.height * scale).roundToInt().toString()
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val maxImageHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.45f
        androidx.compose.material3.Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.resize), style = MaterialTheme.typography.titleLarge)
                    androidx.compose.material3.IconButton(onClick = onDismiss) {
                        Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Close")
                    }
                }
                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .heightIn(max = maxImageHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewUri != null) {
                        coil.compose.AsyncImage(
                            model = previewUri,
                            contentDescription = "Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(stringResource(R.string.no_preview_available))
                    }
                }

                Spacer(Modifier.height(16.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Slider percentage
                    Column(modifier = Modifier.fillMaxWidth().alpha(if (isPercentage) 1f else 0.5f)) {
                        Text("${stringResource(R.string.scale_label)} ${currentPct.toInt()}%", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = currentPct,
                            onValueChange = { 
                                if (isPercentage) {
                                    currentPct = it
                                    imageDimensions?.let { dims ->
                                        widthStr = (dims.width * (it / 100f)).roundToInt().toString()
                                        heightStr = (dims.height * (it / 100f)).roundToInt().toString()
                                    }
                                }
                            },
                            valueRange = 1f..100f,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isPercentage
                        )
                    }

                    // Pixel Inputs
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = widthStr,
                            onValueChange = { 
                                if (widthStr != it) {
                                    isPercentage = false // Lock percentage
                                    widthStr = it 
                                    if (maintainAspect && aspectRatio != null && it.isNotEmpty()) {
                                        it.toIntOrNull()?.let { w ->
                                            heightStr = (w / aspectRatio).roundToInt().toString()
                                        }
                                    }
                                }
                            },
                            label = { Text(stringResource(R.string.resize_width)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = heightStr,
                            onValueChange = { 
                                if (heightStr != it) {
                                    isPercentage = false // Lock percentage
                                    heightStr = it 
                                    if (maintainAspect && aspectRatio != null && it.isNotEmpty()) {
                                        it.toIntOrNull()?.let { h ->
                                            widthStr = (h * aspectRatio).roundToInt().toString()
                                        }
                                    }
                                }
                            },
                            label = { Text(stringResource(R.string.resize_height)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = maintainAspect, onCheckedChange = { maintainAspect = it })
                        Text(stringResource(R.string.resize_keep_aspect))
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = {
                        isPercentage = true
                        currentPct = 100f
                        maintainAspect = true
                        widthStr = imageDimensions?.width?.toString() ?: ""
                        heightStr = imageDimensions?.height?.toString() ?: ""
                        onConfigChange(null)
                    }) { Text(stringResource(R.string.reset)) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Button(onClick = {
                        if (isPercentage) {
                            onConfigChange(com.kompact.model.ResizeConfig(currentPct.toInt(), currentPct.toInt(), maintainAspect, true))
                        } else {
                            val w = widthStr.toIntOrNull() ?: 0
                            val h = heightStr.toIntOrNull() ?: 0
                            if (w > 0 || h > 0) {
                                onConfigChange(com.kompact.model.ResizeConfig(w, h, maintainAspect, false))
                            }
                        }
                        onDismiss()
                    }) { Text(stringResource(R.string.apply)) }
                }
            }
        }
    }
}

@Composable
fun FiltersEditorDialog(
    currentColorConfig: com.kompact.model.ColorFilterConfig?,
    currentBackgroundConfig: com.kompact.model.BackgroundConfig?,
    previewUri: android.net.Uri?,
    showJpegWarning: Boolean,
    onConfirm: (com.kompact.model.ColorFilterConfig?, com.kompact.model.BackgroundConfig?) -> Unit,
    onDismiss: () -> Unit
) {
    var localColorConfig by remember { androidx.compose.runtime.mutableStateOf(currentColorConfig ?: com.kompact.model.ColorFilterConfig()) }
    var localBgConfig by remember { androidx.compose.runtime.mutableStateOf(currentBackgroundConfig ?: com.kompact.model.BackgroundConfig()) }
    
    var selectedTab by remember { androidx.compose.runtime.mutableStateOf(
        if (currentColorConfig == null) "NONE"
        else currentColorConfig.filterType.name
    ) }

    val filterTypes = com.kompact.model.ColorFilterType.values().toList()
    
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val maxImageHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.45f
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 24.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.action_color_filter),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    androidx.compose.material3.IconButton(onClick = onDismiss) {
                        Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxImageHeight)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when (localBgConfig.type) {
                                com.kompact.model.BackgroundType.TRANSPARENT -> androidx.compose.ui.graphics.Color.Transparent
                                com.kompact.model.BackgroundType.CUSTOM -> androidx.compose.ui.graphics.Color(localBgConfig.customColor)
                                else -> androidx.compose.ui.graphics.Color.Black
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewUri != null) {
                        coil.compose.AsyncImage(
                            model = previewUri,
                            contentDescription = "Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            colorFilter = localColorConfig.toComposeColorFilter()
                        )
                    } else {
                        Text(stringResource(R.string.no_preview_available))
                    }
                }

                Spacer(Modifier.height(16.dp))
                
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    val itemsList = filterTypes.map { it.name }.toMutableList()
                    val customIdx = itemsList.indexOf("CUSTOM")
                    if(customIdx != -1) {
                        itemsList.add(customIdx + 1, "BACKGROUND")
                    } else {
                        itemsList.add("BACKGROUND")
                    }
                    
                    items(itemsList.size) { index ->
                        val item = itemsList[index]
                        val isSelected = (selectedTab == item)
                        
                        Column(
                            modifier = Modifier
                                .width(64.dp)
                                .clickable { 
                                    selectedTab = item
                                    if(item != "BACKGROUND") {
                                        val newFilterType = com.kompact.model.ColorFilterType.valueOf(item)
                                        val newIntensity = if (newFilterType == com.kompact.model.ColorFilterType.CUSTOM) 0.5f else 1.0f
                                        localColorConfig = localColorConfig.copy(filterType = newFilterType, intensity = newIntensity)
                                    }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .background(if (item == "BACKGROUND") MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                if (item == "BACKGROUND") {
                                    val checkerColor1 = androidx.compose.ui.graphics.Color.LightGray.copy(alpha = 0.5f)
                                    val checkerColor2 = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.3f)
                                    androidx.compose.foundation.Canvas(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp))) {
                                        val squareSize = size.width / 4
                                        for (row in 0 until 4) {
                                            for (col in 0 until 4) {
                                                val isEven = (row + col) % 2 == 0
                                                drawRect(
                                                    color = if (isEven) checkerColor1 else checkerColor2,
                                                    topLeft = androidx.compose.ui.geometry.Offset(col * squareSize, row * squareSize),
                                                    size = androidx.compose.ui.geometry.Size(squareSize, squareSize)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    val fType = com.kompact.model.ColorFilterType.valueOf(item)
                                    coil.compose.AsyncImage(
                                        model = previewUri ?: R.drawable.ic_launcher,
                                        contentDescription = item,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        colorFilter = com.kompact.model.ColorFilterConfig(filterType = fType).toComposeColorFilter()
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = when (item) {
                                    "NONE" -> stringResource(R.string.default_text)
                                    "CUSTOM" -> stringResource(R.string.filter_custom)
                                    "GRAYSCALE" -> stringResource(R.string.filter_grayscale)
                                    "SEPIA" -> stringResource(R.string.filter_sepia)
                                    "VINTAGE" -> stringResource(R.string.filter_vintage)
                                    "COOL" -> stringResource(R.string.filter_cool)
                                    "WARM" -> stringResource(R.string.filter_warm)
                                    "BACKGROUND" -> stringResource(R.string.filter_background)
                                    else -> item.lowercase().replaceFirstChar { it.uppercase() }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))

                if (selectedTab == "BACKGROUND") {
                    Text(
                        text = stringResource(R.string.background_type),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    
                    if (showJpegWarning && localBgConfig.type == com.kompact.model.BackgroundType.TRANSPARENT) {
                        Text(
                            text = stringResource(R.string.warning_jpeg_does_not_support_transparency),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        BackgroundOption(
                            label = stringResource(R.string.transp),
                            color = androidx.compose.ui.graphics.Color.Transparent,
                            isSelected = localBgConfig.type == com.kompact.model.BackgroundType.TRANSPARENT,
                            onClick = { localBgConfig = localBgConfig.copy(type = com.kompact.model.BackgroundType.TRANSPARENT) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BackgroundOption(
                            label = stringResource(R.string.black),
                            color = androidx.compose.ui.graphics.Color.Black,
                            isSelected = localBgConfig.type == com.kompact.model.BackgroundType.CUSTOM && localBgConfig.customColor == android.graphics.Color.BLACK,
                            onClick = {
                                localBgConfig = localBgConfig.copy(
                                    type = com.kompact.model.BackgroundType.CUSTOM,
                                    customColor = android.graphics.Color.BLACK
                                )
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BackgroundOption(
                            label = stringResource(R.string.white),
                            color = androidx.compose.ui.graphics.Color.White,
                            isSelected = localBgConfig.type == com.kompact.model.BackgroundType.CUSTOM && localBgConfig.customColor == android.graphics.Color.WHITE,
                            onClick = {
                                localBgConfig = localBgConfig.copy(
                                    type = com.kompact.model.BackgroundType.CUSTOM,
                                    customColor = android.graphics.Color.WHITE
                                )
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        BackgroundOption(
                            label = stringResource(R.string.red),
                            color = androidx.compose.ui.graphics.Color.Red,
                            isSelected = localBgConfig.type == com.kompact.model.BackgroundType.CUSTOM && localBgConfig.customColor == android.graphics.Color.RED,
                            onClick = {
                                localBgConfig = localBgConfig.copy(
                                    type = com.kompact.model.BackgroundType.CUSTOM,
                                    customColor = android.graphics.Color.RED
                                )
                            }
                        )
                    }
                } 
                
                if (selectedTab == "CUSTOM") {
                    Text(
                        text = stringResource(R.string.hue),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    val gradientBrush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                        colors = listOf(
                            androidx.compose.ui.graphics.Color.Red,
                            androidx.compose.ui.graphics.Color.Yellow,
                            androidx.compose.ui.graphics.Color.Green,
                            androidx.compose.ui.graphics.Color.Cyan,
                            androidx.compose.ui.graphics.Color.Blue,
                            androidx.compose.ui.graphics.Color.Magenta,
                            androidx.compose.ui.graphics.Color.Red
                        )
                    )

                    Box(modifier = Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                        Spacer(modifier = Modifier.matchParentSize().padding(vertical = 8.dp).clip(RoundedCornerShape(8.dp)).background(gradientBrush))
                        androidx.compose.material3.Slider(
                            value = localColorConfig.hue,
                            onValueChange = { localColorConfig = localColorConfig.copy(hue = it) },
                            valueRange = 0f..360f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = androidx.compose.ui.graphics.Color.White,
                                activeTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                                inactiveTrackColor = androidx.compose.ui.graphics.Color.Transparent
                            )
                        )
                    }
                } 
                
                if (selectedTab != "NONE" && selectedTab != "BACKGROUND") {
                    Text(
                        text = "${stringResource(R.string.intensity)}: ${(localColorConfig.intensity * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.material3.Slider(
                        value = localColorConfig.intensity,
                        onValueChange = { localColorConfig = localColorConfig.copy(intensity = it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { 
                        selectedTab = "NONE"
                        localColorConfig = com.kompact.model.ColorFilterConfig() 
                        localBgConfig = com.kompact.model.BackgroundConfig()
                    }) {
                        Text(stringResource(R.string.reset))
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    TextButton(
                        onClick = {
                            val colorRes = if (localColorConfig.filterType == com.kompact.model.ColorFilterType.NONE) null else localColorConfig
                            val bgRes = if (localBgConfig.type == com.kompact.model.BackgroundType.TRANSPARENT) null else localBgConfig
                            onConfirm(colorRes, bgRes)
                            onDismiss()
                        }
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            }
        }
    }
}

@Composable
fun RotateEditorDialog(
    currentRotation: Float,
    previewUri: Uri?,
    colorFilter: com.kompact.model.ColorFilterConfig?,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var localRotation by remember { mutableFloatStateOf(if (currentRotation > 180f) currentRotation - 360f else currentRotation) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val maxImageHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.45f
        androidx.compose.material3.Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.action_rotate), style = MaterialTheme.typography.titleLarge)
                    androidx.compose.material3.IconButton(onClick = onDismiss) {
                        Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Close")
                    }
                }
                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .heightIn(max = maxImageHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewUri != null) {
                        coil.compose.AsyncImage(
                            model = previewUri,
                            contentDescription = "Preview",
                            modifier = Modifier
                                .fillMaxSize()
                                .rotate(localRotation),
                            contentScale = ContentScale.Fit,
                            colorFilter = colorFilter?.toComposeColorFilter()
                        )
                    } else {
                        Text(stringResource(R.string.no_preview_available))
                    }
                }

                Spacer(Modifier.height(16.dp))
                
                Text(text = stringResource(R.string.degrees_format, localRotation.toInt()), style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.Slider(
                    value = localRotation,
                    onValueChange = { localRotation = it },
                    valueRange = -180f..180f,
                    steps = 35,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(onClick = { 
                        val rem = kotlin.math.abs((localRotation % 90f).toInt())
                        if (rem != 0) {
                            localRotation = -90f
                        } else {
                            localRotation -= 90f
                            if (localRotation < -180f) localRotation += 360f
                        }
                    }) { 
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.RotateLeft, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.rotate_left)) 
                    }
                    Spacer(Modifier.width(16.dp))
                    Button(onClick = { 
                        val rem = kotlin.math.abs((localRotation % 90f).toInt())
                        if (rem != 0) {
                            localRotation = 90f
                        } else {
                            localRotation += 90f
                            if (localRotation > 180f) localRotation -= 360f
                        }
                    }) { 
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.RotateRight, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.rotate_right)) 
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = { localRotation = 0f }) { Text(stringResource(R.string.reset)) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Button(onClick = {
                        onConfirm(localRotation)
                        onDismiss()
                    }) { Text(stringResource(R.string.apply)) }
                }
            }
        }
    }
}

@Composable
fun BackgroundOption(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (color == androidx.compose.ui.graphics.Color.Transparent) MaterialTheme.colorScheme.surfaceVariant else color)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (color == androidx.compose.ui.graphics.Color.Transparent) {
                Text(stringResource(R.string.text_tool), style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}
