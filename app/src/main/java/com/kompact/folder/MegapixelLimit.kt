package com.kompact.folder

import kotlin.math.floor
import kotlin.math.sqrt

object MegapixelLimit {
    fun dimensions(width: Int, height: Int, megapixels: Double): Pair<Int, Int> {
        require(width > 0 && height > 0)
        require(megapixels.isFinite() && megapixels in 0.1..200.0)
        val pixels = width.toLong() * height
        val budget = floor(megapixels * 1_000_000).toLong()
        if (pixels <= budget) return width to height
        val scale = sqrt(budget.toDouble() / pixels)
        // Floor, rather than rounding, keeps even extreme panoramas below the limit.
        return maxOf(1, floor(width * scale).toInt()).coerceAtMost(budget.toInt()) to
            maxOf(1, floor(height * scale).toInt()).coerceAtMost(budget.toInt())
    }
}
