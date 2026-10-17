package com.kompact.util

import android.graphics.Bitmap
import com.kompact.model.ExifData
import com.kompact.model.ImageOutputFormat
import java.io.ByteArrayOutputStream

/**
 * Preservation of EXIF / XMP metadata when re-encoding into modern formats
 * (HEIF, HEIC, AVIF, JXL).
 *
 * The awxkee native encoders (libheif / libavif / libjxl) do not expose an
 * EXIF-injection API, and AndroidX ExifInterface cannot write EXIF into these
 * container formats. The result is that re-encoding a HEIC or JXL loses all
 * metadata (GPS, camera info, date, …).
 *
 * This file performs a **raw box-level copy**: it extracts the `Exif` box
 * from the original container and injects it into the freshly encoded file,
 * without decoding or re-encoding the metadata itself.
 *
 * Supported containers:
 *  - ISOBMFF (HEIF / HEIC / AVIF): the Exif payload lives in a top-level
 *    `Exif` box (type `'Exif'`) or inside the `meta` box.
 *  - JXL container: the Exif payload lives in a top-level `Exif` chunk.
 */

private val MODERN_FORMATS = setOf(
    ImageOutputFormat.HEIF,
    ImageOutputFormat.HEIC,
    ImageOutputFormat.AVIF,
    ImageOutputFormat.JXL,
)

internal fun isModernFormat(format: ImageOutputFormat): Boolean =
    format in MODERN_FORMATS

/**
 * Copies the raw EXIF box from [originalBytes] into [encodedBytes].
 *
 * Returns [encodedBytes] unchanged when:
 *  - the format is not a modern format,
 *  - the original has no EXIF box, or
 *  - the encoded file is not a valid container (defensive).
 *
 * The injection is best-effort: if the encoded container cannot be parsed,
 * the original encoded bytes are returned so the output is never corrupted.
 */
internal fun preserveExifFromOriginal(
    originalBytes: ByteArray,
    encodedBytes: ByteArray,
    format: ImageOutputFormat,
): ByteArray {
    if (!isModernFormat(format)) return encodedBytes
    if (originalBytes.isEmpty() || encodedBytes.isEmpty()) return encodedBytes
    return when (format) {
        ImageOutputFormat.HEIF, ImageOutputFormat.HEIC, ImageOutputFormat.AVIF ->
            injectExifBoxIsoBmff(originalBytes, encodedBytes)
        ImageOutputFormat.JXL ->
            injectExifChunkJxl(originalBytes, encodedBytes)
        else -> encodedBytes
    }
}

// =========================================================================
// ISOBMFF (HEIF / HEIC / AVIF)
// =========================================================================

/**
 * ISOBMFF box header: 4-byte big-endian size (includes the 8-byte header),
 * 4-byte type. If size == 1, a 64-bit "largesize" follows the type.
 */
private const val ISO_BOX_HEADER_SIZE = 8

/**
 * Extracts the raw `Exif` box (including its 8-byte header) from an ISOBMFF
 * container. Searches top-level boxes and, recursively, the children of
 * the `meta` box (which carries a 4-byte version/flags full-box header).
 */
internal fun extractExifBoxIsoBmff(bytes: ByteArray): ByteArray? {
    return findExifBoxInRange(bytes, 0, bytes.size, topLevel = true)
}

private fun findExifBoxInRange(
    bytes: ByteArray,
    start: Int,
    end: Int,
    topLevel: Boolean,
): ByteArray? {
    var offset = start
    while (offset + ISO_BOX_HEADER_SIZE <= end) {
        val size = readUInt32BE(bytes, offset).toInt()
        val type = readBoxType(bytes, offset + 4)
        if (size < ISO_BOX_HEADER_SIZE) return null // corrupt
        val boxEnd = offset + size
        if (boxEnd > end) return null // truncated

        when {
            type == "Exif" -> {
                // Found the Exif box: return the raw bytes (header + payload).
                return bytes.copyOfRange(offset, boxEnd)
            }
            type == "meta" && topLevel -> {
                // The meta box is a FullBox: 4 bytes of version/flags precede
                // the child boxes. Recurse into its children.
                val childStart = offset + ISO_BOX_HEADER_SIZE + 4
                if (childStart < boxEnd) {
                    findExifBoxInRange(bytes, childStart, boxEnd, topLevel = false)
                        ?.let { return it }
                }
            }
        }
        offset = boxEnd
    }
    return null
}

/**
 * Injects the `Exif` box extracted from [originalBytes] into [encodedBytes].
 *
 * The box is inserted right after the top-level `meta` box (or after `ftyp`
 * when no `meta` box exists). This placement is valid per ISO/IEC 14496-12
 * and is what most HEIF writers expect.
 */
private fun injectExifBoxIsoBmff(
    originalBytes: ByteArray,
    encodedBytes: ByteArray,
): ByteArray {
    val exifBox = extractExifBoxIsoBmff(originalBytes) ?: return encodedBytes

    // Find the insertion offset: end of the top-level `meta` box, or end of
    // `ftyp` when no meta box is present.
    val insertOffset = findExifInsertOffsetIsoBmff(encodedBytes)
        ?: return encodedBytes // cannot parse encoded container

    val result = ByteArray(encodedBytes.size + exifBox.size)
    System.arraycopy(encodedBytes, 0, result, 0, insertOffset)
    System.arraycopy(exifBox, 0, result, insertOffset, exifBox.size)
    System.arraycopy(
        encodedBytes, insertOffset,
        result, insertOffset + exifBox.size,
        encodedBytes.size - insertOffset,
    )
    return result
}

/**
 * Returns the byte offset at which a top-level `Exif` box should be inserted:
 * right after the `meta` box, or after `ftyp` when no `meta` box exists.
 * Returns null when the container cannot be parsed.
 */
private fun findExifInsertOffsetIsoBmff(bytes: ByteArray): Int? {
    var offset = 0
    var afterFtyp = 0
    while (offset + ISO_BOX_HEADER_SIZE <= bytes.size) {
        val size = readUInt32BE(bytes, offset).toInt()
        val type = readBoxType(bytes, offset + 4)
        if (size < ISO_BOX_HEADER_SIZE || offset + size > bytes.size) return null

        when (type) {
            "meta" -> return offset + size // insert right after meta
            "ftyp" -> afterFtyp = offset + size
        }
        offset += size
    }
    // No meta box: insert after ftyp, or at the very start.
    return if (afterFtyp > 0) afterFtyp else 0
}

// =========================================================================
// JXL container
// =========================================================================

/**
 * JXL container chunks have the same 8-byte header layout as ISOBMFF boxes
 * (4-byte big-endian size + 4-byte type), but the size field does NOT include
 * the 8-byte header (unlike ISOBMFF). We handle this difference here.
 *
 * JXL top-level structure:
 *   - Signature box (12 bytes: 0x00 0x00 0x00 0x0C 'JXL ' 0x0D 0x0A 0x87 0x0A)
 *   - ftyp box
 *   - Other chunks: 'jbrd', 'Exif', 'xml ', 'jxlc', 'jxlp', …
 */
private const val JXL_SIGNATURE_SIZE = 12

private fun extractExifChunkJxl(bytes: ByteArray): ByteArray? {
    if (bytes.size < JXL_SIGNATURE_SIZE + ISO_BOX_HEADER_SIZE) return null
    var offset = JXL_SIGNATURE_SIZE
    while (offset + ISO_BOX_HEADER_SIZE <= bytes.size) {
        val size = readUInt32BE(bytes, offset).toInt()
        val type = readBoxType(bytes, offset + 4)
        // In JXL, the size field excludes the 8-byte header.
        val chunkSize = size + ISO_BOX_HEADER_SIZE
        if (chunkSize < ISO_BOX_HEADER_SIZE || offset + chunkSize > bytes.size) return null

        if (type == "Exif") {
            return bytes.copyOfRange(offset, offset + chunkSize)
        }
        offset += chunkSize
    }
    return null
}

private fun injectExifChunkJxl(
    originalBytes: ByteArray,
    encodedBytes: ByteArray,
): ByteArray {
    val exifChunk = extractExifChunkJxl(originalBytes) ?: return encodedBytes

    // In the JXL container, insert the Exif chunk right after the ftyp box
    // (before jbrd / jxlc / jxlp). The signature box is 12 bytes, then boxes
    // follow with the same 8-byte header layout.
    if (encodedBytes.size < JXL_SIGNATURE_SIZE + ISO_BOX_HEADER_SIZE) return encodedBytes

    var offset = JXL_SIGNATURE_SIZE
    var afterFtyp = -1
    while (offset + ISO_BOX_HEADER_SIZE <= encodedBytes.size) {
        val size = readUInt32BE(encodedBytes, offset).toInt()
        val type = readBoxType(encodedBytes, offset + 4)
        val chunkSize = size + ISO_BOX_HEADER_SIZE
        if (chunkSize < ISO_BOX_HEADER_SIZE || offset + chunkSize > encodedBytes.size) {
            break
        }
        if (type == "ftyp") {
            afterFtyp = offset + chunkSize
        }
        offset += chunkSize
    }

    val insertOffset = if (afterFtyp >= 0) afterFtyp else JXL_SIGNATURE_SIZE
    val result = ByteArray(encodedBytes.size + exifChunk.size)
    System.arraycopy(encodedBytes, 0, result, 0, insertOffset)
    System.arraycopy(exifChunk, 0, result, insertOffset, exifChunk.size)
    System.arraycopy(
        encodedBytes, insertOffset,
        result, insertOffset + exifChunk.size,
        encodedBytes.size - insertOffset,
    )
    return result
}

// =========================================================================
// EXIF modification (user edits applied on top of preserved metadata)
// =========================================================================

/**
 * Applies user EXIF modifications to a modern-format container.
 *
 * Strategy: the awxkee encoders and AndroidX ExifInterface cannot write EXIF
 * into HEIF/HEIC/AVIF/JXL containers. We work around this by:
 *  1. Creating a tiny 1×1 JPEG carrying the desired EXIF tags (via the
 *     existing [writeExifData], which works on JPEG).
 *  2. Extracting the raw TIFF payload from the JPEG's APP1 segment.
 *  3. Wrapping it in an `Exif` box/chunk and replacing the existing one
 *     in the target container (or injecting it if none exists).
 *
 * This preserves ALL EXIF fields (including the 20+ advanced fields in
 * [ExifData]) because the TIFF structure is produced by ExifInterface itself.
 */
internal fun applyExifModifications(
    encodedBytes: ByteArray,
    exifData: ExifData,
    format: ImageOutputFormat,
): ByteArray {
    if (!isModernFormat(format)) return encodedBytes
    if (encodedBytes.isEmpty()) return encodedBytes

    // Step 1: create a 1×1 JPEG with the desired EXIF metadata.
    val jpegBytes = createJpegWithExif(exifData) ?: return encodedBytes

    // Step 2: extract the TIFF payload from the APP1 segment.
    val tiffData = extractTiffFromJpegApp1(jpegBytes) ?: return encodedBytes

    // Step 3: replace or inject the Exif box/chunk in the container.
    return replaceExifInContainer(encodedBytes, tiffData, format)
}

/**
 * Creates a minimal 1×1 JPEG and stamps the given [exifData] onto it using
 * the existing [writeExifData] (which works correctly on JPEG).
 */
private fun createJpegWithExif(exifData: ExifData): ByteArray? {
    return try {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        bitmap.recycle()
        writeExifData(stream.toByteArray(), exifData)
    } catch (e: Exception) {
        null
    }
}

/**
 * Extracts the raw TIFF data from a JPEG's APP1 (Exif) segment.
 *
 * JPEG APP1 layout:
 *   FF E1 | length(2) | "Exif\0\0" (6) | TIFF data…
 *
 * Returns null if no valid APP1/Exif segment is found.
 */
private fun extractTiffFromJpegApp1(jpegBytes: ByteArray): ByteArray? {
    if (jpegBytes.size < 4) return null
    if (jpegBytes[0] != 0xFF.toByte() || jpegBytes[1] != 0xD8.toByte()) return null

    var offset = 2 // skip SOI
    while (offset + 4 <= jpegBytes.size) {
        if (jpegBytes[offset] != 0xFF.toByte()) return null
        val marker = jpegBytes[offset + 1].toInt() and 0xFF

        // SOS (Start of Scan) — no more metadata segments after this.
        if (marker == 0xDA) return null

        // Standalone markers without a length field.
        if (marker == 0x01 || marker in 0xD0..0xD7) {
            offset += 2
            continue
        }

        val length = ((jpegBytes[offset + 2].toInt() and 0xFF) shl 8) or
            (jpegBytes[offset + 3].toInt() and 0xFF)
        if (length < 2) return null

        if (marker == 0xE1) { // APP1
            val app1Start = offset + 4
            val app1End = offset + 2 + length
            if (app1End > jpegBytes.size) return null
            // Verify "Exif\0\0" identifier.
            if (app1Start + 6 <= app1End &&
                jpegBytes[app1Start] == 'E'.code.toByte() &&
                jpegBytes[app1Start + 1] == 'x'.code.toByte() &&
                jpegBytes[app1Start + 2] == 'i'.code.toByte() &&
                jpegBytes[app1Start + 3] == 'f'.code.toByte() &&
                jpegBytes[app1Start + 4] == 0.toByte() &&
                jpegBytes[app1Start + 5] == 0.toByte()
            ) {
                return jpegBytes.copyOfRange(app1Start + 6, app1End)
            }
        }
        offset += 2 + length
    }
    return null
}

/**
 * Wraps raw TIFF data in an ISOBMFF `Exif` box and replaces the existing
 * one in [containerBytes] (or injects it if none exists).
 */
private fun replaceExifInContainer(
    containerBytes: ByteArray,
    tiffData: ByteArray,
    format: ImageOutputFormat,
): ByteArray {
    return when (format) {
        ImageOutputFormat.HEIF, ImageOutputFormat.HEIC, ImageOutputFormat.AVIF -> {
            val newBox = createExifBoxIsoBmff(tiffData)
            replaceExifBoxIsoBmff(containerBytes, newBox)
        }
        ImageOutputFormat.JXL -> {
            val newChunk = createExifChunkJxl(tiffData)
            replaceExifChunkJxl(containerBytes, newChunk)
        }
        else -> containerBytes
    }
}

/**
 * Creates an ISOBMFF `Exif` box from raw TIFF data.
 * Layout: [size:4]["Exif":4][tiffHeaderOffset:4 = 0][TIFF data…]
 */
private fun createExifBoxIsoBmff(tiffData: ByteArray): ByteArray {
    val boxSize = 12 + tiffData.size // 8 header + 4 offset + TIFF
    val box = ByteArray(boxSize)
    writeUInt32BE(box, 0, boxSize.toLong())
    box[4] = 'E'.code.toByte()
    box[5] = 'x'.code.toByte()
    box[6] = 'i'.code.toByte()
    box[7] = 'f'.code.toByte()
    // bytes 8-11: offset to TIFF header = 0 (already zeroed)
    System.arraycopy(tiffData, 0, box, 12, tiffData.size)
    return box
}

/**
 * Creates a JXL `Exif` chunk from raw TIFF data.
 * Layout: [size:4 (excludes header)]["Exif":4][TIFF data…]
 */
private fun createExifChunkJxl(tiffData: ByteArray): ByteArray {
    val chunkSize = 8 + tiffData.size
    val chunk = ByteArray(chunkSize)
    // JXL size field excludes the 8-byte header.
    writeUInt32BE(chunk, 0, tiffData.size.toLong())
    chunk[4] = 'E'.code.toByte()
    chunk[5] = 'x'.code.toByte()
    chunk[6] = 'i'.code.toByte()
    chunk[7] = 'f'.code.toByte()
    System.arraycopy(tiffData, 0, chunk, 8, tiffData.size)
    return chunk
}

// -------------------------------------------------------------------------
// ISOBMFF box replacement
// -------------------------------------------------------------------------

/**
 * Replaces the existing top-level `Exif` box in [containerBytes] with
 * [newExifBox]. If no Exif box exists, the new box is injected after the
 * `meta` box (or `ftyp`). Returns [containerBytes] unchanged on parse failure.
 */
private fun replaceExifBoxIsoBmff(
    containerBytes: ByteArray,
    newExifBox: ByteArray,
): ByteArray {
    val existing = findExifBoxOffsetIsoBmff(containerBytes)
    return if (existing != null) {
        val (start, end) = existing
        spliceBytes(containerBytes, start, end, newExifBox)
    } else {
        // No existing Exif box: inject after meta/ftyp.
        injectRawExifBoxIsoBmff(containerBytes, newExifBox)
    }
}

/**
 * Finds the byte range [start, end) of the top-level `Exif` box.
 * Also searches inside the `meta` box children. Returns null if not found
 * or if the container cannot be parsed.
 */
private fun findExifBoxOffsetIsoBmff(bytes: ByteArray): Pair<Int, Int>? {
    var offset = 0
    while (offset + ISO_BOX_HEADER_SIZE <= bytes.size) {
        val size = readUInt32BE(bytes, offset).toInt()
        val type = readBoxType(bytes, offset + 4)
        if (size < ISO_BOX_HEADER_SIZE || offset + size > bytes.size) return null

        if (type == "Exif") {
            return offset to (offset + size)
        }
        if (type == "meta") {
            // Search inside meta's children (skip 4-byte version/flags).
            val childStart = offset + ISO_BOX_HEADER_SIZE + 4
            val childEnd = offset + size
            findExifBoxOffsetInRange(bytes, childStart, childEnd)?.let { return it }
        }
        offset += size
    }
    return null
}

private fun findExifBoxOffsetInRange(
    bytes: ByteArray,
    start: Int,
    end: Int,
): Pair<Int, Int>? {
    var offset = start
    while (offset + ISO_BOX_HEADER_SIZE <= end) {
        val size = readUInt32BE(bytes, offset).toInt()
        val type = readBoxType(bytes, offset + 4)
        if (size < ISO_BOX_HEADER_SIZE || offset + size > end) return null
        if (type == "Exif") return offset to (offset + size)
        offset += size
    }
    return null
}

/**
 * Injects a pre-built Exif box into [containerBytes] at the standard
 * location (after `meta` or `ftyp`).
 */
private fun injectRawExifBoxIsoBmff(
    containerBytes: ByteArray,
    exifBox: ByteArray,
): ByteArray {
    val insertOffset = findExifInsertOffsetIsoBmff(containerBytes)
        ?: return containerBytes
    return spliceBytes(containerBytes, insertOffset, insertOffset, exifBox)
}

// -------------------------------------------------------------------------
// JXL chunk replacement
// -------------------------------------------------------------------------

/**
 * Replaces the existing `Exif` chunk in a JXL container with [newExifChunk].
 * If no Exif chunk exists, it is injected after the `ftyp` box.
 */
private fun replaceExifChunkJxl(
    containerBytes: ByteArray,
    newExifChunk: ByteArray,
): ByteArray {
    if (containerBytes.size < JXL_SIGNATURE_SIZE + ISO_BOX_HEADER_SIZE) return containerBytes

    var offset = JXL_SIGNATURE_SIZE
    var afterFtyp = -1
    while (offset + ISO_BOX_HEADER_SIZE <= containerBytes.size) {
        val size = readUInt32BE(containerBytes, offset).toInt()
        val type = readBoxType(containerBytes, offset + 4)
        // JXL size field excludes the 8-byte header.
        val chunkSize = size + ISO_BOX_HEADER_SIZE
        if (chunkSize < ISO_BOX_HEADER_SIZE || offset + chunkSize > containerBytes.size) {
            break
        }
        when (type) {
            "Exif" -> {
                // Replace existing Exif chunk.
                return spliceBytes(containerBytes, offset, offset + chunkSize, newExifChunk)
            }
            "ftyp" -> afterFtyp = offset + chunkSize
        }
        offset += chunkSize
    }
    // No existing Exif chunk: inject after ftyp (or after signature).
    val insertOffset = if (afterFtyp >= 0) afterFtyp else JXL_SIGNATURE_SIZE
    return spliceBytes(containerBytes, insertOffset, insertOffset, newExifChunk)
}

// -------------------------------------------------------------------------
// Byte splice helper
// -------------------------------------------------------------------------

/**
 * Returns a new byte array with [replacement] spliced in between
 * [start] (inclusive) and [end] (exclusive) of [source].
 * When start == end, this is a pure insertion.
 */
private fun spliceBytes(
    source: ByteArray,
    start: Int,
    end: Int,
    replacement: ByteArray,
): ByteArray {
    val removed = end - start
    val result = ByteArray(source.size - removed + replacement.size)
    System.arraycopy(source, 0, result, 0, start)
    System.arraycopy(replacement, 0, result, start, replacement.size)
    System.arraycopy(source, end, result, start + replacement.size, source.size - end)
    return result
}

// =========================================================================
// Byte helpers
// =========================================================================

/** Reads a 4-byte big-endian unsigned integer. */
private fun readUInt32BE(bytes: ByteArray, offset: Int): Long {
    return ((bytes[offset].toLong() and 0xFF) shl 24) or
        ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
        ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
        (bytes[offset + 3].toLong() and 0xFF)
}

/** Writes a 4-byte big-endian unsigned integer. */
private fun writeUInt32BE(bytes: ByteArray, offset: Int, value: Long) {
    bytes[offset] = (value shr 24).toByte()
    bytes[offset + 1] = (value shr 16).toByte()
    bytes[offset + 2] = (value shr 8).toByte()
    bytes[offset + 3] = value.toByte()
}

/** Reads a 4-byte box/chunk type as an ASCII string. */
private fun readBoxType(bytes: ByteArray, offset: Int): String {
    return String(bytes, offset, 4, Charsets.US_ASCII)
}
