package com.kompact.ui

import android.graphics.PointF
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import com.kompact.R
import com.kompact.model.CropBounds
import com.kompact.model.DrawConfig
import com.kompact.model.DrawStroke
import com.kompact.model.DrawToolType
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Convert canvas coordinates to normalized image coordinates (0-1 range)
 */
private fun normalizeToImage(point: Offset, imageOffset: Offset, imageSize: androidx.compose.ui.geometry.Size): PointF {
    val x = (point.x - imageOffset.x) / imageSize.width
    val y = (point.y - imageOffset.y) / imageSize.height
    return PointF(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}

/**
 * Convert normalized image coordinates (0-1 range) back to canvas coordinates
 */
private fun denormalizeFromImage(point: PointF, imageOffset: Offset, imageSize: androidx.compose.ui.geometry.Size): PointF {
    val x = imageOffset.x + point.x * imageSize.width
    val y = imageOffset.y + point.y * imageSize.height
    return PointF(x, y)
}

/**
 * Geometria dell'immagine processata dalla pipeline (EXIF -> rotazione con
 * bounding box -> crop), su cui applyDraw disegna i tratti. Il Draw editor la
 * usa per mostrare esattamente la stessa immagine.
 *
 * Unità: altezza dell'immagine orientata = 1.
 */
private data class ProcessedImageGeometry(
    val aspect: Float, // aspect dell'immagine orientata (w/h)
    val theta: Float,  // rotazione in gradi
    val cw: Float,     // larghezza della regione di crop
    val ch: Float,     // altezza della regione di crop
    val qx: Float,     // centro regione rispetto al centro del bbox ruotato
    val qy: Float
)

private fun computeProcessedImageGeometry(
    orientedAspect: Float,
    rotationDegrees: Float,
    cropBounds: CropBounds?
): ProcessedImageGeometry? {
    val crop = cropBounds?.normalized()
    val theta = ((rotationDegrees % 360f) + 360f) % 360f
    val rad = theta * (kotlin.math.PI.toFloat() / 180f)
    val absSin = abs(sin(rad))
    val absCos = abs(cos(rad))
    val a = orientedAspect
    // Bounding box dopo la rotazione, come Bitmap.createBitmap con postRotate
    val rw = a * absCos + absSin
    val rh = a * absSin + absCos
    val left = crop?.left ?: 0f
    val top = crop?.top ?: 0f
    val right = crop?.right ?: 1f
    val bottom = crop?.bottom ?: 1f
    val cw = (right - left) * rw
    val ch = (bottom - top) * rh
    if (cw <= 0f || ch <= 0f) return null
    return ProcessedImageGeometry(
        aspect = a,
        theta = theta,
        cw = cw,
        ch = ch,
        qx = (left + right) / 2f * rw - rw / 2f,
        qy = (top + bottom) / 2f * rh - rh / 2f
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawEditorDialog(
    imageUri: Uri?,
    drawConfig: DrawConfig?,
    onDrawConfigChange: (DrawConfig?) -> Unit,
    onDismiss: () -> Unit,
    rotationDegrees: Float = 0f,
    cropBounds: CropBounds? = null
) {
    // Aspect dell'immagine come la mostra il loader (EXIF applicato)
    val orientedAspect = rememberOrientedImageAspectRatio(imageUri)
    // State for drawing
    var currentColor by remember { mutableStateOf(Color(drawConfig?.color ?: android.graphics.Color.RED)) }
    var currentStrokeWidth by remember { mutableStateOf(drawConfig?.strokeWidth ?: 5f) }
    var currentToolType by remember { mutableStateOf(drawConfig?.toolType ?: DrawToolType.PEN) }
    var currentOpacity by remember { mutableStateOf(drawConfig?.opacity ?: 1.0f) }
    
    // State for strokes
    var strokes by remember {
        mutableStateOf(drawConfig?.strokes?.toMutableStateList() ?: mutableStateListOf<DrawStroke>())
    }
    
    // Undo/Redo stack
    var undoStack by remember { mutableStateOf(mutableListOf<List<DrawStroke>>()) }
    var redoStack by remember { mutableStateOf(mutableListOf<List<DrawStroke>>()) }
    
    // Current path being drawn (SnapshotStateList: ogni punto aggiunto
    // invalida il Canvas, così il tratto appare in tempo reale)
    val currentPath = remember { mutableStateListOf<PointF>() }
    
    // Image and canvas state
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    var imageSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    var imageOffset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableStateOf(1f) }
    var translation by remember { mutableStateOf(Offset.Zero) }
    
    // Track if we're drawing
    var isDrawing by remember { mutableStateOf(false) }
    
    // Density for Dp to Px conversion
    val density = LocalDensity.current
    
    // Save current state for undo
    fun saveStateForUndo() {
        undoStack.add(strokes.toList())
        redoStack.clear()
    }
    
    // Handle undo
    fun handleUndo() {
        if (undoStack.isNotEmpty()) {
            val previousState = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(strokes.toList())
            strokes = previousState.toMutableStateList()
        }
    }
    
    // Handle redo
    fun handleRedo() {
        if (redoStack.isNotEmpty()) {
            val nextState = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(strokes.toList())
            strokes = nextState.toMutableStateList()
        }
    }
    
    // Handle clear all
    fun handleClearAll() {
        saveStateForUndo()
        strokes.clear()
    }

    // Commit the current path as a stroke with normalized coordinates
    fun commitStroke() {
        if (currentPath.isEmpty()) return
        // Registra lo spessore come frazione del lato maggiore
        // dell'immagine processata mostrata, così l'output
        // riproduce le stesse proporzioni a qualsiasi risoluzione
        val displayMaxEdge = maxOf(imageSize.width, imageSize.height).coerceAtLeast(1f)
        strokes.add(
            DrawStroke(
                points = currentPath.toList(),
                color = currentColor.toArgb(),
                strokeWidth = currentStrokeWidth,
                widthFraction = currentStrokeWidth / displayMaxEdge,
                toolType = currentToolType,
                opacity = currentOpacity
            )
        )
        currentPath.clear()
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
                            stringResource(R.string.draw),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        ) 
                    },
                    actions = {
                        // Reset: ripristina i default del draw editor — cancella
                        // tutti i tratti (con undo) e riporta colore, spessore,
                        // tool e opacità ai valori iniziali.
                        IconButton(
                            onClick = {
                                handleClearAll()
                                currentColor = Color(android.graphics.Color.RED)
                                currentStrokeWidth = 5f
                                currentToolType = DrawToolType.PEN
                                currentOpacity = 1.0f
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
                                // Save and close: onDrawConfigChange salva e chiude già
                                // l'editor (via onToggleDrawEditor nel wiring).
                                val newConfig = DrawConfig(
                                    strokes = strokes.toList(),
                                    color = currentColor.toArgb(),
                                    strokeWidth = currentStrokeWidth,
                                    toolType = currentToolType,
                                    opacity = currentOpacity
                                )
                                onDrawConfigChange(newConfig)
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
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .background(Color.Black.copy(alpha = 0.9f))
                        // heightIn PRIMA di verticalScroll: limita l'altezza del
                        // viewport (max 400dp), così il contenuto può essere più alto
                        // e scrollare. Se l'ordine è invertito, heightIn limita il
                        // contenuto a 400dp e i figli in eccesso vengono tagliati
                        // senza possibilità di scroll (scroll range = 0).
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Tool Type Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = currentToolType == DrawToolType.PEN,
                            onClick = { 
                                currentToolType = DrawToolType.PEN
                                currentOpacity = 1.0f
                            },
                            label = { Text("Pen", color = Color.White) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color.Blue.copy(alpha = 0.5f),
                                containerColor = Color.Gray.copy(alpha = 0.3f)
                            )
                        )
                        FilterChip(
                            selected = currentToolType == DrawToolType.HIGHLIGHTER,
                            onClick = { 
                                currentToolType = DrawToolType.HIGHLIGHTER
                                currentOpacity = 0.5f
                            },
                            label = { Text("Highlighter", color = Color.White) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color.Yellow.copy(alpha = 0.5f),
                                containerColor = Color.Gray.copy(alpha = 0.3f)
                            )
                        )
                    }

                    // Colors Section
                    Text(
                        "Colors",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                    )
                    
                    // Pen colors (opaque)
                    if (currentToolType == DrawToolType.PEN) {
                        ColorPickerRow(
                            colors = listOf(
                                Color.Red, Color.Black, Color.White, Color.Blue, 
                                Color.Green, Color.Yellow, Color.Cyan, Color.Magenta
                            ),
                            selectedColor = currentColor,
                            onColorSelected = { color -> currentColor = color }
                        )
                    }
                    
                    // Highlighter colors (semi-transparent)
                    if (currentToolType == DrawToolType.HIGHLIGHTER) {
                        ColorPickerRow(
                            colors = listOf(
                                Color.Yellow.copy(alpha = 0.5f),
                                Color.Green.copy(alpha = 0.5f),
                                Color.Blue.copy(alpha = 0.5f),
                                Color.Red.copy(alpha = 0.5f),
                                Color.Cyan.copy(alpha = 0.5f),
                                Color.Magenta.copy(alpha = 0.5f),
                                Color.White.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.5f)
                            ),
                            selectedColor = currentColor,
                            onColorSelected = { color -> 
                                currentColor = color
                                currentOpacity = color.alpha
                            }
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 0.5.dp)

                    // Stroke Width Section
                    Text(
                        "Stroke Width",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { 
                                currentStrokeWidth = (currentStrokeWidth - 1f).coerceAtLeast(1f) 
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White)
                        }
                        Text(
                            "%.0f px".format(currentStrokeWidth),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(60.dp),
                            textAlign = TextAlign.Center
                        )
                        Slider(
                            value = currentStrokeWidth,
                            onValueChange = { currentStrokeWidth = it },
                            valueRange = 1f..50f,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { 
                                currentStrokeWidth = (currentStrokeWidth + 1f).coerceAtMost(50f) 
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White)
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 0.5.dp)

                    // Action Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { handleUndo() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White,
                                containerColor = Color.Transparent
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Default.Undo, contentDescription = "Undo")
                            Spacer(Modifier.width(4.dp))
                            Text("Undo")
                        }
                        OutlinedButton(
                            onClick = { handleRedo() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White,
                                containerColor = Color.Transparent
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Default.Redo, contentDescription = "Redo")
                            Spacer(Modifier.width(4.dp))
                            Text("Redo")
                        }
                        OutlinedButton(
                            onClick = { handleClearAll() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.Red,
                                containerColor = Color.Transparent
                            )
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                            Spacer(Modifier.width(4.dp))
                            Text("Clear All")
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
                val maxWidth = constraints.maxWidth.toFloat()
                val maxHeight = constraints.maxHeight.toFloat()
                
                // Center of the canvas
                val centerX = maxWidth / 2f
                val centerY = maxHeight / 2f
                
                canvasSize = androidx.compose.ui.geometry.Size(maxWidth, maxHeight)
                
                // Image display area (85% of canvas)
                val imageWidth = maxWidth * 0.85f
                val imageHeight = maxHeight * 0.85f

                // Immagine processata (EXIF -> rotazione -> crop): i tratti vengono
                // normalizzati su questa immagine, come applyDraw nella pipeline
                val geometry = orientedAspect?.let {
                    computeProcessedImageGeometry(it, rotationDegrees, cropBounds)
                }

                // Fit dell'immagine processata nell'area di display
                val displaySize = if (geometry != null) {
                    val boxAspect = imageWidth / imageHeight
                    val processedAspect = geometry.cw / geometry.ch
                    if (processedAspect >= boxAspect) {
                        androidx.compose.ui.geometry.Size(imageWidth, imageWidth / processedAspect)
                    } else {
                        androidx.compose.ui.geometry.Size(imageHeight * processedAspect, imageHeight)
                    }
                } else {
                    androidx.compose.ui.geometry.Size(imageWidth, imageHeight)
                }
                imageSize = displaySize
                imageOffset = Offset(centerX - displaySize.width / 2f, centerY - displaySize.height / 2f)

                // Zoom/pan della vista (pinch a due dita): immagine e tratti si
                // trasformano insieme; i pointer restano in coordinate locali,
                // quindi la normalizzazione dei tratti non cambia
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = translation.x,
                            translationY = translation.y
                        )
                ) {
                    // Draw image
                    imageUri?.let { uri ->
                        Box(
                            modifier = Modifier
                                .width(with(density) { displaySize.width.toDp() })
                                .height(with(density) { displaySize.height.toDp() })
                                .align(Alignment.Center)
                                .clipToBounds()
                        ) {
                            if (geometry == null) {
                                // Fallback finché l'aspect non è noto
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                // La regione di crop riempie il box di display; l'immagine
                                // orientata viene ruotata con lo stesso bounding box della pipeline
                                val k = displaySize.width / geometry.cw
                                Image(
                                    painter = rememberAsyncImagePainter(uri),
                                    contentDescription = null,
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier
                                        .size(
                                            with(density) { (k * geometry.aspect).toDp() },
                                            with(density) { k.toDp() }
                                        )
                                        .align(Alignment.Center)
                                        .graphicsLayer(
                                            rotationZ = geometry.theta,
                                            translationX = -k * geometry.qx,
                                            translationY = -k * geometry.qy
                                        )
                                )
                            }
                        }
                    }

                    // Drawing canvas: un dito disegna, due dita zoom/pan della vista
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("draw_canvas")
                            .pointerInput(Unit) {
                                val touchSlopPx = 8.dp.toPx()
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var drawing = false
                                    var pastSlop = false
                                    var multiTouch = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pressedChanges = event.changes.filter { it.pressed }
                                        if (pressedChanges.isEmpty()) break

                                        if (pressedChanges.size > 1) {
                                            // Pinch: interrompi l'eventuale tratto in corso
                                            if (drawing) {
                                                drawing = false
                                                isDrawing = false
                                                currentPath.clear()
                                            }
                                            multiTouch = true
                                            val zoomChange = event.calculateZoom()
                                            val panChange = event.calculatePan()
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                            scale = (scale * zoomChange).coerceIn(1f, 5f)
                                            // panChange è in coordinate locali: riscala in px schermo
                                            val maxPanX = (scale - 1f) * canvasSize.width / 2f
                                            val maxPanY = (scale - 1f) * canvasSize.height / 2f
                                            translation = Offset(
                                                (translation.x + panChange.x * scale).coerceIn(-maxPanX, maxPanX),
                                                (translation.y + panChange.y * scale).coerceIn(-maxPanY, maxPanY)
                                            )
                                        } else if (!multiTouch) {
                                            // Disegno a un dito, con touch slop
                                            val change = pressedChanges.first()
                                            if (!pastSlop &&
                                                (change.position - down.position).getDistance() > touchSlopPx
                                            ) {
                                                pastSlop = true
                                            }
                                            if (pastSlop && change.positionChanged()) {
                                                if (!drawing) {
                                                    drawing = true
                                                    isDrawing = true
                                                    saveStateForUndo()
                                                    currentPath.clear()
                                                    currentPath.add(
                                                        normalizeToImage(down.position, imageOffset, imageSize)
                                                    )
                                                }
                                                change.consume()
                                                currentPath.add(
                                                    normalizeToImage(change.position, imageOffset, imageSize)
                                                )
                                            }
                                        }
                                    }
                                    if (drawing) {
                                        isDrawing = false
                                        commitStroke()
                                    }
                                }
                            }
                    ) {
                        // Lato maggiore dell'immagine processata mostrata (letto dallo state,
                        // così il rendering segue i cambi di layout)
                        val displayMaxEdge = maxOf(imageSize.width, imageSize.height).coerceAtLeast(1f)

                        // Draw existing strokes
                        strokes.forEach { stroke ->
                            val strokeColor = Color(stroke.color)
                            val strokeWidth = if (stroke.widthFraction > 0f) {
                                stroke.widthFraction * displayMaxEdge
                            } else {
                                stroke.strokeWidth
                            }

                            // Convert normalized coordinates back to canvas space for display
                            drawPath(
                                path = asPath(stroke.points.map { denormalizeFromImage(it, imageOffset, imageSize) }),
                                color = strokeColor.copy(alpha = stroke.opacity),
                                style = Stroke(width = strokeWidth)
                            )
                        }

                        // Draw current path
                        if (isDrawing && currentPath.isNotEmpty()) {
                            // Convert normalized coordinates back to canvas space for display
                            drawPath(
                                path = asPath(currentPath.map { denormalizeFromImage(it, imageOffset, imageSize) }),
                                color = currentColor.copy(alpha = currentOpacity),
                                style = Stroke(width = currentStrokeWidth)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPickerRow(
    colors: List<Color>,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(colors) { color ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (color == selectedColor) {
                            Color.White
                        } else {
                            Color.Transparent
                        },
                        CircleShape
                    )
                    .border(
                        width = if (color == selectedColor) 2.dp else 0.dp,
                        color = Color.White,
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(color) }
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(color, CircleShape)
                        .align(Alignment.Center)
                )
            }
        }
    }
}

/**
 * Convert a list of PointF to a Compose Path
 */
private fun asPath(points: List<PointF>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        path.lineTo(points[i].x, points[i].y)
    }
    return path
}
