package com.kompact.model

data class TextOverlayConfig(
    val text: String = "",
    val color: Int = android.graphics.Color.WHITE,
    val size: Float = 50f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    // Font height relative to the editor's square canvas (px/px). 0 = unknown, use legacy sizing.
    val sizeFraction: Float = 0f
)
