package com.kompact.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness3
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.kompact.R
import com.kompact.model.AdjustConfig
import com.kompact.model.CropBounds
import com.kompact.util.applyAdjust
import com.kompact.util.applyCrop
import com.kompact.util.applyRotation
import com.kompact.util.decodeBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Lato massimo del bitmap di preview: sufficiente per la visualizzazione,
// evita di decodificare l'immagine a piena risoluzione a ogni cambio slider
private const val ADJUST_PREVIEW_MAX_EDGE = 2048

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustEditorDialog(
    imageUri: Uri?,
    adjustConfig: AdjustConfig?,
    onAdjustConfigChange: (AdjustConfig?) -> Unit,
    onDismiss: () -> Unit,
    rotationDegrees: Float = 0f,
    cropBounds: CropBounds? = null
) {
    // State for adjustments
    var brightness by remember { mutableStateOf(adjustConfig?.brightness ?: 0f) }
    var contrast by remember { mutableStateOf(adjustConfig?.contrast ?: 0f) }
    var saturation by remember { mutableStateOf(adjustConfig?.saturation ?: 0f) }
    var warmth by remember { mutableStateOf(adjustConfig?.warmth ?: 0f) }
    var shadows by remember { mutableStateOf(adjustConfig?.shadows ?: 0f) }
    var highlights by remember { mutableStateOf(adjustConfig?.highlights ?: 0f) }

    // Selected effect for the intensity slider (the first non-zero one
    // when reopening an image already adjusted)
    var selectedEffect by remember { mutableStateOf(initialSelectedEffect(adjustConfig)) }

    // Preview bitmap state
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // Coroutine scope for loading preview
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var previewJob by remember { mutableStateOf<Job?>(null) }

    // Load and apply preview
    fun updatePreview() {
        if (imageUri == null) return

        previewJob?.cancel()
        previewJob = coroutineScope.launch {
            // Debounce: gli slider emettono molti cambi durante il trascinamento
            delay(150)
            val processed = withContext(Dispatchers.IO) {
                runCatching {
                    val bitmap = decodeBitmap(
                        context.contentResolver,
                        imageUri,
                        ADJUST_PREVIEW_MAX_EDGE
                    ) ?: return@withContext null

                    val config = AdjustConfig(
                        brightness = brightness,
                        contrast = contrast,
                        saturation = saturation,
                        warmth = warmth,
                        shadows = shadows,
                        highlights = highlights
                    )

                    // Mostra la stessa immagine che avrà l'output: EXIF (già
                    // normalizzato in decodeBitmap) -> rotazione -> crop -> adjust
                    bitmap
                        .applyRotation(rotationDegrees)
                        .applyCrop(cropBounds)
                        .applyAdjust(config)
                }.getOrNull()
            }
            if (processed != null) {
                previewBitmap = processed
            }
        }
    }
    
    // Update preview when any value changes
    LaunchedEffect(brightness, contrast, saturation, warmth, shadows, highlights) {
        updatePreview()
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Black,
            topBar = {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    title = { 
                        Text(
                            stringResource(R.string.adjust),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        ) 
                    },
                    actions = {
                        // Reset button
                        IconButton(
                            onClick = {
                                brightness = 0f
                                contrast = 0f
                                saturation = 0f
                                warmth = 0f
                                shadows = 0f
                                highlights = 0f
                            }
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Reset",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = {
                                val newConfig = AdjustConfig(
                                    brightness = brightness,
                                    contrast = contrast,
                                    saturation = saturation,
                                    warmth = warmth,
                                    shadows = shadows,
                                    highlights = highlights
                                )
                                onAdjustConfigChange(newConfig)
                                onDismiss()
                            }
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Save",
                                tint = Color.Green
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.9f),
                        titleContentColor = Color.White
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color.Black)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Preview image: il bitmap elaborato (rotazione + crop + adjust),
                // con fallback all'originale finché il primo preview non è pronto
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp)
                        .background(Color.DarkGray)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = previewBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                
                // Effect selection tiles (one next to the other) plus a
                // single intensity slider for the selected effect
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val effects = listOf(
                            AdjustEffectUi(
                                label = stringResource(R.string.brightness),
                                icon = Icons.Default.Brightness5,
                                value = brightness,
                                selected = selectedEffect == AdjustEffect.BRIGHTNESS,
                                onClick = { selectedEffect = AdjustEffect.BRIGHTNESS },
                                onValueChange = { brightness = it }
                            ),
                            AdjustEffectUi(
                                label = stringResource(R.string.contrast),
                                icon = Icons.Default.Tonality,
                                value = contrast,
                                selected = selectedEffect == AdjustEffect.CONTRAST,
                                onClick = { selectedEffect = AdjustEffect.CONTRAST },
                                onValueChange = { contrast = it }
                            ),
                            AdjustEffectUi(
                                label = stringResource(R.string.saturation),
                                icon = Icons.Default.InvertColors,
                                value = saturation,
                                selected = selectedEffect == AdjustEffect.SATURATION,
                                onClick = { selectedEffect = AdjustEffect.SATURATION },
                                onValueChange = { saturation = it }
                            ),
                            AdjustEffectUi(
                                label = stringResource(R.string.warmth),
                                icon = Icons.Default.Thermostat,
                                value = warmth,
                                selected = selectedEffect == AdjustEffect.WARMTH,
                                onClick = { selectedEffect = AdjustEffect.WARMTH },
                                onValueChange = { warmth = it }
                            ),
                            AdjustEffectUi(
                                label = stringResource(R.string.shadows),
                                icon = Icons.Default.Brightness3,
                                value = shadows,
                                selected = selectedEffect == AdjustEffect.SHADOWS,
                                onClick = { selectedEffect = AdjustEffect.SHADOWS },
                                onValueChange = { shadows = it }
                            ),
                            AdjustEffectUi(
                                label = stringResource(R.string.highlights),
                                icon = Icons.Default.LightMode,
                                value = highlights,
                                selected = selectedEffect == AdjustEffect.HIGHLIGHTS,
                                onClick = { selectedEffect = AdjustEffect.HIGHLIGHTS },
                                onValueChange = { highlights = it }
                            )
                        )

                        // Tiles laid out horizontally, one next to the other
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            effects.forEach { effect ->
                                AdjustEffectTile(
                                    label = effect.label,
                                    icon = effect.icon,
                                    value = effect.value,
                                    selected = effect.selected,
                                    onClick = effect.onClick,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Single intensity slider for the selected effect
                        val selected = effects.first { it.selected }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selected.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "%+.0f%%".format(selected.value),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                        Slider(
                            value = selected.value,
                            onValueChange = selected.onValueChange,
                            valueRange = -100f..100f
                        )
                    }
                }
            }
        }
    }
}

private enum class AdjustEffect { BRIGHTNESS, CONTRAST, SATURATION, WARMTH, SHADOWS, HIGHLIGHTS }

private fun initialSelectedEffect(config: AdjustConfig?): AdjustEffect {
    if (config == null) return AdjustEffect.BRIGHTNESS
    return when {
        config.brightness != 0f -> AdjustEffect.BRIGHTNESS
        config.contrast != 0f -> AdjustEffect.CONTRAST
        config.saturation != 0f -> AdjustEffect.SATURATION
        config.warmth != 0f -> AdjustEffect.WARMTH
        config.shadows != 0f -> AdjustEffect.SHADOWS
        config.highlights != 0f -> AdjustEffect.HIGHLIGHTS
        else -> AdjustEffect.BRIGHTNESS
    }
}

private data class AdjustEffectUi(
    val label: String,
    val icon: ImageVector,
    val value: Float,
    val selected: Boolean,
    val onClick: () -> Unit,
    val onValueChange: (Float) -> Unit
)

@Composable
private fun AdjustEffectTile(
    label: String,
    icon: ImageVector,
    value: Float,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) Color.Blue.copy(alpha = 0.5f)
                else Color.Gray.copy(alpha = 0.3f)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
            if (value != 0f) {
                Text(
                    text = "%+.0f%%".format(value),
                    color = Color.White,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                )
            }
        }
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
