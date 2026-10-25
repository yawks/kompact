package com.kompact.folder

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

class ImageMetadataTest {
    private fun segment(marker: Int, value: ByteArray) = byteArrayOf(255.toByte(), marker.toByte(),
        ((value.size + 2) shr 8).toByte(), (value.size + 2).toByte()) + value
    private fun jpeg(vararg segments: ByteArray) = byteArrayOf(255.toByte(), 216.toByte()) +
        segments.fold(byteArrayOf()) { a, b -> a + b } +
        segment(0xDA, byteArrayOf(1, 1, 0, 0, 63, 0)) + byteArrayOf(3, 4, 255.toByte(), 0, 9, 255.toByte(), 217.toByte())

    @Test fun jpegPreservesOpaqueExifXmpIptcIccCommentsAndVendorDataVerbatim() {
        val metadata = listOf(segment(0xE1, "Exif\u0000\u0000opaque-maker-notes-and-thumbnail".toByteArray()),
            segment(0xE1, "http://ns.adobe.com/xap/1.0/\u0000GPS date orientation".toByteArray()),
            segment(0xE2, "ICC_PROFILE\u0000colour-profile".toByteArray()),
            segment(0xED, "Photoshop 3.0\u0000IPTC keywords".toByteArray()),
            segment(0xEF, byteArrayOf(0, 1, 2, 3)), segment(0xFE, "a comment".toByteArray()))
        val source = jpeg(*metadata.toTypedArray(), segment(0xDB, byteArrayOf(11)))
        val encoded = jpeg(segment(0xE0, "encoder JFIF".toByteArray()), segment(0xDB, byteArrayOf(42)))
        val result = ImageMetadata.copy(source, encoded, ImageMetadata.Format.JPEG)
        assertArrayEquals(jpeg(*metadata.toTypedArray(), segment(0xDB, byteArrayOf(42))), result)
    }

    @Test fun motionHdrMpoAndTrailingPayloadAreRejected() {
        for (tag in listOf("MPF\u0000", "MotionPhoto", "MicroVideo", "hdrgm:Version", "Container:Directory")) {
            assertThrows(ImageMetadata.Unsupported::class.java) {
                ImageMetadata.copy(jpeg(segment(0xE1, tag.toByteArray())), jpeg(), ImageMetadata.Format.JPEG)
            }
        }
        assertThrows(ImageMetadata.Unsupported::class.java) {
            ImageMetadata.checkSource(jpeg() + byteArrayOf(1, 255.toByte(), 217.toByte()), ImageMetadata.Format.JPEG)
        }
    }

    @Test fun jpegMetadataBetweenProgressiveScansIsNotSilentlyDiscarded() {
        val source = jpeg().dropLast(2).toByteArray() + segment(0xE1, "late metadata".toByteArray()) + byteArrayOf(255.toByte(), 217.toByte())
        assertThrows(ImageMetadata.Unsupported::class.java) { ImageMetadata.checkSource(source, ImageMetadata.Format.JPEG) }
    }

    @Test fun pngKeepsExifTextColourAndUnknownChunksWithTheirCrc() {
        val metadata = listOf(pngChunk("eXIf", "raw exif".toByteArray()), pngChunk("iTXt", "raw XMP".toByteArray()),
            pngChunk("iCCP", "raw ICC".toByteArray()), pngChunk("vpAg", byteArrayOf(9, 8, 7)))
        val source = png(4000, 3000, metadata)
        val encoded = png(2581, 1936, emptyList())
        assertArrayEquals(png(2581, 1936, metadata), ImageMetadata.copy(source, encoded, ImageMetadata.Format.PNG))
    }

    @Test fun animatedPngIsRejected() {
        val source = png(4000, 3000, listOf(pngChunk("acTL", byteArrayOf(1, 2))))
        assertThrows(ImageMetadata.Unsupported::class.java) { ImageMetadata.checkSource(source, ImageMetadata.Format.PNG) }
    }

    @Test fun webpKeepsExifXmpIccAndOpaqueChunksAndSetsExtendedHeaderFlags() {
        val metadata = listOf(webpChunk("EXIF", "raw TIFF".toByteArray()), webpChunk("XMP ", "raw XMP".toByteArray()),
            webpChunk("ICCP", "profile".toByteArray()), webpChunk("TEST", byteArrayOf(99)))
        val source = webp(listOf(vp8l(4000, 3000)) + metadata)
        val result = ImageMetadata.copy(source, webp(listOf(vp8l(2581, 1936))), ImageMetadata.Format.WEBP)
        assertEquals("VP8X", String(result, 12, 4))
        assertEquals(0x2C, result[20].toInt())
        metadata.forEach { assertTrue(result.toList().windowed(it.size).any { window -> window == it.toList() }) }
        assertEquals(result.size - 8, ByteBuffer.wrap(result, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int)
    }

    @Test fun webpAlphaFlagMatchesTheEncodedPixels() {
        val image = webp(listOf(vp8l(10, 10, true)))
        val result = ImageMetadata.copy(image, image, ImageMetadata.Format.WEBP)
        assertEquals(0x10, result[20].toInt())
    }

    @Test fun malformedContainersAndUnsupportedFormatsAreRejected() {
        assertThrows(RuntimeException::class.java) {
            ImageMetadata.checkSource(png(10, 10, emptyList()).dropLast(1).toByteArray(), ImageMetadata.Format.PNG)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ImageMetadata.checkSource(webp(listOf(vp8l(10, 10))).dropLast(1).toByteArray(), ImageMetadata.Format.WEBP)
        }
        assertThrows(ImageMetadata.Unsupported::class.java) { ImageMetadata.format("ftypheic".toByteArray()) }
    }

    private fun png(width: Int, height: Int, metadata: List<ByteArray>): ByteArray =
        byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10) +
            pngChunk("IHDR", ByteBuffer.allocate(13).putInt(width).putInt(height).put(byteArrayOf(8, 2, 0, 0, 0)).array()) +
            metadata.fold(byteArrayOf()) { a, b -> a + b } + pngChunk("IDAT", byteArrayOf(1, 2, 3)) + pngChunk("IEND", byteArrayOf())
    private fun pngChunk(type: String, data: ByteArray): ByteArray {
        val body = type.toByteArray() + data
        return ByteBuffer.allocate(4).putInt(data.size).array() + body +
            ByteBuffer.allocate(4).putInt(CRC32().apply { update(body) }.value.toInt()).array()
    }
    private fun webp(chunks: List<ByteArray>): ByteArray {
        val body = "WEBP".toByteArray() + chunks.fold(byteArrayOf()) { a, b -> a + b }
        return "RIFF".toByteArray() + le32(body.size) + body
    }
    private fun webpChunk(type: String, data: ByteArray) = type.toByteArray() + le32(data.size) + data +
        if (data.size % 2 == 1) byteArrayOf(0) else byteArrayOf()
    private fun vp8l(width: Int, height: Int, alpha: Boolean = false) = webpChunk("VP8L", byteArrayOf(0x2F) +
        le32((width - 1) or ((height - 1) shl 14) or (if (alpha) 1 shl 28 else 0)))
    private fun le32(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
}
