package com.kompact.util

import java.io.ByteArrayOutputStream

/**
 * Pure-Kotlin GIF89a encoder: median-cut palette quantization (max 256
 * colors, one reserved slot for transparency) + LZW compression.
 * No native dependencies; produces a real, decodable GIF file.
 */

internal data class QuantizedPalette(
    val indices: IntArray,
    val palette: IntArray, // RGB ints (0xFFRRGGBB), padded to a power of two
    val transparentIndex: Int?, // palette slot used for fully transparent pixels
)

private const val ALPHA_THRESHOLD = 128

internal fun quantizeToPalette(pixels: IntArray, maxColors: Int = 256): QuantizedPalette {
    val hasTransparency = pixels.any { (it ushr 24) < ALPHA_THRESHOLD }
    val transparentIndex = if (hasTransparency) 0 else null
    val colorBudget = if (hasTransparency) maxColors - 1 else maxColors

    val opaqueColors = pixels
        .filter { (it ushr 24) >= ALPHA_THRESHOLD }
        .map { it and 0xFFFFFF }
        .distinct()

    // Median-cut: repeatedly split the box with the widest channel range.
    var boxes: List<List<Int>> = listOf(opaqueColors)
    while (boxes.size < colorBudget) {
        var target = -1
        var targetRange = 0
        var targetChannel = 0
        for (i in boxes.indices) {
            val box = boxes[i]
            if (box.size <= 1) continue
            for (ch in 0..2) {
                val range = boxRange(box, ch)
                if (range > targetRange) {
                    targetRange = range
                    target = i
                    targetChannel = ch
                }
            }
        }
        if (target < 0 || targetRange <= 0) break
        val sorted = boxes[target].sortedBy { channelOf(it, targetChannel) }
        val mid = sorted.size / 2
        val newBoxes = boxes.toMutableList()
        newBoxes[target] = sorted.subList(0, mid)
        newBoxes.add(sorted.subList(mid, sorted.size))
        boxes = newBoxes
    }

    val realColors = boxes.map { averageColor(it) }
    val realCount = if (transparentIndex != null) realColors.size + 1 else realColors.size
    val palette = IntArray(paddedPaletteSize(realCount))
    if (transparentIndex != null) {
        palette[0] = 0xFF000000.toInt()
        realColors.forEachIndexed { i, c -> palette[i + 1] = c }
    } else {
        realColors.forEachIndexed { i, c -> palette[i] = c }
    }

    val cache = HashMap<Int, Int>()
    val indices = IntArray(pixels.size)
    for (i in pixels.indices) {
        val p = pixels[i]
        if ((p ushr 24) < ALPHA_THRESHOLD) {
            indices[i] = transparentIndex ?: 0
            continue
        }
        val rgb = p and 0xFFFFFF
        indices[i] = cache.getOrPut(rgb) { nearestPaletteIndex(rgb, palette, realCount) }
    }
    return QuantizedPalette(indices, palette, transparentIndex)
}

internal fun gifEncode(
    indices: IntArray,
    width: Int,
    height: Int,
    palette: IntArray,
    transparentIndex: Int?,
): ByteArray {
    val out = ByteArrayOutputStream()
    // Header
    out.write("GIF89a".toByteArray(Charsets.US_ASCII))
    // Logical Screen Descriptor
    writeShortLE(out, width)
    writeShortLE(out, height)
    val gctBits = when (palette.size) {
        2 -> 0
        4 -> 1
        8 -> 2
        16 -> 3
        32 -> 4
        64 -> 5
        128 -> 6
        else -> 7
    }
    out.write(0xF0 or gctBits) // global color table flag, 8-bit color resolution, table size
    out.write(transparentIndex ?: 0) // background color index
    out.write(0) // pixel aspect ratio
    // Global Color Table (RGB triplets, zero-padded to the power-of-two size)
    for (color in palette) {
        out.write((color shr 16) and 0xFF)
        out.write((color shr 8) and 0xFF)
        out.write(color and 0xFF)
    }
    for (i in palette.size until (1 shl (gctBits + 1))) {
        out.write(0)
        out.write(0)
        out.write(0)
    }
    // Graphic Control Extension for the transparent color
    if (transparentIndex != null) {
        out.write(0x21)
        out.write(0xF9)
        out.write(4)
        out.write(0x01) // transparent color flag set
        writeShortLE(out, 0) // delay
        out.write(transparentIndex)
        out.write(0) // block terminator
    }
    // Image Descriptor
    out.write(0x2C)
    writeShortLE(out, 0)
    writeShortLE(out, 0)
    writeShortLE(out, width)
    writeShortLE(out, height)
    out.write(0) // no local color table
    // Image Data: LZW
    val minCodeSize = maxOf(2, gctBits + 1)
    out.write(minCodeSize)
    val compressed = lzwCompress(indices, minCodeSize)
    var offset = 0
    while (offset < compressed.size) {
        val blockSize = minOf(255, compressed.size - offset)
        out.write(blockSize)
        out.write(compressed, offset, blockSize)
        offset += blockSize
    }
    out.write(0) // block terminator
    // Trailer
    out.write(0x3B)
    return out.toByteArray()
}

private fun lzwCompress(indices: IntArray, minCodeSize: Int): ByteArray {
    val clearCode = 1 shl minCodeSize
    val endCode = clearCode + 1
    val bits = BitWriter()
    var dict = HashMap<Long, Int>()
    var nextCode = endCode + 1
    var codeSize = minCodeSize + 1

    bits.write(clearCode, codeSize)
    var prefix = indices[0]
    for (i in 1 until indices.size) {
        val k = indices[i]
        val key = (prefix.toLong() shl 32) or k.toLong()
        val found = dict[key]
        if (found != null) {
            prefix = found
        } else {
            bits.write(prefix, codeSize)
            if (nextCode < 4096) {
                dict[key] = nextCode
                nextCode++
                // GIF spec: the code size grows when the next table entry
                // would no longer fit in the current code size.
                if (nextCode > (1 shl codeSize) && codeSize < 12) codeSize++
            } else {
                bits.write(clearCode, codeSize)
                dict = HashMap()
                nextCode = endCode + 1
                codeSize = minCodeSize + 1
            }
            prefix = k
        }
    }
    bits.write(prefix, codeSize)
    bits.write(endCode, codeSize)
    return bits.finish()
}

private class BitWriter {
    private val bytes = ByteArrayOutputStream()
    private var buffer = 0
    private var count = 0

    fun write(code: Int, size: Int) {
        buffer = buffer or (code shl count)
        count += size
        while (count >= 8) {
            bytes.write(buffer and 0xFF)
            buffer = buffer ushr 8
            count -= 8
        }
    }

    fun finish(): ByteArray {
        if (count > 0) {
            bytes.write(buffer and 0xFF)
            buffer = 0
            count = 0
        }
        return bytes.toByteArray()
    }
}

private fun channelOf(color: Int, channel: Int): Int = when (channel) {
    0 -> (color shr 16) and 0xFF
    1 -> (color shr 8) and 0xFF
    else -> color and 0xFF
}

private fun boxRange(box: List<Int>, channel: Int): Int {
    var min = 255
    var max = 0
    for (c in box) {
        val v = channelOf(c, channel)
        if (v < min) min = v
        if (v > max) max = v
    }
    return max - min
}

private fun averageColor(box: List<Int>): Int {
    var r = 0L
    var g = 0L
    var b = 0L
    for (c in box) {
        r += (c shr 16) and 0xFF
        g += (c shr 8) and 0xFF
        b += c and 0xFF
    }
    val n = box.size
    return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
}

private fun nearestPaletteIndex(rgb: Int, palette: IntArray, count: Int): Int {
    val r = (rgb shr 16) and 0xFF
    val g = (rgb shr 8) and 0xFF
    val b = rgb and 0xFF
    var best = 0
    var bestDist = Int.MAX_VALUE
    for (i in 0 until count) {
        val c = palette[i]
        val dr = r - ((c shr 16) and 0xFF)
        val dg = g - ((c shr 8) and 0xFF)
        val db = b - (c and 0xFF)
        val dist = dr * dr + dg * dg + db * db
        if (dist < bestDist) {
            bestDist = dist
            best = i
        }
    }
    return best
}

private fun paddedPaletteSize(realCount: Int): Int {
    var size = 2
    while (size < realCount) size *= 2
    return size.coerceAtMost(256)
}

private fun writeShortLE(out: ByteArrayOutputStream, value: Int) {
    out.write(value and 0xFF)
    out.write((value shr 8) and 0xFF)
}
