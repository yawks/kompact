package com.kompact.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.kompact.R
import com.kompact.model.TextOverlayConfig

@Composable
private fun ControlRow(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onSliderChange: (Float) -> Unit,
    sliderValue: Float,
    sliderRange: ClosedFloatingPointRange<Float>,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (isCompact) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    label,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.widthIn(min = 35.dp, max = 50.dp)
                )
                IconButton(
                    onClick = onDecrease,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Slider(
                    value = sliderValue,
                    onValueChange = onSliderChange,
                    valueRange = sliderRange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 24.dp),
                    colors = SliderDefaults.colors()
                )
                IconButton(
                    onClick = onIncrease,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Text(
                    value,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.widthIn(min = 35.dp, max = 50.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp
                )
            }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                label,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.widthIn(min = 40.dp, max = 55.dp)
            )
            IconButton(
                onClick = onDecrease,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White)
            }
            Slider(
                value = sliderValue,
                onValueChange = onSliderChange,
                valueRange = sliderRange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 28.dp),
                colors = SliderDefaults.colors()
            )
            IconButton(
                onClick = onIncrease,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
            }
            Text(
                value,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.widthIn(min = 40.dp, max = 55.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorDialog(
    imageUri: Uri?,
    textConfig: TextOverlayConfig?,
    onTextConfigChange: (TextOverlayConfig?) -> Unit,
    onDismiss: () -> Unit
) {
    // State per il testo e la configurazione
    var text by remember { mutableStateOf(textConfig?.text ?: "") }
    var color by remember { mutableStateOf(Color(textConfig?.color ?: android.graphics.Color.WHITE)) }
    var size by remember { mutableStateOf(textConfig?.size ?: 50f) }
    var scale by remember { mutableStateOf(textConfig?.scale ?: 1f) }
    var rotation by remember { mutableStateOf(textConfig?.rotation ?: 0f) }
    
    // State per la posizione - NORMALIZZATA (0-1)
    // offsetX e offsetY sono in coordinate normalizzate (0-1) dove (0.5, 0.5) = centro
    var offsetX by remember { mutableStateOf(textConfig?.offsetX ?: 0.5f) }
    var offsetY by remember { mutableStateOf(textConfig?.offsetY ?: 0.5f) }
    
    // State per l'editing
    var isEditing by remember { mutableStateOf(text.isEmpty()) }
    var isDragging by remember { mutableStateOf(false) }
    
    // Dimensione del container
    var containerWidth by remember { mutableStateOf(1f) }
    var containerHeight by remember { mutableStateOf(1f) }
    
    // Focus manager
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Gestione focus
    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    // Double tap per reset
    fun handleDoubleTap() {
        scale = 1f
        rotation = 0f
        offsetX = 0.5f
        offsetY = 0.5f
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
                            stringResource(R.string.add_text),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        ) 
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (text.isBlank()) {
                                    isEditing = false
                                    focusManager.clearFocus()
                                    onDismiss()
                                } else {
                                    isEditing = false
                                    focusManager.clearFocus()
                                    onTextConfigChange(
                                        TextOverlayConfig(
                                            text = text.trim(),
                                            color = color.toArgb(),
                                            size = size,
                                            scale = scale,
                                            offsetX = offsetX,
                                            offsetY = offsetY,
                                            rotation = rotation
                                        )
                                    )
                                    onDismiss()
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Save Text",
                                tint = if (text.isNotBlank()) Color.Green else Color.Gray
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
            },
            bottomBar = {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .background(Color.Black.copy(alpha = 0.9f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .padding(bottom = 56.dp)
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Colors Section
                        Text(
                            "Colors",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        ) {
                            val containerMaxWidth = this@BoxWithConstraints.maxWidth
                            val colorSize = if (containerMaxWidth < 600.dp) 32.dp else 40.dp
                            val colorSpacing = if (containerMaxWidth < 600.dp) 4.dp else 8.dp
                            
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(colorSpacing, Alignment.CenterHorizontally)
                            ) {
                                val colors = listOf(
                                    Color.White, Color.Black, Color(0xFFF44336), Color(0xFFE91E63), 
                                    Color(0xFF9C27B0), Color(0xFF673AB7), Color(0xFF3F51B5), 
                                    Color(0xFF2196F3), Color(0xFF03A9F4), Color(0xFF00BCD4), 
                                    Color(0xFF009688), Color(0xFF4CAF50), Color(0xFF8BC34A), 
                                    Color(0xFFCDDC39), Color(0xFFFFEB3B), Color(0xFFFFC107), 
                                    Color(0xFFFF9800), Color(0xFFFF5722), Color(0xFF795548), Color(0xFF9E9E9E)
                                )
                                items(colors) { c ->
                                    Box(
                                        modifier = Modifier
                                            .size(colorSize)
                                            .background(c, CircleShape)
                                            .border(
                                                if (color == c) 2.dp else 0.dp,
                                                Color.White,
                                                CircleShape
                                            )
                                            .clickable { color = c }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 0.5.dp)

                        // Control Section - Responsive layout
                        val containerMaxWidth = this@BoxWithConstraints.maxWidth
                        if (containerMaxWidth < 600.dp) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ControlRow(
                                    label = "Font",
                                    value = "%.0f".format(size),
                                    onDecrease = { size = (size - 5f).coerceIn(20f, 120f) },
                                    onIncrease = { size = (size + 5f).coerceIn(20f, 120f) },
                                    onSliderChange = { size = it },
                                    sliderValue = size,
                                    sliderRange = 20f..120f,
                                    isCompact = true
                                )
                                ControlRow(
                                    label = "Scale",
                                    value = "%.1f".format(scale),
                                    onDecrease = { scale = (scale - 0.1f).coerceIn(0.5f, 5f) },
                                    onIncrease = { scale = (scale + 0.1f).coerceIn(0.5f, 5f) },
                                    onSliderChange = { scale = it },
                                    sliderValue = scale,
                                    sliderRange = 0.5f..5f,
                                    isCompact = true
                                )
                                ControlRow(
                                    label = "Rotate",
                                    value = "%.0f\u00B0".format(rotation),
                                    onDecrease = { rotation = (rotation - 15f + 360f) % 360f },
                                    onIncrease = { rotation = (rotation + 15f) % 360f },
                                    onSliderChange = { rotation = it },
                                    sliderValue = rotation,
                                    sliderRange = 0f..360f,
                                    isCompact = true
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ControlRow(
                                    label = "Font",
                                    value = "%.0f".format(size),
                                    onDecrease = { size = (size - 5f).coerceIn(20f, 120f) },
                                    onIncrease = { size = (size + 5f).coerceIn(20f, 120f) },
                                    onSliderChange = { size = it },
                                    sliderValue = size,
                                    sliderRange = 20f..120f,
                                    isCompact = false,
                                    modifier = Modifier.weight(1f)
                                )
                                ControlRow(
                                    label = "Scale",
                                    value = "%.1f".format(scale),
                                    onDecrease = { scale = (scale - 0.1f).coerceIn(0.5f, 5f) },
                                    onIncrease = { scale = (scale + 0.1f).coerceIn(0.5f, 5f) },
                                    onSliderChange = { scale = it },
                                    sliderValue = scale,
                                    sliderRange = 0.5f..5f,
                                    isCompact = false,
                                    modifier = Modifier.weight(1f)
                                )
                                ControlRow(
                                    label = "Rotate",
                                    value = "%.0f\u00B0".format(rotation),
                                    onDecrease = { rotation = (rotation - 15f + 360f) % 360f },
                                    onIncrease = { rotation = (rotation + 15f) % 360f },
                                    onSliderChange = { rotation = it },
                                    sliderValue = rotation,
                                    sliderRange = 0f..360f,
                                    isCompact = false,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color.Black)
            ) {
                // Salva le dimensioni del container
                containerWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                containerHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                
                // Calcola le dimensioni della zona di lavoro (85% del container, aspetto 1:1)
                val maxImageSize = containerWidth.coerceAtMost(containerHeight) * 0.85f
                
                // Immagine di sfondo
                imageUri?.let { uri ->
                    Box(
                        modifier = Modifier
                            .width(maxImageSize.dp)
                            .height(maxImageSize.dp)
                            .align(Alignment.Center)
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(uri),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                // Overlay scuro quando si modifica il testo
                if (isEditing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                    )
                }

                // Layer per gesture di trasformazione (pinch/zoom/rotate/drag)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, transformRotation ->
                                if (!isEditing && !isDragging) {
                                    // Pan: sposta il testo (converte in offset normalizzato)
                                    val panX = pan.x / maxImageSize
                                    val panY = pan.y / maxImageSize
                                    offsetX = (offsetX + panX).coerceIn(0f, 1f)
                                    offsetY = (offsetY + panY).coerceIn(0f, 1f)
                                    
                                    // Zoom: scala il testo
                                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                                    
                                    // Rotazione
                                    rotation += transformRotation
                                }
                            }
                        }
                ) {
                    // Elemento testo - posizionato al centro con offset normalizzato
                    Box(
                        modifier = Modifier
                            .width(maxImageSize.dp)
                            .height(maxImageSize.dp)
                            .align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        // Calcola la posizione usando offset normalizzato [0, 1]
                        // (0.5, 0.5) = centro, (0, 0) = top-left, (1, 1) = bottom-right
                        val textModifier = Modifier
                            .graphicsLayer(
                                translationX = (offsetX - 0.5f) * maxImageSize,
                                translationY = (offsetY - 0.5f) * maxImageSize,
                                scaleX = scale,
                                scaleY = scale,
                                rotationZ = rotation
                            )
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { isDragging = true },
                                    onDragEnd = { isDragging = false },
                                    onDragCancel = { isDragging = false },
                                    onDrag = { change, dragAmount ->
                                        if (!isEditing) {
                                            change.consume()
                                            // Converte dragAmount in offset normalizzato
                                            val dragX = dragAmount.x / maxImageSize
                                            val dragY = dragAmount.y / maxImageSize
                                            offsetX = (offsetX + dragX).coerceIn(0f, 1f)
                                            offsetY = (offsetY + dragY).coerceIn(0f, 1f)
                                        }
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { 
                                        if (isEditing) {
                                            isEditing = false
                                            focusManager.clearFocus()
                                        }
                                    },
                                    onDoubleTap = { 
                                        if (!isEditing) {
                                            handleDoubleTap()
                                        }
                                    }
                                )
                            }
                            .border(
                                width = if (!isEditing && text.isNotEmpty()) 2.dp else 0.dp,
                                color = Color.White.copy(alpha = 0.8f),
                                shape = RoundedCornerShape(4.dp)
                            )

                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            textStyle = TextStyle(
                                color = color,
                                fontSize = size.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(alpha = 0.7f),
                                    offset = Offset(2f, 2f),
                                    blurRadius = 4f
                                )
                            ),
                            cursorBrush = SolidColor(color),
                            modifier = textModifier
                                .focusRequester(focusRequester)
                                .widthIn(min = 50.dp, max = maxImageSize.dp * 0.9f),
                            singleLine = false,
                            decorationBox = { innerTextField ->
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    if (text.isEmpty()) {
                                        Text(
                                            text = if (isEditing) 
                                                stringResource(R.string.enter_text) 
                                            else 
                                                "Tap to add text",
                                            style = TextStyle(
                                                color = color.copy(alpha = 0.4f),
                                                fontSize = size.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
