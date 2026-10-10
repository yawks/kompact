package com.kompact

import com.kompact.util.gifEncode
import com.kompact.util.quantizeToPalette
import com.kompact.util.tiffEncode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.zip.Inflater

class GifTiffEncoderTest {

    // ---------- GIF ----------

    @Test
    fun gifHasHeaderAndTrailer() {
        val pixels = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFFFF.toInt())
        val q = quantizeToPalette(pixels)
        val gif = gifEncode(q.indices, 2, 2, q.palette, q.transparentIndex)
        assertEquals("GIF89a", String(gif, 0, 6, Charsets.US_ASCII))
        assertEquals(0x3B.toByte(), gif[gif.size - 1])
    }

    @Test
    fun gifLogicalScreenDescriptorMatchesSize() {
        val pixels = intArrayOf(0xFF112233.toInt(), 0xFF445566.toInt())
        val q = quantizeToPalette(pixels)
        val gif = gifEncode(q.indices, 2, 1, q.palette, q.transparentIndex)
        assertEquals(2, readShortLE(gif, 6))
        assertEquals(1, readShortLE(gif, 8))
    }

    @Test
    fun gifReservesTransparentSlot() {
        val pixels = intArrayOf(0x00FF0000.toInt(), 0xFF00FF00.toInt())
        val q = quantizeToPalette(pixels)
        assertEquals(0, q.transparentIndex)
        assertEquals(0, q.indices[0])
        assertEquals(1, q.indices[1])
    }

    @Test
    fun gifRoundtripFewColors() {
        val pixels = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFFFF.toInt())
        val q = quantizeToPalette(pixels)
        val gif = gifEncode(q.indices, 2, 2, q.palette, q.transparentIndex)
        val (dims, argb) = decodeGif(gif)
        assertEquals(2 to 2, dims)
        assertArrayEquals(pixels, argb)
    }

    @Test
    fun gifRoundtripRandomImage() {
        // Large enough to grow the LZW code size past several powers of two
        // and to fill the 4096-entry table (exercising the clear code path).
        val random = java.util.Random(42)
        val pixels = IntArray(128 * 128) { 0xFF000000.toInt() or random.nextInt(0x1000000) }
        val q = quantizeToPalette(pixels)
        val gif = gifEncode(q.indices, 128, 128, q.palette, q.transparentIndex)
        val (dims, argb) = decodeGif(gif)
        assertEquals(128 to 128, dims)
        // The palette is quantized to 256 colors, so compare against the
        // quantized expectation, not the original pixels.
        for (i in pixels.indices) {
            assertEquals("pixel $i", q.palette[q.indices[i]], argb[i])
        }
    }

    @Test
    fun gifRoundtripPreservesTransparency() {
        val pixels = intArrayOf(0x00FF0000.toInt(), 0xFF00FF00.toInt())
        val q = quantizeToPalette(pixels)
        val gif = gifEncode(q.indices, 2, 1, q.palette, q.transparentIndex)
        val (_, argb) = decodeGif(gif)
        assertEquals(0, argb[0]) // fully transparent
        assertEquals(0xFF00FF00.toInt(), argb[1])
    }

    // ---------- TIFF ----------

    @Test
    fun tiffHasLittleEndianHeaderAndIfdAt8() {
        val pixels = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt())
        val tiff = tiffEncode(pixels, 2, 1, hasAlpha = false)
        assertEquals('I'.code.toByte(), tiff[0])
        assertEquals('I'.code.toByte(), tiff[1])
        assertEquals(42, tiff[2].toInt())
        assertEquals(0, tiff[3].toInt())
        assertEquals(8, readLongLE(tiff, 4))
    }

    @Test
    fun tiffStripDataRoundTripsPixels() {
        val pixels = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFFFF.toInt())
        val tiff = tiffEncode(pixels, 2, 2, hasAlpha = false)
        val tags = parseIfd(tiff)
        val compression = tags[259]!!
        val stripOffset = tags[273]!!
        val byteCount = tags[279]!!
        var strip = tiff.copyOfRange(stripOffset, stripOffset + byteCount)
        if (compression == 8) {
            val inflater = Inflater()
            inflater.setInput(strip)
            val raw = ByteArray(2 * 2 * 3)
            val n = inflater.inflate(raw)
            inflater.end()
            assertEquals(raw.size, n)
            strip = raw
        } else {
            assertEquals(1, compression)
        }
        val expected = byteArrayOf(
            0xFF.toByte(), 0, 0, 0, 0xFF.toByte(), 0, // rosso, verde
            0, 0, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte() // blu, bianco
        )
        assertArrayEquals(expected, strip)
    }

    @Test
    fun tiffWithAlphaHasFourSamplesAndAlphaBytes() {
        val pixels = intArrayOf(0x80FF0000.toInt(), 0xFF00FF00.toInt())
        val tiff = tiffEncode(pixels, 2, 1, hasAlpha = true)
        val tags = parseIfd(tiff)
        assertEquals(4, tags[277]) // SamplesPerPixel
        assertEquals(2, tags[338]) // ExtraSamples: unassociated alpha
        val bitsOffset = tags[258]!! // BitsPerSample SHORT[4] lives at this offset
        assertEquals(8, readShortLE(tiff, bitsOffset))
        assertEquals(8, readShortLE(tiff, bitsOffset + 6))
        val expected = byteArrayOf(0xFF.toByte(), 0, 0, 0x80.toByte(), 0, 0xFF.toByte(), 0, 0xFF.toByte())
        val stripOffset = tags[273]!!
        val byteCount = tags[279]!!
        val strip = tiff.copyOfRange(stripOffset, stripOffset + byteCount)
        if (tags[259] == 8) {
            val inflater = Inflater()
            inflater.setInput(strip)
            val raw = ByteArray(2 * 4)
            assertEquals(raw.size, inflater.inflate(raw))
            inflater.end()
            assertArrayEquals(expected, raw)
        } else {
            assertArrayEquals(expected, strip)
        }
    }

    // ---------- helpers ----------

    /**
     * Minimal spec-compliant GIF decoder used to validate the encoder:
     * parses header, global color table, extensions and the image block,
     * then LZW-decodes the pixel indices. Returns (width, height) and ARGB pixels.
     */
    private fun decodeGif(gif: ByteArray): Pair<Pair<Int, Int>, IntArray> {
        var pos = 6 // skip "GIF89a"
        val width = readShortLE(gif, pos)
        val height = readShortLE(gif, pos + 2)
        pos += 4
        val packed = gif[pos].toInt() and 0xFF
        pos += 1
        val gctSize = 1 shl ((packed and 0x07) + 1)
        pos += 2 // background index, aspect ratio
        val gct = IntArray(gctSize)
        for (i in 0 until gctSize) {
            gct[i] = (0xFF shl 24) or
                ((gif[pos].toInt() and 0xFF) shl 16) or
                ((gif[pos + 1].toInt() and 0xFF) shl 8) or
                (gif[pos + 2].toInt() and 0xFF)
            pos += 3
        }
        var transparentIndex = -1
        while (true) {
            when (val blockId = gif[pos].toInt() and 0xFF) {
                0x21 -> { // extension
                    pos++
                    val label = gif[pos].toInt() and 0xFF
                    pos++
                    if (label == 0xF9) { // Graphic Control Extension
                        val size = gif[pos].toInt() and 0xFF
                        pos++
                        val gcePacked = gif[pos].toInt() and 0xFF
                        if (gcePacked and 0x01 != 0) transparentIndex = gif[pos + 3].toInt() and 0xFF
                        pos += size + 1 // block data + terminator
                    } else {
                        while (true) {
                            val sz = gif[pos].toInt() and 0xFF
                            pos++
                            if (sz == 0) break
                            pos += sz
                        }
                    }
                }
                0x2C -> { // image descriptor
                    pos += 10 // separator + left, top, width, height, packed
                    val minCodeSize = gif[pos].toInt() and 0xFF
                    pos++
                    val data = java.io.ByteArrayOutputStream()
                    while (true) {
                        val sz = gif[pos].toInt() and 0xFF
                        pos++
                        if (sz == 0) break
                        data.write(gif, pos, sz)
                        pos += sz
                    }
                    val indices = lzwDecode(data.toByteArray(), minCodeSize, width * height)
                    val argb = IntArray(width * height)
                    for (i in indices.indices) {
                        argb[i] = if (indices[i] == transparentIndex) 0 else gct[indices[i]]
                    }
                    return (width to height) to argb
                }
                0x3B -> throw IllegalStateException("trailer before image data")
                else -> throw IllegalStateException("unknown block: $blockId")
            }
        }
    }

    private fun lzwDecode(data: ByteArray, minCodeSize: Int, expectedPixels: Int): IntArray {
        val clearCode = 1 shl minCodeSize
        val endCode = clearCode + 1
        val prefix = IntArray(4096)
        val suffix = IntArray(4096)
        val stack = IntArray(4097)
        var bitPos = 0
        fun readCode(size: Int): Int {
            var code = 0
            for (i in 0 until size) {
                val bit = (data[bitPos / 8].toInt() shr (bitPos % 8)) and 1
                code = code or (bit shl i)
                bitPos++
            }
            return code
        }
        val output = IntArray(expectedPixels)
        var outPos = 0
        var codeSize = minCodeSize + 1
        var nextCode = endCode + 1
        var prev = -1
        while (outPos < expectedPixels) {
            val code = readCode(codeSize)
            if (code == clearCode) {
                codeSize = minCodeSize + 1
                nextCode = endCode + 1
                prev = -1
                continue
            }
            if (code == endCode) break
            var sp = 0
            var c = code
            val kwkwk = code >= nextCode
            if (kwkwk) c = prev
            while (c > endCode) {
                stack[sp++] = suffix[c]
                c = prefix[c]
            }
            stack[sp++] = c
            val firstChar = c
            if (kwkwk) stack[sp++] = firstChar
            while (sp > 0 && outPos < expectedPixels) output[outPos++] = stack[--sp]
            if (prev != -1 && nextCode < 4096) {
                prefix[nextCode] = prev
                suffix[nextCode] = firstChar
                nextCode++
                if (nextCode == (1 shl codeSize) && codeSize < 12) codeSize++
            }
            prev = code
        }
        return output
    }

    private fun parseIfd(tiff: ByteArray): Map<Int, Int> {
        val ifdOffset = readLongLE(tiff, 4)
        val count = readShortLE(tiff, ifdOffset)
        val tags = mutableMapOf<Int, Int>()
        for (i in 0 until count) {
            val entryOffset = ifdOffset + 2 + i * 12
            val tag = readShortLE(tiff, entryOffset)
            val type = readShortLE(tiff, entryOffset + 2)
            val valueCount = readLongLE(tiff, entryOffset + 4)
            val valueOrOffset = readLongLE(tiff, entryOffset + 8)
            // SHORT count=1 is stored inline; anything bigger is an offset.
            tags[tag] = if (type == 3 && valueCount == 1) valueOrOffset and 0xFFFF else valueOrOffset
        }
        return tags
    }

    private fun readShortLE(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 0xFF) or ((b[offset + 1].toInt() and 0xFF) shl 8)

    private fun readLongLE(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 0xFF) or
            ((b[offset + 1].toInt() and 0xFF) shl 8) or
            ((b[offset + 2].toInt() and 0xFF) shl 16) or
            ((b[offset + 3].toInt() and 0xFF) shl 24)
}
