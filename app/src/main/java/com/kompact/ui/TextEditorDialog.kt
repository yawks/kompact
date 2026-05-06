package com.kompact.ui

import android.net.Uri
import androidx.core.view.WindowCompat
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.kompact.R
import com.kompact.model.TextOverlayConfig
import kotlin.math.roundToInt
import android.util.Log

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
        // Vertical layout per schermi piccoli
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
        // Horizontal layout per schermi grandi
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
    var text by remember { mutableStateOf(textConfig?.text ?: "") }
    var color by remember { mutableStateOf(Color(textConfig?.color ?: android.graphics.Color.WHITE)) }
    var size by remember { mutableStateOf(textConfig?.size ?: 50f) }
    var scale by remember { mutableStateOf(textConfig?.scale ?: 1f) }
    var rotation by remember { mutableStateOf(textConfig?.rotation ?: 0f) }

    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var hasInitializedOffset by remember { mutableStateOf(false) }
    var containerWidth by remember { mutableStateOf(1f) }
    var containerHeight by remember { mutableStateOf(1f) }
    
    // Remembered state for offset calculation
    val savedContainerWidth = remember { mutableStateOf(1f) }
    val savedContainerHeight = remember { mutableStateOf(1f) }
    
    var isDragging by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(text.isEmpty()) }
    var lastTapTime by remember { mutableStateOf(0L) }
    var tapCount by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(isEditing) {
        if (isEditing) {
            try { focusRequester.requestFocus() } catch (e: Exception) {}
        } else {
            focusManager.clearFocus()
        }
    }

    // Logica per double tap detection
    fun handleTextTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTime < 300) {
            // Double tap detected
            tapCount++
            if (tapCount >= 2) {
                // Reset transforms
                scale = 1f
                rotation = 0f
                offsetX = 0f
                offsetY = 0f
                tapCount = 0
                lastTapTime = 0
            }
        } else {
            // Single tap
            tapCount = 1
            lastTapTime = now
            isEditing = true
            focusRequester.requestFocus()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val view = LocalView.current
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        LaunchedEffect(dialogWindow) {
            dialogWindow?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
            }
        }

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
                                Log.d("TextEditorDialog", "Save button clicked, textBlank=${text.isBlank()}")
                                if (text.isBlank()) {
                                    // Se il testo è vuoto, esci senza salvare
                                    Log.d("TextEditorDialog", "Text is blank, dismissing without save")
                                    isEditing = false
                                    focusManager.clearFocus()
                                    onDismiss()
                                } else {
                                    try {
                                        Log.d("TextEditorDialog", "Saving text: '$text', size=$size, scale=$scale, rotation=$rotation")
                                        val width = savedContainerWidth.value.coerceAtLeast(1f)
                                        val height = savedContainerHeight.value.coerceAtLeast(1f)
                                        
                                        val finalOffsetX = (offsetX / width) + 0.5f
                                        val finalOffsetY = (offsetY / height) + 0.5f
                                        
                                        val newConfig = TextOverlayConfig(
                                            text = text.trim(),
                                            color = color.toArgb(),
                                            size = size,
                                            scale = scale,
                                            offsetX = finalOffsetX.coerceIn(0f, 1f),
                                            offsetY = finalOffsetY.coerceIn(0f, 1f),
                                            rotation = rotation
                                        )
                                        
                                        Log.d("TextEditorDialog", "Created config: offsetX=${newConfig.offsetX}, offsetY=${newConfig.offsetY}, size=${newConfig.size}, restrictedBox=${width}x${height}")
                                        
                                        // Esci dall'editing
                                        isEditing = false
                                        focusManager.clearFocus()
                                        
                                        // Salva e chiudi
                                        Log.d("TextEditorDialog", "Calling onTextConfigChange...")
                                        onTextConfigChange(newConfig)
                                        Log.d("TextEditorDialog", "Calling onDismiss...")
                                        onDismiss()
                                        Log.d("TextEditorDialog", "After onDismiss")
                                    } catch (e: Exception) {
                                        Log.e("TextEditorDialog", "Error saving text", e)
                                        e.printStackTrace()
                                        // Fallback: chiudi comunque
                                        onDismiss()
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Save Text",
                                tint = if (text.isNotBlank()) Color.Green else Color.Gray,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.7f),
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
                        .background(Color.Black.copy(alpha = 0.7f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .padding(bottom = 56.dp)
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Colors Section with dynamic sizing
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
                            // Vertical layout per schermi piccoli/portrait
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
                                    value = "%.0f°".format(rotation),
                                    onDecrease = { rotation = (rotation - 15f + 360f) % 360f },
                                    onIncrease = { rotation = (rotation + 15f) % 360f },
                                    onSliderChange = { rotation = it },
                                    sliderValue = rotation,
                                    sliderRange = 0f..360f,
                                    isCompact = true
                                )
                            }
                        } else {
                            // Horizontal layout per schermi grandi/landscape
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
                                    value = "%.0f°".format(rotation),
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
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                containerWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                containerHeight = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                
                // Box ristretto dove effettivamente vive il testo - con calcolo preciso delle dimensioni
                val restrictedBoxWidth = (containerWidth * 0.85f).coerceAtLeast(1f)
                val restrictedBoxHeight = restrictedBoxWidth  // 1:1 aspect ratio
                
                // Salva i valori per il calcolo negli offset
                savedContainerWidth.value = restrictedBoxWidth
                savedContainerHeight.value = restrictedBoxHeight

                if (!hasInitializedOffset && textConfig != null) {
                    offsetX = (textConfig.offsetX - 0.5f) * restrictedBoxWidth
                    offsetY = (textConfig.offsetY - 0.5f) * restrictedBoxHeight
                    hasInitializedOffset = true
                } else if (!hasInitializedOffset) {
                    offsetX = 0f
                    offsetY = 0f
                    hasInitializedOffset = true
                }

                // Immagine di sfondo - limitata a dimensione ragionevole
                imageUri?.let { uri ->
                    Box(
                        modifier = Modifier
                            .width((restrictedBoxWidth).dp)
                            .height((restrictedBoxHeight).dp)
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

                // Dimmer quando si modifica il testo
                if (isEditing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                    )
                }

                // Layer di interazione globale - Solo per trasform gestures (pinch/zoom/rotate)
                // Questo conosce le dimensioni piene (containerWidth/Height)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, transformRotation ->
                                if (!isEditing) {
                                    // Consenti pan/zoom/rotate libero da qualsiasi punto
                                    offsetX += pan.x
                                    offsetY += pan.y
                                    scale = (scale * zoom).coerceIn(0.5f, 5f)
                                    rotation += transformRotation
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            // Tap generico fuori dal testo
                            detectTapGestures(
                                onTap = { _ ->
                                    if (isEditing) {
                                        // Esci da editing al tap fuori
                                        isEditing = false
                                        focusManager.clearFocus()
                                    }
                                }
                            )
                        }
                )

                // Elemento testo - posizionato sopra l'immagine limitata (stesso container dell'immagine)
                Box(
                    modifier = Modifier
                        .width((restrictedBoxWidth).dp)
                        .height((restrictedBoxHeight).dp)
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    val textContainerModifier = Modifier
                        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            rotationZ = rotation
                        )
                        .pointerInput(Unit) {
                            // Drag sul testo - move/transform il testo
                            detectDragGestures(
                                onDragStart = { isDragging = true },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false },
                                onDrag = { change, dragAmount ->
                                    // Permetti drag sia in editing che in transform mode
                                    if (!isEditing) {
                                        change.consume()
                                        // Drag = sposta il testo in qualsiasi momento
                                        offsetX += dragAmount.x
                                        offsetY += dragAmount.y
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            // Tap sul testo - single/double tap detection
                            detectTapGestures(
                                onTap = { _ -> handleTextTap() },
                                onDoubleTap = { _ ->
                                    // Double tap = reset transforms
                                    scale = 1f
                                    rotation = 0f
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            )
                        }
                        .border(
                            width = if (!isEditing && text.isNotEmpty()) 2.dp else 0.dp,
                            color = Color.White.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(12.dp)

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
                                offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                                blurRadius = 4f
                            )
                        ),
                        cursorBrush = SolidColor(color),
                        modifier = textContainerModifier
                            .focusRequester(focusRequester)
                            .widthIn(min = 50.dp, max = 300.dp),
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

                    // Hint quando non in editing
                    if (!isEditing && text.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(2.dp, Color.Yellow.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
    }
}
