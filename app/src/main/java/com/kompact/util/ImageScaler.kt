package com.kompact.util

import kotlin.math.max

object ImageScaler {
    fun calculateScaleFactor(
        width: Int,
        height: Int,
        maxWidth: Int,
        maxHeight: Int,
    ): Float {
        if (width <= 0 || height <= 0) return 1f
        val widthRatio = width.toFloat() / maxWidth
        val heightRatio = height.toFloat() / maxHeight
        val largestRatio = max(widthRatio, heightRatio)
        return if (largestRatio <= 1f) 1f else largestRatio
    }
}
