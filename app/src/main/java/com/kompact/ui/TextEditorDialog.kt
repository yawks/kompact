package com.kompact.ui

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.exifinterface.media.ExifInterface
import coil3.compose.rememberAsyncImagePainter
import com.kompact.R
import com.kompact.model.CropBounds
import com.kompact.model.TextOverlayConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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

private data class EditableText(
    val id: Int,
    val config: TextOverlayConfig,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorDialog(
    imageUri: Uri?,
    texts: List<TextOverlayConfig>,
    onTextsChange: (List<TextOverlayConfig>) -> Unit,
    onDismiss: () -> Unit,
    rotationDegrees: Float = 0f,
    cropBounds: CropBounds? = null
) {
    // Un elemento per ogni casella di testo sul canvas; l'id rende ogni
    // testo autonomo e identificabile per selezione, editing ed eliminazione.
    var items by remember(texts) { mutableStateOf(texts.mapIndexed { index, config -> EditableText(index, config) }) }
    var nextId by remember(texts) { mutableStateOf(texts.size) }
    var selectedId by remember { mutableStateOf<Int?>(null) }

    // Tastiera aperta per la casella selezionata
    var isEditing by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    // Dimensione del container
    var containerWidth by remember { mutableStateOf(1f) }
    var containerHeight by remember { mutableStateOf(1f) }

    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current

    val selectedItem = items.firstOrNull { it.id == selectedId }

    fun updateItem(id: Int, transform: (TextOverlayConfig) -> TextOverlayConfig) {
        items = items.map { if (it.id == id) it.copy(config = transform(it.config)) else it }
    }

    fun updateSelected(transform: (TextOverlayConfig) -> TextOverlayConfig) {
        val id = selectedId ?: return
        updateItem(id, transform)
    }

    fun addText() {
        val id = nextId
        nextId += 1
        items = items + EditableText(id, TextOverlayConfig())
        selectedId = id
        isEditing = true
    }

    fun deleteSelected() {
        val id = selectedId ?: return
        items = items.filterNot { it.id == id }
        selectedId = null
        isEditing = false
        focusManager.clearFocus()
    }

    // Prima apertura senza testi esistenti: crea subito il primo, come nel
    // vecchio editor a testo singolo. Riaprendo con testi salvati seleziona
    // il primo così i controlli agiscono subito su un bersaglio.
    LaunchedEffect(Unit) {
        if (items.isEmpty()) {
            addText()
        } else if (selectedId == null) {
            selectedId = items.first().id
        }
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
                        // Aggiunge una nuova casella di testo indipendente
                        IconButton(onClick = { addText() }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Text", tint = Color.White)
                        }
                        // Elimina la casella selezionata
                        if (selectedItem != null) {
                            IconButton(onClick = { deleteSelected() }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Text", tint = Color.White)
                            }
                        }
                        IconButton(
                            onClick = {
                                // Record the font height relative to the editor's square
                                // canvas so the output overlay can reproduce the same
                                // proportions on any image resolution.
                                val canvasPx = containerWidth.coerceAtMost(containerHeight) * 0.85f
                                val saved = items.map { item ->
                                    val fontPx = with(density) { item.config.size.sp.toPx() }
                                    item.config.copy(
                                        text = item.config.text.trim(),
                                        sizeFraction = if (canvasPx > 0f) fontPx / canvasPx else 0f
                                    )
                                }.filter { it.text.isNotBlank() }
                                isEditing = false
                                focusManager.clearFocus()
                                // onTextsChange salva e chiude già l'editor (via onToggleTextEditor
                                // nel wiring): chiamare anche onDismiss() farebbe un doppio toggle
                                // e il dialog resterebbe aperto.
                                onTextsChange(saved)
                            }
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Save Text",
                                tint = if (items.any { it.config.text.isNotBlank() }) Color.Green else Color.Gray
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
                            // heightIn prima di verticalScroll: limita il viewport,
                            // non il contenuto — così su display piccoli i controlli
                            // restano accessibili scrollando.
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (selectedItem == null) {
                            Text(
                                stringResource(R.string.tap_text_to_edit),
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            val selectedConfig = selectedItem.config

                            // Colors Section - colore della casella selezionata
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
                                                    if (Color(selectedConfig.color) == c) 2.dp else 0.dp,
                                                    Color.White,
                                                    CircleShape
                                                )
                                                .clickable { updateSelected { it.copy(color = c.toArgb()) } }
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 0.5.dp)

                            // Control Section - Responsive layout, tutti i controlli
                            // agiscono sulla casella selezionata
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
                                        value = "%.0f".format(selectedConfig.size),
                                        onDecrease = { updateSelected { it.copy(size = (it.size - 5f).coerceIn(20f, 120f)) } },
                                        onIncrease = { updateSelected { it.copy(size = (it.size + 5f).coerceIn(20f, 120f)) } },
                                        onSliderChange = { newSize -> updateSelected { it.copy(size = newSize) } },
                                        sliderValue = selectedConfig.size,
                                        sliderRange = 20f..120f,
                                        isCompact = true
                                    )
                                    ControlRow(
                                        label = "Scale",
                                        value = "%.1f".format(selectedConfig.scale),
                                        onDecrease = { updateSelected { it.copy(scale = (it.scale - 0.1f).coerceIn(0.5f, 5f)) } },
                                        onIncrease = { updateSelected { it.copy(scale = (it.scale + 0.1f).coerceIn(0.5f, 5f)) } },
                                        onSliderChange = { newScale -> updateSelected { it.copy(scale = newScale) } },
                                        sliderValue = selectedConfig.scale,
                                        sliderRange = 0.5f..5f,
                                        isCompact = true
                                    )
                                    ControlRow(
                                        label = "Rotate",
                                        value = "%.0f\u00B0".format(selectedConfig.rotation),
                                        onDecrease = { updateSelected { it.copy(rotation = (it.rotation - 15f + 360f) % 360f) } },
                                        onIncrease = { updateSelected { it.copy(rotation = (it.rotation + 15f) % 360f) } },
                                        onSliderChange = { newRotation -> updateSelected { it.copy(rotation = newRotation) } },
                                        sliderValue = selectedConfig.rotation,
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
                                        value = "%.0f".format(selectedConfig.size),
                                        onDecrease = { updateSelected { it.copy(size = (it.size - 5f).coerceIn(20f, 120f)) } },
                                        onIncrease = { updateSelected { it.copy(size = (it.size + 5f).coerceIn(20f, 120f)) } },
                                        onSliderChange = { newSize -> updateSelected { it.copy(size = newSize) } },
                                        sliderValue = selectedConfig.size,
                                        sliderRange = 20f..120f,
                                        isCompact = false,
                                        modifier = Modifier.weight(1f)
                                    )
                                    ControlRow(
                                        label = "Scale",
                                        value = "%.1f".format(selectedConfig.scale),
                                        onDecrease = { updateSelected { it.copy(scale = (it.scale - 0.1f).coerceIn(0.5f, 5f)) } },
                                        onIncrease = { updateSelected { it.copy(scale = (it.scale + 0.1f).coerceIn(0.5f, 5f)) } },
                                        onSliderChange = { newScale -> updateSelected { it.copy(scale = newScale) } },
                                        sliderValue = selectedConfig.scale,
                                        sliderRange = 0.5f..5f,
                                        isCompact = false,
                                        modifier = Modifier.weight(1f)
                                    )
                                    ControlRow(
                                        label = "Rotate",
                                        value = "%.0f\u00B0".format(selectedConfig.rotation),
                                        onDecrease = { updateSelected { it.copy(rotation = (it.rotation - 15f + 360f) % 360f) } },
                                        onIncrease = { updateSelected { it.copy(rotation = (it.rotation + 15f) % 360f) } },
                                        onSliderChange = { newRotation -> updateSelected { it.copy(rotation = newRotation) } },
                                        sliderValue = selectedConfig.rotation,
                                        sliderRange = 0f..360f,
                                        isCompact = false,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
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
                // constraints sono in pixel: convertili in Dp per il layout, mentre le
                // gesture e graphicsLayer usano direttamente i pixel.
                val maxImageSize = containerWidth.coerceAtMost(containerHeight) * 0.85f
                val maxImageSizeDp = with(LocalDensity.current) { maxImageSize.toDp() }

                // Immagine di sfondo: riproduce la catena della pipeline
                // (orientamento EXIF -> rotazione -> crop) così il posizionamento
                // del testo e la sua dimensione corrispondono all'immagine di output.
                imageUri?.let { uri ->
                    val painter = rememberAsyncImagePainter(uri)
                    val imageAspect = rememberOrientedImageAspectRatio(uri)
                    val crop = cropBounds?.normalized()
                    val theta = ((rotationDegrees % 360f) + 360f) % 360f
                    val rad = theta * (kotlin.math.PI.toFloat() / 180f)
                    val absSin = abs(sin(rad))
                    val absCos = abs(cos(rad))
                    Box(
                        modifier = Modifier
                            .width(maxImageSizeDp)
                            .height(maxImageSizeDp)
                            .align(Alignment.Center)
                            .background(Color.Black)
                    ) {
                        if (imageAspect == null || (theta == 0f && crop == null)) {
                            // Nessuna trasformazione: semplice Fit, identico all'output
                            Image(
                                painter = painter,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            // Dimensioni in unità relative (altezza immagine = 1)
                            val w = imageAspect
                            val h = 1f
                            // Bounding box dopo la rotazione, come Bitmap.createBitmap con postRotate
                            val rw = w * absCos + h * absSin
                            val rh = w * absSin + h * absCos
                            val left = crop?.left ?: 0f
                            val top = crop?.top ?: 0f
                            val right = crop?.right ?: 1f
                            val bottom = crop?.bottom ?: 1f
                            val cw = (right - left) * rw
                            val ch = (bottom - top) * rh
                            if (cw <= 0f || ch <= 0f) {
                                Image(
                                    painter = painter,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                // Scala per far entrare la regione di crop nel quadrato (Fit)
                                val k = maxImageSize / maxOf(cw, ch)
                                // Centro della regione di crop rispetto al centro del bounding box ruotato
                                val qx = (left + right) / 2f * rw - rw / 2f
                                val qy = (top + bottom) / 2f * rh - rh / 2f
                                Box(
                                    modifier = Modifier
                                        .size(
                                            with(density) { (k * cw).toDp() },
                                            with(density) { (k * ch).toDp() }
                                        )
                                        .align(Alignment.Center)
                                        .clipToBounds()
                                ) {
                                    Image(
                                        painter = painter,
                                        contentDescription = null,
                                        contentScale = ContentScale.FillBounds,
                                        modifier = Modifier
                                            .size(
                                                with(density) { (k * w).toDp() },
                                                with(density) { (k * h).toDp() }
                                            )
                                            .graphicsLayer(
                                                rotationZ = theta,
                                                translationX = -k * qx,
                                                translationY = -k * qy
                                            )
                                    )
                                }
                            }
                        }
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

                // Layer per gesture di trasformazione (pinch/zoom/rotate/drag):
                // agiscono sulla casella selezionata
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("text_canvas")
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, transformRotation ->
                                if (!isEditing && !isDragging) {
                                    updateSelected { c ->
                                        c.copy(
                                            // Pan: sposta il testo (converte in offset normalizzato)
                                            offsetX = (c.offsetX + pan.x / maxImageSize).coerceIn(0f, 1f),
                                            offsetY = (c.offsetY + pan.y / maxImageSize).coerceIn(0f, 1f),
                                            // Zoom: scala il testo
                                            scale = (c.scale * zoom).coerceIn(0.5f, 5f),
                                            // Rotazione
                                            rotation = c.rotation + transformRotation
                                        )
                                    }
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            // Tap sul canvas fuori dalle caselle: chiude la tastiera
                            // o deseleziona la casella corrente
                            detectTapGestures {
                                if (isEditing) {
                                    isEditing = false
                                    focusManager.clearFocus()
                                } else {
                                    selectedId = null
                                }
                            }
                        }
                ) {
                    // Zona quadrata con le caselle di testo, posizionate al centro
                    // e spostate tramite offset normalizzato [0, 1]
                    Box(
                        modifier = Modifier
                            .width(maxImageSizeDp)
                            .height(maxImageSizeDp)
                            .align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        items.forEach { item ->
                            key(item.id) {
                                TextItemView(
                                    item = item,
                                    isSelected = item.id == selectedId,
                                    isEditingThis = isEditing && item.id == selectedId,
                                    maxImageSize = maxImageSize,
                                    maxImageSizeDp = maxImageSizeDp,
                                    onTextChange = { newText ->
                                        updateItem(item.id) { it.copy(text = newText) }
                                    },
                                    onTap = {
                                        when {
                                            // Tap sulla casella mentre si scrive: chiude la
                                            // tastiera (toggle off). L'edit resta applicato:
                                            // il testo è già in items e viene salvato con Ok.
                                            isEditing && selectedId == item.id -> {
                                                isEditing = false
                                                focusManager.clearFocus()
                                            }
                                            // Tap su una casella (selezionata o meno): la
                                            // seleziona e apre subito la tastiera, così non
                                            // serve un doppio tap per riaprire l'edit.
                                            else -> {
                                                selectedId = item.id
                                                isEditing = true
                                            }
                                        }
                                    },
                                    onDragStart = {
                                        selectedId = item.id
                                        isDragging = true
                                    },
                                    onDragEnd = { isDragging = false },
                                    onDragBy = { dx, dy ->
                                        updateItem(item.id) {
                                            it.copy(
                                                offsetX = (it.offsetX + dx).coerceIn(0f, 1f),
                                                offsetY = (it.offsetY + dy).coerceIn(0f, 1f)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                        if (items.isEmpty()) {
                            Text(
                                stringResource(R.string.tap_text_to_edit),
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Una singola casella di testo sul canvas. Gestisce la propria posizione
 * (offset normalizzato), scala, rotazione e colore; il contenuto è editabile
 * solo quando la casella è selezionata e la tastiera è aperta (readOnly).
 */
@Composable
private fun TextItemView(
    item: EditableText,
    isSelected: Boolean,
    isEditingThis: Boolean,
    maxImageSize: Float,
    maxImageSizeDp: Dp,
    onTextChange: (String) -> Unit,
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragBy: (Float, Float) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val config = item.config
    val color = Color(config.color)
    val textStyle = TextStyle(
        color = color,
        fontSize = config.size.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        shadow = androidx.compose.ui.graphics.Shadow(
            color = Color.Black.copy(alpha = 0.7f),
            offset = Offset(2f, 2f),
            blurRadius = 4f
        )
    )
    // Placeholder (casella vuota): stesso stile ma colore attenuato e senza ombra.
    val placeholderStyle = TextStyle(
        color = color.copy(alpha = 0.4f),
        fontSize = config.size.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    LaunchedEffect(isEditingThis) {
        if (isEditingThis) focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .graphicsLayer(
                translationX = (config.offsetX - 0.5f) * maxImageSize,
                translationY = (config.offsetY - 0.5f) * maxImageSize,
                scaleX = config.scale,
                scaleY = config.scale,
                rotationZ = config.rotation
            )
            .pointerInput(item.id) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragBy(dragAmount.x / maxImageSize, dragAmount.y / maxImageSize)
                    }
                )
            }
            .pointerInput(item.id) {
                // Solo onTap, niente onDoubleTap: il doppio tap ritarderebbe
                // onTap di ~300ms (doubleTapTimeout) e il tap sulla casella deve
                // entrare subito in edit mode.
                detectTapGestures(onTap = { onTap() })
            }
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                shape = RoundedCornerShape(4.dp)
            )
    ) {
        // Fuori dall'edit mode la casella mostra un Text semplice (non
        // focusable): un BasicTextField readOnly intercetterebbe il tap con il
        // proprio tap-to-focus, mandando il campo a fuoco ma lasciando la
        // casella in readOnly (niente tastiera, niente edit). In edit mode torna
        // il BasicTextField editabile; key(isEditingThis) lo ricrea così il
        // focus riparte pulito ad ogni apertura.
        key(isEditingThis) {
            if (isEditingThis) {
                BasicTextField(
                    value = config.text,
                    onValueChange = onTextChange,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(color),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .widthIn(min = 50.dp, max = maxImageSizeDp * 0.9f),
                    singleLine = false,
                    decorationBox = { innerTextField ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(4.dp)
                        ) {
                            if (config.text.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.enter_text),
                                    style = placeholderStyle
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            } else {
                Text(
                    text = if (config.text.isEmpty()) stringResource(R.string.enter_text) else config.text,
                    style = if (config.text.isEmpty()) placeholderStyle else textStyle,
                    modifier = Modifier
                        .widthIn(min = 50.dp, max = maxImageSizeDp * 0.9f)
                        .padding(4.dp)
                )
            }
        }
    }
}

/**
 * Aspect ratio (width/height) of the image as it is displayed by image loaders
 * (Coil applies the EXIF orientation tag). Matches the coordinate space used by
 * the compression pipeline, which normalizes the orientation at decode time.
 */
@Composable
internal fun rememberOrientedImageAspectRatio(imageUri: Uri?): Float? {
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
                            val raw = options.outWidth.toFloat() / options.outHeight.toFloat()
                            val orientation = context.contentResolver.openInputStream(uri)?.use { s ->
                                ExifInterface(s).getAttributeInt(
                                    ExifInterface.TAG_ORIENTATION,
                                    ExifInterface.ORIENTATION_NORMAL
                                )
                            } ?: ExifInterface.ORIENTATION_NORMAL
                            val swapped = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                                orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                                orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                                orientation == ExifInterface.ORIENTATION_TRANSVERSE
                            if (swapped) 1f / raw else raw
                        } else null
                    }
                }.getOrNull()
            }
        }
    }
    return aspectRatio
}
