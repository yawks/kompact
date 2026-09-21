package com.kompact.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
                
                // Adjustment sliders
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AdjustSlider(
                            label = stringResource(R.string.brightness),
                            value = brightness,
                            onValueChange = { brightness = it },
                            valueRange = -100f..100f
                        )
                        
                        AdjustSlider(
                            label = stringResource(R.string.contrast),
                            value = contrast,
                            onValueChange = { contrast = it },
                            valueRange = -100f..100f
                        )
                        
                        AdjustSlider(
                            label = stringResource(R.string.saturation),
                            value = saturation,
                            onValueChange = { saturation = it },
                            valueRange = -100f..100f
                        )
                        
                        AdjustSlider(
                            label = stringResource(R.string.warmth),
                            value = warmth,
                            onValueChange = { warmth = it },
                            valueRange = -100f..100f
                        )
                        
                        AdjustSlider(
                            label = stringResource(R.string.shadows),
                            value = shadows,
                            onValueChange = { shadows = it },
                            valueRange = -100f..100f
                        )
                        
                        AdjustSlider(
                            label = stringResource(R.string.highlights),
                            value = highlights,
                            onValueChange = { highlights = it },
                            valueRange = -100f..100f
                        )
                    }
                }
                
                // Current values summary
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "${stringResource(R.string.brightness)}: ${"%.0f%%".format(brightness)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                        Text(
                            "${stringResource(R.string.contrast)}: ${"%.0f%%".format(contrast)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                        Text(
                            "${stringResource(R.string.saturation)}: ${"%.0f%%".format(saturation)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                        Text(
                            "${stringResource(R.string.warmth)}: ${"%.0f%%".format(warmth)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                        Text(
                            "${stringResource(R.string.shadows)}: ${"%.0f%%".format(shadows)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                        Text(
                            "${stringResource(R.string.highlights)}: ${"%.0f%%".format(highlights)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdjustSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${"%.0f%%".format(value)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                modifier = Modifier.width(50.dp),
                textAlign = TextAlign.Right
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
