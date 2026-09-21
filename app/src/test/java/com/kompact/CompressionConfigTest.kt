package com.kompact

import com.kompact.model.AdjustConfig
import com.kompact.model.CropBounds
import com.kompact.model.DrawConfig
import com.kompact.model.ImageCompressionConfig
import com.kompact.model.ImageOutputFormat
import com.kompact.model.ResizeConfig
import com.kompact.model.withoutVisualEdits
import com.kompact.model.hasVisualEdits
import com.kompact.util.ImageScaler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun withoutVisualEditsClearsAllPerImageEffects() {
        val config = ImageCompressionConfig(
            cropBounds = CropBounds(0.1f, 0.1f, 0.9f, 0.9f),
            resizeConfig = ResizeConfig(width = 1920, height = 1080),
            rotationDegrees = 90f,
            drawConfig = DrawConfig(),
            adjustConfig = AdjustConfig(brightness = 25f)
        )

        val reset = config.withoutVisualEdits()

        assertNull(reset.cropBounds)
        assertNull(reset.textConfig)
        assertNull(reset.resizeConfig)
        assertNull(reset.backgroundConfig)
        assertNull(reset.colorFilterConfig)
        assertNull(reset.drawConfig)
        assertNull(reset.adjustConfig)
        assertEquals(0f, reset.rotationDegrees, 0.0001f)
        assertFalse(reset.hasVisualEdits())
    }

    @Test
    fun withoutVisualEditsKeepsCompressionSettings() {
        val config = ImageCompressionConfig(
            format = ImageOutputFormat.WEBP,
            percentage = 42,
            stripExif = true,
            preserveSourceDate = true,
            outputFileName = "out.jpg"
        )

        val reset = config.withoutVisualEdits()

        assertEquals(ImageOutputFormat.WEBP, reset.format)
        assertEquals(42, reset.percentage)
        assertTrue(reset.stripExif)
        assertTrue(reset.preserveSourceDate)
        assertEquals("out.jpg", reset.outputFileName)
    }
}
