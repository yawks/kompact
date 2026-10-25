package com.kompact.folder

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Raw container metadata, including unrecognised/vendor fields, is never round-tripped
 * through a restricted EXIF model. Pixel dimensions come from the new image header;
 * embedded metadata and thumbnails are kept verbatim, including EXIF orientation. */
internal object ImageMetadata {
    enum class Format { JPEG, PNG, WEBP }
    class Unsupported(message: String) : Exception(message)

    private data class Chunk(val type: String, val bytes: ByteArray)

    fun format(bytes: ByteArray): Format = when {
        bytes.size >= 2 && u(bytes[0]) == 255 && u(bytes[1]) == 216 -> Format.JPEG
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(pngSignature) -> Format.PNG
        bytes.size >= 12 && text(bytes, 0, 4) == "RIFF" && text(bytes, 8, 4) == "WEBP" -> Format.WEBP
        else -> throw Unsupported("Format not supported for lossless metadata preservation")
    }

    fun checkSource(bytes: ByteArray, format: Format) {
        when (format) {
            Format.JPEG -> {
                val metadata = jpegChunks(bytes)
                // Multi-picture, motion and gain-map offsets would become invalid after resizing.
                val hasAuxiliaryImage = metadata.any {
                    val value = it.bytes.toString(Charsets.ISO_8859_1)
                    value.contains("MPF\u0000") || value.contains("MotionPhoto") ||
                        value.contains("MicroVideo") || value.contains("hdrgm:") ||
                        value.contains("Container:Directory") || value.contains("urn:iso:std:iso:ts:21496")
                }
                if (hasAuxiliaryImage || !isSimpleJpeg(bytes)) {
                    throw Unsupported("Motion Photo, HDR or multi-picture JPEG is left unchanged")
                }
            }
            Format.PNG -> {
                val chunks = pngChunks(bytes)
                if (chunks.any { it.type == "acTL" } || u(chunks.first().bytes[17]) == 3 || u(chunks.first().bytes[16]) != 8 ||
                    chunks.any { it.type == "bKGD" || it.type == "hIST" }) {
                    throw Unsupported("Animated or palette-dependent PNG is left unchanged")
                }
            }
            Format.WEBP -> if (webpChunks(bytes).any { it.type == "ANIM" || it.type == "ANMF" }) {
                throw Unsupported("Animated WebP is left unchanged")
            }
        }
    }

    fun copy(source: ByteArray, encoded: ByteArray, format: Format): ByteArray {
        checkSource(source, format)
        require(format(encoded) == format)
        return when (format) {
            Format.JPEG -> {
                val outputChunks = jpegChunks(encoded)
                val headerEnd = outputChunks.sumOf { it.bytes.size } + 2
                val sourceMetadata = jpegChunks(source).filter { isJpegMetadata(it) }
                ByteArrayOutputStream().apply {
                    write(encoded, 0, 2)
                    sourceMetadata.forEach { write(it.bytes) }
                    outputChunks.filterNot { isJpegMetadata(it) }.forEach { write(it.bytes) }
                    write(encoded, headerEnd, encoded.size - headerEnd)
                }.toByteArray()
            }
            Format.PNG -> {
                val metadata = pngChunks(source).filter { isPngMetadata(it) }
                ByteArrayOutputStream().apply {
                    write(pngSignature)
                    pngChunks(encoded).filterNot { isPngMetadata(it) }.forEach {
                        write(it.bytes)
                        if (it.type == "IHDR") metadata.forEach { chunk -> write(chunk.bytes) }
                    }
                }.toByteArray()
            }
            Format.WEBP -> {
                val metadata = webpChunks(source).filterNot { it.type in webpPixelChunks }
                val image = webpChunks(encoded)
                val bounds = webpCanvas(image)
                val hasAlpha = image.any { it.type == "ALPH" } || image.any {
                    (it.type == "VP8X" && u(it.bytes[8]) and 0x10 != 0) ||
                        (it.type == "VP8L" && uint32le(it.bytes, 9) ushr 28 and 1 != 0)
                }
                val flags = (if (metadata.any { it.type == "ICCP" }) 0x20 else 0) or
                    (if (metadata.any { it.type == "EXIF" }) 0x08 else 0) or
                    (if (metadata.any { it.type == "XMP " }) 0x04 else 0) or (if (hasAlpha) 0x10 else 0)
                val extended = ByteArray(10).apply {
                    this[0] = flags.toByte()
                    for (i in 0..2) {
                        this[4 + i] = ((bounds.first - 1) shr (8 * i)).toByte()
                        this[7 + i] = ((bounds.second - 1) shr (8 * i)).toByte()
                    }
                }
                val body = ByteArrayOutputStream().apply {
                    write("WEBP".toByteArray())
                    write(webpChunk("VP8X", extended))
                    metadata.filter { it.type == "ICCP" }.forEach { write(it.bytes) }
                    image.filter { it.type in webpPixelChunks && it.type != "VP8X" }.forEach { write(it.bytes) }
                    metadata.filterNot { it.type == "ICCP" }.forEach { write(it.bytes) }
                }.toByteArray()
                "RIFF".toByteArray() + le32(body.size) + body
            }
        }.also { result ->
            val before = metadata(source, format)
            val after = metadata(result, format)
            check(before.size == after.size && before.zip(after).all { (a, b) -> a.contentEquals(b) }) {
                "Metadata verification failed"
            }
        }
    }

    private fun metadata(bytes: ByteArray, format: Format): List<ByteArray> = when (format) {
        Format.JPEG -> jpegChunks(bytes).filter { isJpegMetadata(it) }
        Format.PNG -> pngChunks(bytes).filter { isPngMetadata(it) }
        Format.WEBP -> webpChunks(bytes).filterNot { it.type in webpPixelChunks }.sortedBy { it.type }
    }.map { it.bytes }

    private fun isJpegMetadata(chunk: Chunk) = chunk.type.toInt() in 0xE0..0xEF || chunk.type.toInt() == 0xFE
    private fun isPngMetadata(chunk: Chunk) = chunk.type !in setOf("IHDR", "PLTE", "IDAT", "IEND", "tRNS")

    /** Walk entropy-coded scans as well as headers: APP/COM after SOS must not be
     * silently lost, and a Motion Photo trailer must not be mistaken for an EOI. */
    private fun isSimpleJpeg(bytes: ByteArray): Boolean {
        var offset = 2
        var scanSeen = false
        var inScan = false
        while (offset < bytes.size) {
            if (inScan) {
                while (offset < bytes.size && u(bytes[offset]) != 255) offset++
                if (offset + 1 >= bytes.size) return false
                if (u(bytes[offset + 1]) == 0 || u(bytes[offset + 1]) in 0xD0..0xD7) {
                    offset += 2
                    continue
                }
                inScan = false
            }
            if (offset + 1 >= bytes.size || u(bytes[offset]) != 255) return false
            val marker = u(bytes[offset + 1])
            if (marker == 0xD9) return scanSeen && offset + 2 == bytes.size
            if (scanSeen && (marker in 0xE0..0xEF || marker == 0xFE)) return false
            if (offset + 4 > bytes.size) return false
            val size = (u(bytes[offset + 2]) shl 8) + u(bytes[offset + 3])
            if (size < 2 || offset.toLong() + size + 2 > bytes.size) return false
            // Four-component/CMYK JPEG cannot be safely re-encoded with an unchanged Adobe APP14.
            if (marker in setOf(0xC0, 0xC1, 0xC2) && size >= 8 && u(bytes[offset + 9]) == 4) return false
            offset += size + 2
            if (marker == 0xDA) { scanSeen = true; inScan = true }
        }
        return false
    }

    private fun jpegChunks(bytes: ByteArray): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        var offset = 2
        while (offset + 4 <= bytes.size) {
            require(u(bytes[offset]) == 255) { "Invalid JPEG marker" }
            val marker = u(bytes[offset + 1])
            if (marker == 0xDA) return chunks
            require(marker != 0xD9 && marker !in 0xD0..0xD8)
            val size = (u(bytes[offset + 2]) shl 8) + u(bytes[offset + 3])
            require(size >= 2 && offset.toLong() + size + 2 <= bytes.size)
            chunks += Chunk(marker.toString(), bytes.copyOfRange(offset, offset + size + 2))
            offset += size + 2
        }
        error("JPEG has no scan")
    }

    private fun pngChunks(bytes: ByteArray): List<Chunk> {
        val chunks = mutableListOf<Chunk>()
        var offset = 8
        while (offset + 12 <= bytes.size) {
            val size = ByteBuffer.wrap(bytes, offset, 4).int
            require(size >= 0 && offset.toLong() + size + 12 <= bytes.size)
            val type = text(bytes, offset + 4, 4)
            chunks += Chunk(type, bytes.copyOfRange(offset, offset + size + 12))
            offset += size + 12
            if (type == "IEND") {
                require(offset == bytes.size) { "PNG contains trailing data" }
                require(chunks.first().type == "IHDR" && chunks.first().bytes.size == 25)
                return chunks
            }
        }
        error("Truncated PNG")
    }

    private fun webpChunks(bytes: ByteArray): List<Chunk> {
        require(uint32le(bytes, 4).toLong() + 8 == bytes.size.toLong())
        val chunks = mutableListOf<Chunk>()
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val size = uint32le(bytes, offset + 4)
            require(size >= 0)
            val end = offset.toLong() + 8 + size + size % 2
            require(end <= bytes.size)
            chunks += Chunk(text(bytes, offset, 4), bytes.copyOfRange(offset, end.toInt()))
            offset = end.toInt()
        }
        require(offset == bytes.size)
        return chunks
    }

    private fun webpCanvas(chunks: List<Chunk>): Pair<Int, Int> {
        chunks.firstOrNull { it.type == "VP8X" }?.bytes?.let {
            require(it.size >= 18)
            return (1 + u(it[12]) + (u(it[13]) shl 8) + (u(it[14]) shl 16)) to
                (1 + u(it[15]) + (u(it[16]) shl 8) + (u(it[17]) shl 16))
        }
        val chunk = chunks.first { it.type == "VP8 " || it.type == "VP8L" }
        val b = chunk.bytes
        return if (chunk.type == "VP8 ") {
            require(b.size >= 18)
            (u(b[14]) + (u(b[15]) shl 8) and 0x3FFF) to (u(b[16]) + (u(b[17]) shl 8) and 0x3FFF)
        } else {
            require(b.size >= 13 && u(b[8]) == 0x2F)
            val bits = uint32le(b, 9)
            ((bits and 0x3FFF) + 1) to (((bits ushr 14) and 0x3FFF) + 1)
        }
    }

    private fun webpChunk(type: String, payload: ByteArray) = type.toByteArray() + le32(payload.size) +
        payload + if (payload.size % 2 == 1) byteArrayOf(0) else byteArrayOf()
    private fun le32(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    private fun uint32le(bytes: ByteArray, offset: Int) = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
    private fun u(value: Byte) = value.toInt() and 255
    private fun text(bytes: ByteArray, offset: Int, length: Int) = String(bytes, offset, length, Charsets.US_ASCII)
    private val pngSignature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    private val webpPixelChunks = setOf("VP8X", "VP8 ", "VP8L", "ALPH", "ANIM", "ANMF")
}
