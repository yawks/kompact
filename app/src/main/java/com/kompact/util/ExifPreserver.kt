package com.kompact.util

import com.kompact.model.ImageOutputFormat

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
// Byte helpers
// =========================================================================

/** Reads a 4-byte big-endian unsigned integer. */
private fun readUInt32BE(bytes: ByteArray, offset: Int): Long {
    return ((bytes[offset].toLong() and 0xFF) shl 24) or
        ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
        ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
        (bytes[offset + 3].toLong() and 0xFF)
}

/** Reads a 4-byte box/chunk type as an ASCII string. */
private fun readBoxType(bytes: ByteArray, offset: Int): String {
    return String(bytes, offset, 4, Charsets.US_ASCII)
}
