package com.kompact

import com.kompact.util.ImageScaler
import org.junit.Assert.assertEquals
import org.junit.Test

class CompressionConfigTest {

    @Test
    fun scaleFactorReturnsOneWhenWithinBounds() {
        val factor = ImageScaler.calculateScaleFactor(1000, 500, 1920, 1080)
        assertEquals(1f, factor, 0.0001f)
    }

    @Test
    fun scaleFactorPicksLargestRatio() {
        val factor = ImageScaler.calculateScaleFactor(4000, 2000, 1920, 1080)
        assertEquals(4000f / 1920f, factor, 0.0001f)
    }
}
