package com.kompact.folder

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class MegapixelLimitTest {
    @Test fun smallerAndExactLimitImagesAreUnchanged() {
        assertEquals(2000 to 1000, MegapixelLimit.dimensions(2000, 1000, 5.0))
        assertEquals(2500 to 2000, MegapixelLimit.dimensions(2500, 2000, 5.0))
    }

    @Test fun landscapePortraitAndPanoramaRespectTheBudget() {
        val random = Random(42)
        repeat(10_000) {
            val width = random.nextInt(1, 100_000)
            val height = random.nextInt(1, 100_000)
            val limit = random.nextDouble(0.1, 200.0)
            val (w, h) = MegapixelLimit.dimensions(width, height, limit)
            assertTrue(w in 1..width && h in 1..height)
            assertTrue("$w × $h exceeds $limit MP", w.toLong() * h <= (limit * 1_000_000).toLong())
            if (w > 1 && h > 1) {
                assertEquals(width.toDouble() / height, w.toDouble() / h, (1.0 + width.toDouble() / height) / h)
            }
        }
    }

    @Test fun extremeDimensionsDoNotOverflowOrExceedLimit() {
        for ((width, height) in listOf(Int.MAX_VALUE to Int.MAX_VALUE, 1 to Int.MAX_VALUE, Int.MAX_VALUE to 1)) {
            val (w, h) = MegapixelLimit.dimensions(width, height, 5.0)
            assertTrue(w.toLong() * h <= 5_000_000)
        }
    }

    @Test fun invalidInputIsRejected() {
        for (limit in listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0, 201.0)) {
            assertThrows(IllegalArgumentException::class.java) { MegapixelLimit.dimensions(4000, 3000, limit) }
        }
    }
}
