package com.kompact.ui
import com.kompact.R
import androidx.compose.ui.res.stringResource

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.kompact.model.TextOverlayConfig

@Composable
fun TextEditorDialog(
    imageUri: Uri?,
    textConfig: TextOverlayConfig?,
    onTextConfigChange: (TextOverlayConfig?) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(textConfig?.text ?: "") }
    var color by remember { mutableStateOf(Color(textConfig?.color ?: android.graphics.Color.WHITE)) }
    var scale by remember { mutableStateOf(textConfig?.scale ?: 1f) }
    var offset by remember { mutableStateOf(Offset(textConfig?.offsetX ?: 0.5f, textConfig?.offsetY ?: 0.5f)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.add_text)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            val newConfig = if (text.isBlank()) null else TextOverlayConfig(
                                text = text,
                                color = color.toArgb(),
                                scale = scale,
                                offsetX = offset.x,
                                offsetY = offset.y
                            )
                            onTextConfigChange(newConfig)
                            onDismiss()
                        }) {
                            Text(stringResource(R.string.save))
                        }
                    }
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                    imageUri?.let { uri ->
                        Image(
                            painter = rememberAsyncImagePainter(uri),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    // A wrapper for the text that can be dragged and scaled
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val newX = (offset.x + dragAmount.x / size.width).coerceIn(0f, 1f)
                                    val newY = (offset.y + dragAmount.y / size.height).coerceIn(0f, 1f)
                                    offset = Offset(newX, newY)
                                }
                            }
                    ) {
                        Text(
                            text = text.ifEmpty { "Enter Text" },
                            color = color,
                            fontSize = (50 * scale).sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(
                                    x = (offset.x * 300).dp, // approx mapping for preview
                                    y = (offset.y * 500).dp
                                )
                                .transformable(rememberTransformableState { zoomChange, _, _ ->
                                    scale = (scale * zoomChange).coerceIn(0.5f, 5f)
                                })
                        )
                    }
                }

                // Controls
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(stringResource(R.string.text_content)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Scale: ${"%.1f".format(scale)}")
                    Slider(
                        value = scale,
                        onValueChange = { scale = it },
                        valueRange = 0.5f..5f
                    )
                    // Quick colors
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(Color.White, Color.Black, Color.Red, Color.Green, Color.Blue, Color.Yellow).forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(c, RoundedCornerShape(20.dp))
                                    .clickable { color = c }
                            )
                        }
                    }
                }
            }
        }
    }
}

// Temporary shim for TopAppBar if Material3 imports are tricky
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    androidx.compose.material3.TopAppBar(
        title = title,
        navigationIcon = navigationIcon,
        actions = actions
    )
}
