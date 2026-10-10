package com.kompact.util

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

/**
 * Pure-Kotlin baseline TIFF encoder (little-endian): 8-bit RGB or RGBA,
 * single strip, uncompressed or Adobe Deflate (via java.util.zip, already
 * part of the Android runtime). Produces a real, decodable TIFF file.
 */

private const val TYPE_SHORT = 3
private const val TYPE_LONG = 4

internal fun tiffEncode(pixels: IntArray, width: Int, height: Int, hasAlpha: Boolean): ByteArray {
    val samples = if (hasAlpha) 4 else 3

    // Raw strip data: interleaved R,G,B(,A), rows top to bottom.
    val raw = ByteArray(width * height * samples)
    var o = 0
    for (p in pixels) {
        raw[o++] = (p shr 16).toByte()
        raw[o++] = (p shr 8).toByte()
        raw[o++] = p.toByte()
        if (hasAlpha) raw[o++] = (p shr 24).toByte()
    }

    // Deflate the strip; fall back to uncompressed when it does not pay off.
    val deflater = Deflater()
    deflater.setInput(raw)
    deflater.finish()
    val compBuf = ByteArray(raw.size + 1024)
    val compSize = deflater.deflate(compBuf)
    deflater.end()
    val useDeflate = compSize > 0 && compSize < raw.size
    val stripData = if (useDeflate) compBuf.copyOf(compSize) else raw
    val compression = if (useDeflate) 8 else 1

    val entryCount = if (hasAlpha) 11 else 10
    val ifdSize = 2 + entryCount * 12 + 4
    val bitsArrayOffset = 8 + ifdSize
    val bitsArrayEnd = bitsArrayOffset + samples * 2
    val stripOffset = if (bitsArrayEnd and 1 != 0) bitsArrayEnd + 1 else bitsArrayEnd

    val out = ByteArrayOutputStream()
    // Header: little-endian, magic 42, first IFD at offset 8.
    out.write(byteArrayOf('I'.code.toByte(), 'I'.code.toByte(), 42, 0))
    writeLongLE(out, 8)
    // IFD: entries must be sorted by ascending tag.
    writeShortLE(out, entryCount)
    entry(out, 256, TYPE_LONG, 1, width) // ImageWidth
    entry(out, 257, TYPE_LONG, 1, height) // ImageLength
    entry(out, 258, TYPE_SHORT, samples, bitsArrayOffset) // BitsPerSample (array)
    entry(out, 259, TYPE_SHORT, 1, compression) // Compression: 1 = none, 8 = Deflate
    entry(out, 262, TYPE_SHORT, 1, 2) // PhotometricInterpretation: RGB
    entry(out, 273, TYPE_LONG, 1, stripOffset) // StripOffsets
    entry(out, 277, TYPE_SHORT, 1, samples) // SamplesPerPixel
    entry(out, 278, TYPE_LONG, 1, height) // RowsPerStrip
    entry(out, 279, TYPE_LONG, 1, stripData.size) // StripByteCounts
    entry(out, 284, TYPE_SHORT, 1, 1) // PlanarConfiguration: chunky
    if (hasAlpha) entry(out, 338, TYPE_SHORT, 1, 2) // ExtraSamples: unassociated alpha
    writeLongLE(out, 0) // next IFD: none
    // BitsPerSample array: 8 bits per sample.
    repeat(samples) { writeShortLE(out, 8) }
    if (bitsArrayEnd and 1 != 0) out.write(0) // pad to even offset
    out.write(stripData)
    return out.toByteArray()
}

private fun entry(out: ByteArrayOutputStream, tag: Int, type: Int, count: Int, value: Int) {
    writeShortLE(out, tag)
    writeShortLE(out, type)
    writeLongLE(out, count)
    writeLongLE(out, value)
}

private fun writeShortLE(out: ByteArrayOutputStream, value: Int) {
    out.write(value and 0xFF)
    out.write((value shr 8) and 0xFF)
}

private fun writeLongLE(out: ByteArrayOutputStream, value: Int) {
    out.write(value and 0xFF)
    out.write((value shr 8) and 0xFF)
    out.write((value shr 16) and 0xFF)
    out.write((value shr 24) and 0xFF)
}
