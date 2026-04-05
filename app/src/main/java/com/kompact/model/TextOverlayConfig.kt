package com.kompact.model

data class TextOverlayConfig(
    val text: String = "",
    val color: ULong = 0xFFFFFFFFUL, // white by default
    val size: Float = 50f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f
)
