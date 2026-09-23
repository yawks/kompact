package com.kompact.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import com.kompact.model.BackgroundConfig
import com.kompact.model.CompressionGoal
import com.kompact.model.CropBounds
import com.kompact.model.ExifData
import com.kompact.model.ImageCompressionConfig
import com.kompact.model.ImageOutputFormat
import com.kompact.model.ResizeConfig
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

private const val MAX_IMAGE_EDGE = 16384
private const val MIN_JPG_QUALITY = 10
private const val QUALITY_STEP = 5

internal fun readExifData(resolver: ContentResolver, uri: Uri): ExifData? {
    return try {
        resolver.openInputStream(uri)?.use { input ->
            val exif = ExifInterface(input)
            
            // ========================================================================
            // RADICAL GPS PARSE: Read individual GPS tags directly
            // Try multiple approaches to ensure we get GPS data from all devices
            // ========================================================================
            
            // Primary: Parse from individual GPS tags (most reliable across devices)
            var latitude = parseGpsCoordinate(
                exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE),
                exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
            )
            var longitude = parseGpsCoordinate(
                exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE),
                exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)
            )
            
            // Fallback 1: Try using the built-in latLong property (works on some devices)
            if (latitude == null || longitude == null) {
                val latLong = exif.latLong
                if (latLong != null) {
                    latitude = latLong.getOrNull(0)
                    longitude = latLong.getOrNull(1)
                }
            }
            
            // Parse altitude from TAG_GPS_ALTITUDE and TAG_GPS_ALTITUDE_REF
            var altitude: Double? = parseGpsAltitude(
                exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE),
                exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF)
            )
            
            // Fallback for altitude: Try using getAltitude() if direct parsing failed
            if (altitude == null && exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE) != null) {
                val altitudeRef = exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF)
                if (altitudeRef != null) {
                    altitude = exif.getAltitude(0.0)
                }
            }
            
            // Generate position string from GPS coordinates if available
            val position = exif.getAttribute(ExifInterface.TAG_GPS_AREA_INFORMATION)
                ?: (if (latitude != null && longitude != null) formatGpsPosition(latitude, longitude) else null)
            
            ExifData(
                dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME),
                make = exif.getAttribute(ExifInterface.TAG_MAKE),
                model = exif.getAttribute(ExifInterface.TAG_MODEL),
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL),
                latitude = latitude,
                longitude = longitude,
                altitude = altitude,
                position = position,
                exposureTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME),
                fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER),
                iso = exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, 0).takeIf { it > 0 },
                focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH),
                flash = exif.getAttributeInt(ExifInterface.TAG_FLASH, 0),
                whiteBalance = exif.getAttributeInt(ExifInterface.TAG_WHITE_BALANCE, 0),
                artist = exif.getAttribute(ExifInterface.TAG_ARTIST),
                copyright = exif.getAttribute(ExifInterface.TAG_COPYRIGHT),
                software = exif.getAttribute(ExifInterface.TAG_SOFTWARE),
                description = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION),
                userComment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT),
                // Additional camera/device metadata
                exposureProgram = exif.getAttributeIntOrNull(ExifInterface.TAG_EXPOSURE_PROGRAM),
                meteringMode = exif.getAttributeIntOrNull(ExifInterface.TAG_METERING_MODE),
                exposureMode = exif.getAttributeIntOrNull(ExifInterface.TAG_EXPOSURE_MODE),
                sceneType = exif.getAttributeIntOrNull(ExifInterface.TAG_SCENE_TYPE),
                sceneCaptureType = exif.getAttributeIntOrNull(ExifInterface.TAG_SCENE_CAPTURE_TYPE),
                digitalZoomRatio = exif.getAttribute(ExifInterface.TAG_DIGITAL_ZOOM_RATIO),
                focalLength35mm = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM),
                maxApertureValue = exif.getAttribute(ExifInterface.TAG_MAX_APERTURE_VALUE),
                subjectDistance = exif.getAttribute(ExifInterface.TAG_SUBJECT_DISTANCE),
                subjectArea = exif.getAttribute(ExifInterface.TAG_SUBJECT_AREA),
                // Image characteristics
                contrast = exif.getAttributeIntOrNull(ExifInterface.TAG_CONTRAST),
                saturation = exif.getAttributeIntOrNull(ExifInterface.TAG_SATURATION),
                sharpness = exif.getAttributeIntOrNull(ExifInterface.TAG_SHARPNESS),
                brightnessValue = exif.getAttribute(ExifInterface.TAG_BRIGHTNESS_VALUE),
                // Device info
                fileSource = exif.getAttribute(ExifInterface.TAG_FILE_SOURCE),
                customRendered = exif.getAttributeIntOrNull(ExifInterface.TAG_CUSTOM_RENDERED),
                colorSpace = exif.getAttributeIntOrNull(ExifInterface.TAG_COLOR_SPACE),
                gamma = exif.getAttribute(ExifInterface.TAG_GAMMA),
                // Additional info
                imageUniqueId = exif.getAttribute(ExifInterface.TAG_IMAGE_UNIQUE_ID),
                subsecTime = exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME),
                subsecTimeOriginal = exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL),
                subsecTimeDigitized = exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED),
            )
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Parse GPS coordinate from EXIF rational array format to decimal degrees.
 * GPS coordinates in EXIF are stored as: [degrees, minutes, seconds] as rational numbers
 * Reference indicates N/S for latitude or E/W for longitude
 * Format: "dd/ddddd,mm/mm,ss/sss" or array of rational strings
 */
private fun parseGpsCoordinate(coordinateValue: String?, referenceValue: String?): Double? {
    if (coordinateValue.isNullOrBlank() || referenceValue.isNullOrBlank()) return null
    
    return try {
        // Parse the coordinate array (format: "deg/1,min/1,sec/1" or "deg_num/deg_den,min_num/min_den,sec_num/sec_den")
        val parts = coordinateValue.split(",")
        if (parts.size != 3) return null
        
        val degrees = parseRational(parts[0]) ?: return null
        val minutes = parseRational(parts[1]) ?: return null
        val seconds = parseRational(parts[2]) ?: return null
        
        // Convert to decimal degrees: degrees + minutes/60 + seconds/3600
        val decimalDegrees = degrees + (minutes / 60.0) + (seconds / 3600.0)
        
        // Apply reference (N/S for latitude, E/W for longitude)
        val sign = when (referenceValue.uppercase()) {
            "S", "W" -> -1.0
            else -> 1.0  // "N", "E" or unknown (default to positive)
        }
        
        decimalDegrees * sign
    } catch (e: Exception) {
        null
    }
}

/**
 * Parse altitude from EXIF GPS altitude tags.
 * Altitude is stored as a rational number, with reference indicating above/below sea level.
 */
private fun parseGpsAltitude(altitudeValue: String?, altitudeRefValue: String?): Double? {
    if (altitudeValue == null) return null
    
    return try {
        val altitude = parseRational(altitudeValue)
        if (altitude == null) return null
        
        // Reference: 0 = above sea level, 1 = below sea level
        val sign = if (altitudeRefValue == "1") -1.0 else 1.0
        
        altitude * sign
    } catch (e: Exception) {
        null
    }
}

/**
 * Parse a rational number string in format "numerator/denominator" or just "number".
 * EXIF stores rational numbers as strings like "123/100" or "45/1"
 */
private fun parseRational(value: String): Double? {
    return try {
        if (!value.contains('/')) {
            // Simple integer or decimal
            value.toDoubleOrNull()
        } else {
            // Rational format: numerator/denominator
            val parts = value.split('/')
            val numerator = parts[0].toDoubleOrNull() ?: return null
            val denominator = parts[1].toDoubleOrNull() ?: return null
            if (denominator == 0.0) return null
            numerator / denominator
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Helper extension to get attribute as nullable Int
 * Returns null if the tag doesn't exist or can't be parsed
 */
private fun ExifInterface.getAttributeIntOrNull(tag: String): Int? {
    return try {
        val value = getAttributeInt(tag, Int.MIN_VALUE)
        if (value == Int.MIN_VALUE) null else value
    } catch (e: Exception) {
        null
    }
}

/**
 * Formats GPS coordinates as a human-readable position string.
 * Format: "latitude, longitude" (e.g., "45.4642, 9.1900")
 */
private fun formatGpsPosition(latitude: Double, longitude: Double): String {
    return "%1$.6f, %2$.6f".format(latitude, longitude)
}


internal val ALL_EXIF_TAGS = arrayOf(
        ExifInterface.TAG_APERTURE_VALUE,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_BITS_PER_SAMPLE,
        ExifInterface.TAG_BODY_SERIAL_NUMBER,
        ExifInterface.TAG_BRIGHTNESS_VALUE,
        ExifInterface.TAG_CAMARA_OWNER_NAME,
        ExifInterface.TAG_CAMERA_OWNER_NAME,
        ExifInterface.TAG_CFA_PATTERN,
        ExifInterface.TAG_COLOR_SPACE,
        ExifInterface.TAG_COMPONENTS_CONFIGURATION,
        ExifInterface.TAG_COMPRESSED_BITS_PER_PIXEL,
        ExifInterface.TAG_COMPRESSION,
        ExifInterface.TAG_CONTRAST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_CUSTOM_RENDERED,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DEFAULT_CROP_SIZE,
        ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION,
        ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
        ExifInterface.TAG_DNG_VERSION,
        ExifInterface.TAG_EXIF_VERSION,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_EXPOSURE_INDEX,
        ExifInterface.TAG_EXPOSURE_MODE,
        ExifInterface.TAG_EXPOSURE_PROGRAM,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_FILE_SOURCE,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_FLASHPIX_VERSION,
        ExifInterface.TAG_FLASH_ENERGY,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT,
        ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION,
        ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_GAIN_CONTROL,
        ExifInterface.TAG_GAMMA,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_AREA_INFORMATION,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_DEST_BEARING,
        ExifInterface.TAG_GPS_DEST_BEARING_REF,
        ExifInterface.TAG_GPS_DEST_DISTANCE,
        ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
        ExifInterface.TAG_GPS_DEST_LATITUDE,
        ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
        ExifInterface.TAG_GPS_DEST_LONGITUDE,
        ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
        ExifInterface.TAG_GPS_DIFFERENTIAL,
        ExifInterface.TAG_GPS_DOP,
        ExifInterface.TAG_GPS_H_POSITIONING_ERROR,
        ExifInterface.TAG_GPS_IMG_DIRECTION,
        ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_MAP_DATUM,
        ExifInterface.TAG_GPS_MEASURE_MODE,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_SATELLITES,
        ExifInterface.TAG_GPS_SPEED,
        ExifInterface.TAG_GPS_SPEED_REF,
        ExifInterface.TAG_GPS_STATUS,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_TRACK,
        ExifInterface.TAG_GPS_TRACK_REF,
        ExifInterface.TAG_GPS_VERSION_ID,
        ExifInterface.TAG_IMAGE_DESCRIPTION,
        ExifInterface.TAG_IMAGE_LENGTH,
        ExifInterface.TAG_IMAGE_UNIQUE_ID,
        ExifInterface.TAG_IMAGE_WIDTH,
        ExifInterface.TAG_INTEROPERABILITY_INDEX,
        ExifInterface.TAG_ISO_SPEED,
        ExifInterface.TAG_ISO_SPEED_LATITUDE_YYY,
        ExifInterface.TAG_ISO_SPEED_LATITUDE_ZZZ,
        ExifInterface.TAG_ISO_SPEED_RATINGS,
        ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT,
        ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_LENS_SERIAL_NUMBER,
        ExifInterface.TAG_LENS_SPECIFICATION,
        ExifInterface.TAG_LIGHT_SOURCE,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MAKER_NOTE,
        ExifInterface.TAG_MAX_APERTURE_VALUE,
        ExifInterface.TAG_METERING_MODE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_NEW_SUBFILE_TYPE,
        ExifInterface.TAG_OECF,
        ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_ORF_ASPECT_FRAME,
        ExifInterface.TAG_ORF_PREVIEW_IMAGE_LENGTH,
        ExifInterface.TAG_ORF_PREVIEW_IMAGE_START,
        ExifInterface.TAG_ORF_THUMBNAIL_IMAGE,
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_PHOTOMETRIC_INTERPRETATION,
        ExifInterface.TAG_PIXEL_X_DIMENSION,
        ExifInterface.TAG_PIXEL_Y_DIMENSION,
        ExifInterface.TAG_PLANAR_CONFIGURATION,
        ExifInterface.TAG_PRIMARY_CHROMATICITIES,
        ExifInterface.TAG_RECOMMENDED_EXPOSURE_INDEX,
        ExifInterface.TAG_REFERENCE_BLACK_WHITE,
        ExifInterface.TAG_RELATED_SOUND_FILE,
        ExifInterface.TAG_RESOLUTION_UNIT,
        ExifInterface.TAG_ROWS_PER_STRIP,
        ExifInterface.TAG_RW2_ISO,
        ExifInterface.TAG_RW2_JPG_FROM_RAW,
        ExifInterface.TAG_RW2_SENSOR_BOTTOM_BORDER,
        ExifInterface.TAG_RW2_SENSOR_LEFT_BORDER,
        ExifInterface.TAG_RW2_SENSOR_RIGHT_BORDER,
        ExifInterface.TAG_RW2_SENSOR_TOP_BORDER,
        ExifInterface.TAG_SAMPLES_PER_PIXEL,
        ExifInterface.TAG_SATURATION,
        ExifInterface.TAG_SCENE_CAPTURE_TYPE,
        ExifInterface.TAG_SCENE_TYPE,
        ExifInterface.TAG_SENSING_METHOD,
        ExifInterface.TAG_SENSITIVITY_TYPE,
        ExifInterface.TAG_SHARPNESS,
        ExifInterface.TAG_SHUTTER_SPEED_VALUE,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_SPATIAL_FREQUENCY_RESPONSE,
        ExifInterface.TAG_SPECTRAL_SENSITIVITY,
        ExifInterface.TAG_STANDARD_OUTPUT_SENSITIVITY,
        ExifInterface.TAG_STRIP_BYTE_COUNTS,
        ExifInterface.TAG_STRIP_OFFSETS,
        ExifInterface.TAG_SUBFILE_TYPE,
        ExifInterface.TAG_SUBJECT_AREA,
        ExifInterface.TAG_SUBJECT_DISTANCE,
        ExifInterface.TAG_SUBJECT_DISTANCE_RANGE,
        ExifInterface.TAG_SUBJECT_LOCATION,
        ExifInterface.TAG_SUBSEC_TIME,
        ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_THUMBNAIL_IMAGE_LENGTH,
        ExifInterface.TAG_THUMBNAIL_IMAGE_WIDTH,
        ExifInterface.TAG_TRANSFER_FUNCTION,
        ExifInterface.TAG_USER_COMMENT,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_WHITE_POINT,
        ExifInterface.TAG_XMP,
        ExifInterface.TAG_X_RESOLUTION,
        ExifInterface.TAG_Y_CB_CR_COEFFICIENTS,
        ExifInterface.TAG_Y_CB_CR_POSITIONING,
        ExifInterface.TAG_Y_CB_CR_SUB_SAMPLING,
        ExifInterface.TAG_Y_RESOLUTION
)

internal fun writeExifData(bytes: ByteArray, exifData: ExifData?, resolver: ContentResolver? = null, originalUri: Uri? = null): ByteArray {
    return try {
        val tempFile = java.io.File.createTempFile("exif", ".tmp")
        tempFile.writeBytes(bytes)
        val exif = ExifInterface(tempFile.absolutePath)
        
        if (exifData != null && originalUri != null && resolver != null) {
            // First, copy all original attributes
            try {
                resolver.openInputStream(originalUri)?.use { input ->
                    val originalExif = ExifInterface(input)
                    ALL_EXIF_TAGS.forEach { tag ->
                        val value = originalExif.getAttribute(tag)
                        if (value != null) {
                            exif.setAttribute(tag, value)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore parsing errors for original exif
            }
        }
        
        if (exifData == null) {
            // User explicitly requested to strip EXIF data
            ALL_EXIF_TAGS.forEach { tag ->
                exif.setAttribute(tag, null)
            }
        } else {
            // Apply customized values on top
            exif.setAttribute(ExifInterface.TAG_DATETIME, exifData.dateTime)
            exif.setAttribute(ExifInterface.TAG_MAKE, exifData.make)
            exif.setAttribute(ExifInterface.TAG_MODEL, exifData.model)
            // The pixels were re-encoded from a bitmap already normalized to upright
            // orientation (see decodeBitmap), so the output must not rotate again.
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            
            // Handle GPS coordinates - write both using setLatLong for compatibility
            // and also set individual tags for completeness
            if (exifData.latitude != null && exifData.longitude != null) {
                exif.setLatLong(exifData.latitude, exifData.longitude)
                // Also write the position string if coordinates are available
                if (exifData.position.isNullOrBlank()) {
                    exif.setAttribute(ExifInterface.TAG_GPS_AREA_INFORMATION, 
                        formatGpsPosition(exifData.latitude, exifData.longitude))
                } else {
                    exif.setAttribute(ExifInterface.TAG_GPS_AREA_INFORMATION, exifData.position)
                }
            } else {
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null)
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null)
                exif.setAttribute(ExifInterface.TAG_GPS_AREA_INFORMATION, null)
            }
            
            // Handle altitude - set it if provided, otherwise clear GPS altitude tags
            if (exifData.altitude != null) {
                exif.setAltitude(exifData.altitude)
                // Ensure altitude reference is set (0 = above sea level, 1 = below sea level)
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, 
                    if (exifData.altitude >= 0) "0" else "1")
            } else {
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)
                exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, null)
            }
            
            exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, exifData.exposureTime)
            exif.setAttribute(ExifInterface.TAG_F_NUMBER, exifData.fNumber)
            exif.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, exifData.iso?.toString())
            exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH, exifData.focalLength)
            exif.setAttribute(ExifInterface.TAG_FLASH, exifData.flash?.toString())
            exif.setAttribute(ExifInterface.TAG_WHITE_BALANCE, exifData.whiteBalance?.toString())
            exif.setAttribute(ExifInterface.TAG_ARTIST, exifData.artist)
            exif.setAttribute(ExifInterface.TAG_COPYRIGHT, exifData.copyright)
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, exifData.software)
            exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, exifData.description)
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, exifData.userComment)
            
            // Additional camera/device metadata
            exifData.exposureProgram?.let { exif.setAttribute(ExifInterface.TAG_EXPOSURE_PROGRAM, it.toString()) }
            exifData.meteringMode?.let { exif.setAttribute(ExifInterface.TAG_METERING_MODE, it.toString()) }
            exifData.exposureMode?.let { exif.setAttribute(ExifInterface.TAG_EXPOSURE_MODE, it.toString()) }
            exifData.sceneType?.let { exif.setAttribute(ExifInterface.TAG_SCENE_TYPE, it.toString()) }
            exifData.sceneCaptureType?.let { exif.setAttribute(ExifInterface.TAG_SCENE_CAPTURE_TYPE, it.toString()) }
            exif.setAttribute(ExifInterface.TAG_DIGITAL_ZOOM_RATIO, exifData.digitalZoomRatio)
            exif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, exifData.focalLength35mm)
            exif.setAttribute(ExifInterface.TAG_MAX_APERTURE_VALUE, exifData.maxApertureValue)
            exif.setAttribute(ExifInterface.TAG_SUBJECT_DISTANCE, exifData.subjectDistance)
            exif.setAttribute(ExifInterface.TAG_SUBJECT_AREA, exifData.subjectArea)
            
            // Image characteristics
            exifData.contrast?.let { exif.setAttribute(ExifInterface.TAG_CONTRAST, it.toString()) }
            exifData.saturation?.let { exif.setAttribute(ExifInterface.TAG_SATURATION, it.toString()) }
            exifData.sharpness?.let { exif.setAttribute(ExifInterface.TAG_SHARPNESS, it.toString()) }
            exif.setAttribute(ExifInterface.TAG_BRIGHTNESS_VALUE, exifData.brightnessValue)
            
            // Device info
            exif.setAttribute(ExifInterface.TAG_FILE_SOURCE, exifData.fileSource)
            exifData.customRendered?.let { exif.setAttribute(ExifInterface.TAG_CUSTOM_RENDERED, it.toString()) }
            exifData.colorSpace?.let { exif.setAttribute(ExifInterface.TAG_COLOR_SPACE, it.toString()) }
            exif.setAttribute(ExifInterface.TAG_GAMMA, exifData.gamma)
            
            // Additional info
            exif.setAttribute(ExifInterface.TAG_IMAGE_UNIQUE_ID, exifData.imageUniqueId)
            exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME, exifData.subsecTime)
            exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, exifData.subsecTimeOriginal)
            exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED, exifData.subsecTimeDigitized)
            
            // Ensure position is written even if it was set directly
            if (!exifData.position.isNullOrBlank()) {
                exif.setAttribute(ExifInterface.TAG_GPS_AREA_INFORMATION, exifData.position)
            }
        }
        exif.saveAttributes()
        tempFile.readBytes().also { tempFile.delete() }
    } catch (e: Exception) {
        bytes
    }
}

internal fun decodeBitmap(resolver: ContentResolver, uri: Uri, maxEdge: Int = MAX_IMAGE_EDGE): Bitmap? {
    val boundsOptions = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        inScaled = false
    }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOptions) }
    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = calculateSampleSize(boundsOptions, maxEdge, maxEdge)
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inScaled = false
    }
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
        ?: return null
    return applyExifOrientation(decoded, resolver, uri)
}

/**
 * BitmapFactory ignores the EXIF orientation tag, while image loaders used by the
 * editors (Coil) display the upright image. Rotate/flip the decoded bitmap so the
 * processing pipeline works in the same coordinate space the user saw while editing.
 */
private fun applyExifOrientation(bitmap: Bitmap, resolver: ContentResolver, uri: Uri): Bitmap {
    val orientation = try {
        resolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    val matrix = android.graphics.Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        else -> return bitmap
    }

    val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (oriented != bitmap) {
        bitmap.recycle()
    }
    return oriented
}

internal fun encodeBitmapWithTarget(
    bitmap: Bitmap,
    outputFormat: ImageOutputFormat,
    requestedQuality: Int,
    targetBytes: Long?,
): Pair<ByteArray, Int> {
    val normalizedQuality = (100 - requestedQuality).coerceIn(10, 100)
    return when (outputFormat) {
        ImageOutputFormat.BMP -> encodeToBmp(bitmap) to 100 // BMP is lossless
        ImageOutputFormat.GIF -> encodeToPng(bitmap, 100) to 100 // For now, save as PNG with gif extension
        ImageOutputFormat.TIF -> encodeToPng(bitmap, 100) to 100 // Same
        ImageOutputFormat.AVIF -> encodeToWebp(bitmap, normalizedQuality) to normalizedQuality // AVIF not supported, use WEBP
        ImageOutputFormat.JXL -> encodeToPng(bitmap, 100) to 100 // JXL not supported, use PNG
        ImageOutputFormat.ICO -> encodeToIco(bitmap) to 100 // ICO is lossless (PNG inside)
        ImageOutputFormat.TELEGRAM_PNG -> encodeToPng(bitmap, normalizedQuality) to normalizedQuality // PNG optimized
        else -> {
            val compressFormat = when (outputFormat) {
                ImageOutputFormat.JPG -> Bitmap.CompressFormat.JPEG
                ImageOutputFormat.PNG -> Bitmap.CompressFormat.PNG
                ImageOutputFormat.WEBP -> webpFormat()
                ImageOutputFormat.HEIF, ImageOutputFormat.HEIC -> Bitmap.CompressFormat.JPEG // HEIF not supported in CompressFormat, use JPG
                else -> Bitmap.CompressFormat.JPEG // fallback
            }
            if (targetBytes == null || compressFormat == Bitmap.CompressFormat.PNG) {
                val stream = ByteArrayOutputStream()
                bitmap.compress(compressFormat, normalizedQuality, stream)
                stream.toByteArray() to normalizedQuality
            } else {
                val stream = ByteArrayOutputStream()
                var currentQuality = normalizedQuality
                while (currentQuality >= MIN_JPG_QUALITY) {
                    stream.reset()
                    bitmap.compress(compressFormat, currentQuality, stream)
                    if (stream.size().toLong() <= targetBytes || currentQuality <= MIN_JPG_QUALITY) {
                        break
                    }
                    currentQuality -= QUALITY_STEP
                }
                stream.toByteArray() to currentQuality.coerceAtLeast(MIN_JPG_QUALITY)
            }
        }
    }
}

internal fun Bitmap.toJpg(quality: Int): ByteArray {
    val stream = ByteArrayOutputStream()
    this.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(10, 100), stream)
    return stream.toByteArray()
}

internal fun encodeToPng(bitmap: Bitmap, quality: Int): ByteArray {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, quality, stream)
    return stream.toByteArray()
}

internal fun encodeToWebp(bitmap: Bitmap, quality: Int): ByteArray {
    val stream = ByteArrayOutputStream()
    bitmap.compress(webpFormat(), quality, stream)
    return stream.toByteArray()
}

internal fun encodeToBmp(bitmap: Bitmap): ByteArray {
    // Simple BMP encoder for 24-bit RGB
    val width = bitmap.width
    val height = bitmap.height
    val rowSize = (width * 3 + 3) and -4 // padded to 4 bytes
    val pixelDataSize = rowSize * height
    val fileSize = 54 + pixelDataSize // header + data

    val buffer = ByteArray(fileSize)
    var offset = 0

    // BITMAPFILEHEADER
    buffer[offset++] = 'B'.code.toByte()
    buffer[offset++] = 'M'.code.toByte()
    // file size
    for (i in 0..3) buffer[offset++] = (fileSize shr (i * 8)).toByte()
    // reserved
    for (i in 0..3) buffer[offset++] = 0
    // data offset
    val dataOffset = 54
    for (i in 0..3) buffer[offset++] = (dataOffset shr (i * 8)).toByte()

    // BITMAPINFOHEADER
    val headerSize = 40
    for (i in 0..3) buffer[offset++] = (headerSize shr (i * 8)).toByte()
    // width
    for (i in 0..3) buffer[offset++] = (width shr (i * 8)).toByte()
    // height
    for (i in 0..3) buffer[offset++] = (height shr (i * 8)).toByte()
    // planes
    buffer[offset++] = 1
    buffer[offset++] = 0
    // bits per pixel
    buffer[offset++] = 24
    buffer[offset++] = 0
    // compression
    for (i in 0..3) buffer[offset++] = 0
    // image size
    for (i in 0..3) buffer[offset++] = (pixelDataSize shr (i * 8)).toByte()
    // x pixels per meter
    for (i in 0..3) buffer[offset++] = 0
    // y pixels per meter
    for (i in 0..3) buffer[offset++] = 0
    // colors used
    for (i in 0..3) buffer[offset++] = 0
    // important colors
    for (i in 0..3) buffer[offset++] = 0

    // Pixel data, bottom to top
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    for (y in height - 1 downTo 0) {
        for (x in 0 until width) {
            val pixel = pixels[y * width + x]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            buffer[offset++] = b.toByte()
            buffer[offset++] = g.toByte()
            buffer[offset++] = r.toByte()
        }
        // padding
        val padding = rowSize - width * 3
        for (i in 0 until padding) buffer[offset++] = 0
    }

    return buffer
}

internal fun encodeToIco(bitmap: Bitmap): ByteArray {
    // ICO encoder that creates a standard Windows ICO file
    // ICO format: header + icon entries + image data
    // For simplicity, we store the image as a PNG inside the ICO container
    // This provides better quality and transparency support
    
    val width = bitmap.width
    val height = bitmap.height
    
    // First, encode the bitmap as PNG
    val pngData = encodeToPng(bitmap, 100)
    
    // ICO header: 6 bytes
    // 0-1: Reserved (0)
    // 2-3: Image type (1 for icon)
    // 4-5: Number of images (1)
    val headerSize = 6
    
    // Icon entry: 16 bytes per image
    // 0: Image width (1-256, 0 means 256)
    // 1: Image height (1-256, 0 means 256)
    // 2: Number of colors in palette (0 if >= 256 colors)
    // 3: Reserved (0)
    // 4-5: Color planes (1 for PNG)
    // 6-7: Bits per pixel (0 for PNG)
    // 8-11: Image data size in bytes
    // 12-15: Offset of image data from beginning of file
    val entrySize = 16
    
    val imageDataSize = pngData.size
    val offsetToImageData = headerSize + entrySize
    
    val icoData = ByteArray(headerSize + entrySize + imageDataSize)
    var offset = 0
    
    // Write ICO header
    icoData[offset++] = 0 // Reserved
    icoData[offset++] = 0 // Reserved
    icoData[offset++] = 1 // Image type: 1 = icon
    icoData[offset++] = 0 // Image type: high byte
    icoData[offset++] = 1 // Number of images: 1
    icoData[offset++] = 0 // Number of images: high byte
    
    // Write icon entry
    // Width and height (must be <= 255 for standard ICO)
    val icoWidth = if (width > 255) 255 else width
    val icoHeight = if (height > 255) 255 else height
    icoData[offset++] = icoWidth.toByte() // Width
    icoData[offset++] = icoHeight.toByte() // Height
    icoData[offset++] = 0 // Number of colors (0 = 256+ colors)
    icoData[offset++] = 0 // Reserved
    icoData[offset++] = 0 // Color planes (low byte)
    icoData[offset++] = 1 // Color planes (high byte) - 1 plane
    icoData[offset++] = 0 // Bits per pixel (low byte)
    icoData[offset++] = 32 // Bits per pixel (high byte) - 32 for PNG in ICO
    
    // Image data size (4 bytes, little-endian)
    for (i in 0..3) {
        icoData[offset++] = (imageDataSize shr (i * 8)).toByte()
    }
    
    // Offset to image data (4 bytes, little-endian)
    for (i in 0..3) {
        icoData[offset++] = (offsetToImageData shr (i * 8)).toByte()
    }
    
    // Write PNG data
    System.arraycopy(pngData, 0, icoData, offset, pngData.size)
    
    return icoData
}

internal fun resolveImageExtension(
    context: Context,
    resolver: ContentResolver,
    uri: Uri,
    format: ImageOutputFormat,
): String {
    return when (format) {
        ImageOutputFormat.ORIGINAL -> inferOriginalExtension(context, resolver, uri)
        ImageOutputFormat.JPG -> "jpg"
        ImageOutputFormat.PNG -> "png"
        ImageOutputFormat.WEBP -> "webp"
        ImageOutputFormat.BMP -> "bmp"
        ImageOutputFormat.GIF -> "gif"
        ImageOutputFormat.TIF -> "tif"
        ImageOutputFormat.HEIF -> "heif"
        ImageOutputFormat.HEIC -> "heic"
        ImageOutputFormat.AVIF -> "avif"
        ImageOutputFormat.JXL -> "jxl"
        ImageOutputFormat.ICO -> "ico"
        ImageOutputFormat.TELEGRAM_PNG -> "png"
    }
}

private fun inferOriginalExtension(
    context: Context,
    resolver: ContentResolver,
    uri: Uri,
): String {
    val mime = DocumentFile.fromSingleUri(context, uri)?.type ?: resolver.getType(uri)
    return when {
        mime?.contains("png", ignoreCase = true) == true -> "png"
        mime?.contains("webp", ignoreCase = true) == true -> "webp"
        mime?.contains("bmp", ignoreCase = true) == true -> "bmp"
        mime?.contains("gif", ignoreCase = true) == true -> "gif"
        mime?.contains("tiff", ignoreCase = true) == true -> "tif"
        mime?.contains("heif", ignoreCase = true) == true -> "heif"
        mime?.contains("heic", ignoreCase = true) == true -> "heic"
        mime?.contains("avif", ignoreCase = true) == true -> "avif"
        mime?.contains("jxl", ignoreCase = true) == true -> "jxl"
        mime?.contains("ico", ignoreCase = true) == true -> "ico"
        mime?.contains("x-icon", ignoreCase = true) == true -> "ico"
        else -> "jpg"
    }
}

internal fun webpFormat(): Bitmap.CompressFormat {
    return if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
}

internal fun calculateSampleSize(options: BitmapFactory.Options, maxWidth: Int, maxHeight: Int): Int {
    var inSampleSize = 1
    val height = options.outHeight
    val width = options.outWidth
    if (height <= 0 || width <= 0) return inSampleSize
    while ((height / inSampleSize) > maxHeight || (width / inSampleSize) > maxWidth) {
        inSampleSize *= 2
    }
    return inSampleSize
}

internal fun ContentResolver.getFileSize(context: Context, uri: Uri): Long {
    val docLength = DocumentFile.fromSingleUri(context, uri)?.length()
    if (docLength != null && docLength >= 0) return docLength
    val projection = arrayOf(android.provider.OpenableColumns.SIZE)
    return query(uri, projection, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
        if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else 0L
    } ?: 0L
}

internal fun supportsExif(format: ImageOutputFormat): Boolean {
    return when (format) {
        ImageOutputFormat.JPG, ImageOutputFormat.PNG, ImageOutputFormat.WEBP, ImageOutputFormat.HEIF, ImageOutputFormat.HEIC, ImageOutputFormat.TIF, ImageOutputFormat.ORIGINAL -> true
        else -> false
    }
}

internal fun resolveTargetBytes(config: ImageCompressionConfig, originalSize: Long): Long? {
    return when (config.goal) {
        CompressionGoal.TARGET_SIZE -> config.targetSizeBytes
        CompressionGoal.PERCENTAGE -> {
            if (config.percentage == 0) null
            else (originalSize * (100 - config.percentage) / 100.0).toLong().coerceAtLeast(0L)
        }
    }
}

internal fun Bitmap.applyCrop(bounds: CropBounds?): Bitmap {
    if (bounds == null || bounds.isFull()) return this
    if (width <= 0 || height <= 0) return this
    val normalized = bounds.normalized()
    val leftPx = (normalized.left * width).roundToInt().coerceIn(0, width - 1)
    val topPx = (normalized.top * height).roundToInt().coerceIn(0, height - 1)
    val rightPx = (normalized.right * width).roundToInt().coerceIn(leftPx + 1, width)
    val bottomPx = (normalized.bottom * height).roundToInt().coerceIn(topPx + 1, height)
    val cropWidth = (rightPx - leftPx).coerceAtLeast(1)
    val cropHeight = (bottomPx - topPx).coerceAtLeast(1)
    val cropped = Bitmap.createBitmap(this, leftPx, topPx, cropWidth, cropHeight)
    if (cropped != this) {
        this.recycle()
    }
    return cropped
}

internal fun Bitmap.applyResize(config: ResizeConfig?): Bitmap {
    if (config == null || !config.isValid()) return this
    if (config.width == width && config.height == height && !config.isPercentage) return this

    var newWidth = config.width
    var newHeight = config.height
    
    if (config.isPercentage) {
        val pct = config.width / 100f
        newWidth = (width * pct).toInt()
        newHeight = (height * pct).toInt()
    } else if (config.maintainAspectRatio) {
         val aspectRatio = width.toFloat() / height.toFloat()
         // if width changed and height changed, standard logic handles based on which changed, let's just use width as primary if both provided, or whatever simplistic logic. For simplicity, just use newWidth and calc height
         if (newWidth > 0 && newHeight > 0) {
              // let's just force aspect ratio on width usually
              newHeight = (newWidth / aspectRatio).toInt()
         }
    }

    if (newWidth <= 0) newWidth = 1
    if (newHeight <= 0) newHeight = 1
    
    return Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
}

internal fun Bitmap.applyBackground(config: BackgroundConfig?): Bitmap {
    if (config == null || config.type == com.kompact.model.BackgroundType.TRANSPARENT) return this

    val newBitmap = Bitmap.createBitmap(width, height, this.config ?: Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(newBitmap)
    canvas.drawColor(config.customColor)
    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(this, 0f, 0f, paint)
    
    return newBitmap
}

internal fun Bitmap.applyRotation(degrees: Float): Bitmap {
    if (degrees % 360f == 0f) return this
    val matrix = android.graphics.Matrix()
    matrix.postRotate(degrees)
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

internal fun Bitmap.applyColorFilter(config: com.kompact.model.ColorFilterConfig?): Bitmap {
    if (config == null || config.filterType == com.kompact.model.ColorFilterType.NONE) return this

    val colorMatrix = config.toAndroidColorMatrix() ?: return this

    val paint = android.graphics.Paint()
    paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)

    val newBitmap = Bitmap.createBitmap(width, height, this.config ?: Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(newBitmap)
    canvas.drawBitmap(this, 0f, 0f, paint)
    return newBitmap
}
internal fun android.graphics.Bitmap.applyTextOverlay(configs: List<com.kompact.model.TextOverlayConfig>?): android.graphics.Bitmap {
    if (configs.isNullOrEmpty()) return this
    return configs.fold(this) { acc, config -> acc.applySingleTextOverlay(config) }
}

private fun android.graphics.Bitmap.applySingleTextOverlay(config: com.kompact.model.TextOverlayConfig): android.graphics.Bitmap {
    if (config.text.isBlank()) return this

    val workingBitmap = if (this.isMutable && this.config == android.graphics.Bitmap.Config.ARGB_8888) this else this.copy(android.graphics.Bitmap.Config.ARGB_8888, true) ?: return this
    val canvas = android.graphics.Canvas(workingBitmap)
    val width = workingBitmap.width
    val height = workingBitmap.height
    val maxEdge = maxOf(width, height).toFloat()
    val paint = android.graphics.Paint().apply {
        color = config.color
        // In the editor the font height is a fraction of its square canvas, and that
        // square maps to the image's longest edge. Fall back to legacy sizing when the
        // fraction was not recorded.
        textSize = if (config.sizeFraction > 0f) {
            config.sizeFraction * config.scale * maxEdge
        } else {
            config.size * config.scale * (width / 1080f).coerceAtLeast(1f)
        }
        isAntiAlias = true
        style = android.graphics.Paint.Style.FILL
        textAlign = android.graphics.Paint.Align.CENTER
        setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
    }

    // The editor lays the text out over a square canvas with the image fitted inside
    // (ContentScale.Fit, letterboxed and centered). Map the square-space offsets to
    // image-space coordinates and keep the anchor inside the bitmap so the text is
    // never drawn completely outside the output image.
    val x = (config.offsetX * maxEdge - (maxEdge - width) / 2f).coerceIn(0f, width.toFloat())
    val y = (config.offsetY * maxEdge - (maxEdge - height) / 2f).coerceIn(0f, height.toFloat())

    canvas.save()
    canvas.rotate(config.rotation, x, y)

    // Draw multiline text, centered on the anchor like the editor renders it
    val lines = config.text.split('\n')

    val spacing = paint.descent() - paint.ascent()
    val blockTop = y - lines.size * spacing / 2f
    lines.forEachIndexed { index, line ->
        canvas.drawText(line, x, blockTop + index * spacing - paint.ascent(), paint)
    }

    canvas.restore()

    return workingBitmap
}

internal fun android.graphics.Bitmap.applyDraw(config: com.kompact.model.DrawConfig?): android.graphics.Bitmap {
    if (config == null || config.strokes.isEmpty()) return this

    val workingBitmap = if (this.isMutable && this.config == android.graphics.Bitmap.Config.ARGB_8888) {
        this
    } else {
        this.copy(android.graphics.Bitmap.Config.ARGB_8888, true) ?: return this
    }
    
    val canvas = android.graphics.Canvas(workingBitmap)
    // Lato maggiore dell'immagine processata: come nel Draw editor, lo spessore è
    // proporzionale a questa dimensione così l'output riproduce le proporzioni viste
    val maxEdge = maxOf(workingBitmap.width, workingBitmap.height).toFloat()
    
    for (stroke in config.strokes) {
        val paint = android.graphics.Paint().apply {
            color = stroke.color
            strokeWidth = if (stroke.widthFraction > 0f) {
                stroke.widthFraction * maxEdge
            } else {
                stroke.strokeWidth
            }
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            alpha = (stroke.opacity * 255).toInt()
            isAntiAlias = true
            isFilterBitmap = true
        }
        
        if (stroke.points.size < 2) continue
        
        // Points are stored as normalized coordinates (0-1 range)
        // Scale them to the bitmap dimensions
        val path = android.graphics.Path()
        path.moveTo(stroke.points[0].x * workingBitmap.width, stroke.points[0].y * workingBitmap.height)
        
        for (i in 1 until stroke.points.size) {
            path.lineTo(stroke.points[i].x * workingBitmap.width, stroke.points[i].y * workingBitmap.height)
        }
        
        canvas.drawPath(path, paint)
    }
    
    return workingBitmap
}

/**
 * Apply image adjustments (brightness, contrast, saturation, warmth, shadows, highlights)
 * All values are percentages: -100 to 100 where 0 = no change
 */
internal fun android.graphics.Bitmap.applyAdjust(config: com.kompact.model.AdjustConfig?): android.graphics.Bitmap {
    if (config == null) return this
    
    // If all adjustments are 0, return unchanged
    if (config.brightness == 0f && config.contrast == 0f && 
        config.saturation == 0f && config.warmth == 0f &&
        config.shadows == 0f && config.highlights == 0f) {
        return this
    }
    
    val workingBitmap = if (this.isMutable && this.config == android.graphics.Bitmap.Config.ARGB_8888) {
        this
    } else {
        this.copy(android.graphics.Bitmap.Config.ARGB_8888, true) ?: return this
    }
    
    val canvas = android.graphics.Canvas(workingBitmap)
    val paint = android.graphics.Paint()
    
    // Create color matrix for adjustments
    val colorMatrix = android.graphics.ColorMatrix()
    
    // Apply saturation
    if (config.saturation != 0f) {
        val saturation = 1f + (config.saturation / 100f)
        colorMatrix.setSaturation(saturation.coerceIn(0f, 2f))
    }
    
    // Apply contrast
    if (config.contrast != 0f) {
        val contrast = 1f + (config.contrast / 100f)
        val contrastValue = contrast.coerceIn(0f, 2f)
        val contrastMatrix = android.graphics.ColorMatrix(floatArrayOf(
            contrastValue, 0f, 0f, 0f, (0.5f - contrastValue * 0.5f) * 255f,
            0f, contrastValue, 0f, 0f, (0.5f - contrastValue * 0.5f) * 255f,
            0f, 0f, contrastValue, 0f, (0.5f - contrastValue * 0.5f) * 255f,
            0f, 0f, 0f, 1f, 0f
        ))
        colorMatrix.postConcat(contrastMatrix)
    }
    
    // Apply brightness
    if (config.brightness != 0f) {
        val brightness = config.brightness / 100f
        val brightnessMatrix = android.graphics.ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, brightness * 255f,
            0f, 1f, 0f, 0f, brightness * 255f,
            0f, 0f, 1f, 0f, brightness * 255f,
            0f, 0f, 0f, 1f, 0f
        ))
        colorMatrix.postConcat(brightnessMatrix)
    }
    
    // Apply warmth (blue to yellow)
    if (config.warmth != 0f) {
        val warmth = config.warmth / 100f
        val warmthValue = 1f + warmth * 0.2f  // More subtle effect
        val warmthMatrix = android.graphics.ColorMatrix(floatArrayOf(
            warmthValue, 0f, 0f, 0f, 0f,
            0f, warmthValue.coerceIn(0.8f, 1.2f), 0f, 0f, 0f,
            0f, 0f, 1f / warmthValue.coerceIn(0.8f, 1.2f), 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
        colorMatrix.postConcat(warmthMatrix)
    }
    
    // Apply shadows and highlights using curves (simplified with contrast adjustments)
    if (config.shadows != 0f || config.highlights != 0f) {
        val shadows = config.shadows / 100f
        val highlights = config.highlights / 100f
        
        // Simple shadow/highlight adjustment via RGB curves
        // This is a simplified approximation
        val shadowHighlightMatrix = android.graphics.ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, shadows * 255f,
            0f, 1f, 0f, 0f, shadows * 255f,
            0f, 0f, 1f, 0f, shadows * 255f,
            0f, 0f, 0f, 1f, 0f
        ))
        colorMatrix.postConcat(shadowHighlightMatrix)
    }
    
    paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
    canvas.drawBitmap(workingBitmap, 0f, 0f, paint)
    
    return workingBitmap
}
