package com.kompact.util

import android.graphics.Bitmap
import com.radzivon.bartoshyk.avif.coder.AvifEncodingOptions
import com.radzivon.bartoshyk.avif.coder.Coder
import com.radzivon.bartoshyk.avif.coder.HeifQualityArg
import com.radzivon.bartoshyk.avif.coder.HevcEncodingOptions
import com.awxkee.jxlcoder.JxlCoder
import com.awxkee.jxlcoder.JxlCompressionOption

/**
 * Encodes a Bitmap to HEIF format using libheif (via io.github.awxkee:avif-coder 3.x).
 * HEIF uses HEVC compression; quality maps 10-100 (higher = better).
 */
internal fun encodeToHeif(bitmap: Bitmap, quality: Int): ByteArray {
    val options = HevcEncodingOptions(quality = HeifQualityArg.Quality(quality.coerceIn(10, 100)))
    return Coder().encodeHeic(bitmap, null, options)
}

/**
 * Encodes a Bitmap to HEIC format using libheif (via io.github.awxkee:avif-coder 3.x).
 * HEIC is the Apple-flavoured HEIF variant; same encoder, .heic extension.
 */
internal fun encodeToHeic(bitmap: Bitmap, quality: Int): ByteArray {
    val options = HevcEncodingOptions(quality = HeifQualityArg.Quality(quality.coerceIn(10, 100)))
    return Coder().encodeHeic(bitmap, null, options)
}

/**
 * Encodes a Bitmap to AVIF format using libavif (via io.github.awxkee:avif-coder 3.x).
 * AVIF uses AV1 compression; quality maps 10-100 (higher = better).
 */
internal fun encodeToAvif(bitmap: Bitmap, quality: Int): ByteArray {
    val options = AvifEncodingOptions(quality = quality.coerceIn(10, 100))
    return Coder().encodeAvif(bitmap, null, options)
}

/**
 * Encodes a Bitmap to JPEG XL format using libjxl (via io.github.awxkee:jxl-coder 2.6.x).
 * Quality 10-100 maps to lossy JXL; quality >= 100 uses lossless encoding.
 */
internal fun encodeToJxl(bitmap: Bitmap, quality: Int): ByteArray {
    val clamped = quality.coerceIn(10, 100)
    return if (clamped >= 100) {
        JxlCoder.encode(bitmap, compressionOption = JxlCompressionOption.LOSSLESS)
    } else {
        JxlCoder.encode(bitmap, compressionOption = JxlCompressionOption.LOSSY, quality = clamped)
    }
}
