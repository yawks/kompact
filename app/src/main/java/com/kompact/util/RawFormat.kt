package com.kompact.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayInputStream

/**
 * Formati Raw riconosciuti come input. La decodifica avviene tramite il
 * JPEG embedded (preview a piena risoluzione) che la fotocamera scrive
 * dentro il file Raw: è un approccio best-effort senza librerie native,
 * ma copre la grande maggioranza dei formati (CR2, CR3, NEF, ARW, DNG,
 * ORF, RW2, PEF, SR2, RAF, ecc.).
 */
val RAW_EXTENSIONS: Set<String> = setOf(
    // Adobe
    "dng",
    // Canon
    "cr2", "cr3", "crw",
    // Nikon
    "nef", "nrw",
    // Sony
    "arw", "srf", "sr2",
    // Olympus / OM System
    "orf",
    // Panasonic / Leica
    "rw2", "raw",
    // Fujifilm
    "raf",
    // Pentax / Ricoh
    "pef", "ptx",
    // Samsung
    "srw",
    // Sigma
    "x3f",
    // Hasselblad
    "3fr", "fff",
    // Epson
    "erf",
    // Leaf / Mamiya
    "mos", "mef",
    // Minolta / Konica
    "mrw",
    // Kodak
    "kdc", "dcr",
    // Phase One / Leaf
    "iiq",
    // GoPro / altri
    "gpr",
)

val RAW_MIME_TYPES: Set<String> = setOf(
    "image/x-adobe-dng", "image/x-dng", "image/dng",
    "image/x-canon-cr2", "image/x-canon-crw", "image/x-canon-cr3",
    "image/x-nikon-nef", "image/x-nikon-nrw",
    "image/x-sony-arw", "image/x-sony-srf", "image/x-sony-sr2",
    "image/x-olympus-orf",
    "image/x-panasonic-rw2", "image/x-panasonic-raw",
    "image/x-fuji-raf",
    "image/x-pentax-pef", "image/x-pentax-ptx",
    "image/x-samsung-srw",
    "image/x-sigma-x3f",
    "image/x-hasselblad-3fr", "image/x-hasselblad-fff",
    "image/x-epson-erf",
    "image/x-leaf-mos", "image/x-mamiya-mef",
    "image/x-minolta-mrw",
    "image/x-kodak-kdc", "image/x-kodak-dcr",
    "image/x-phaseone-iiq",
    "image/x-raw", "application/x-raw",
)

fun isRawExtension(extension: String): Boolean =
    extension.lowercase() in RAW_EXTENSIONS

fun isRawMime(mimeType: String?): Boolean =
    mimeType?.lowercase()?.let { it in RAW_MIME_TYPES } == true

fun isRawFileName(fileName: String): Boolean =
    isRawExtension(fileName.substringAfterLast('.', ""))

/**
 * True se l'URI punta a un file Raw, basandosi sull'estensione del display
 * name o sul MIME type (via ContentResolver).
 */
fun isRawFile(resolver: ContentResolver, uri: Uri, displayName: String? = null, mimeType: String? = null): Boolean {
    displayName?.let { if (isRawFileName(it)) return true }
    mimeType?.let { if (isRawMime(it)) return true }
    val type = runCatching { resolver.getType(uri) }.getOrNull()
    if (isRawMime(type)) return true
    // Fallback: prova a indovinare dall'ultima parte dell'URI
    val lastSegment = uri.lastPathSegment
    if (lastSegment != null) {
        val name = lastSegment.substringAfterLast('/').substringBefore('?')
        if (isRawFileName(name)) return true
    }
    // Ultimo fallback: magic bytes (per content URI SAF con document ID opaco)
    return hasRawMagicBytes(resolver, uri)
}

/**
 * Controlla i magic bytes dei formati Raw più comuni. Usato come fallback
 * quando MIME type e nome file non sono conclusivi (es. content URI SAF con
 * document ID opaco e provider che restituisce application/octet-stream).
 * È volutamente conservativo: meglio un falso positivo (ricodifica invece di
 * copia byte-identica) che un falso negativo (copia di un Raw con estensione
 * .jpg = file corrotto).
 */
private fun hasRawMagicBytes(resolver: ContentResolver, uri: Uri): Boolean {
    val header = resolver.openInputStream(uri)?.use { stream ->
        val buf = ByteArray(16)
        val read = stream.read(buf)
        if (read > 0) buf.copyOf(read) else null
    } ?: return false
    if (header.size < 4) return false

    val isII = header[0] == 0x49.toByte() && header[1] == 0x49.toByte() // "II" little-endian
    val isMM = header[0] == 0x4D.toByte() && header[1] == 0x4D.toByte() // "MM" big-endian

    // TIFF-based Raw: II*\0 (DNG, CR2, NEF, ARW, PEF, SR2, ...), IIU\0 (RW2),
    // IIRO/IIRS (ORF Olympus), MM\0* (alcuni Raw big-endian)
    if (isII && (header[2] == 0x2A.toByte() || header[2] == 0x55.toByte() ||
        header[2] == 0x52.toByte())) {
        return true
    }
    if (isMM && header[2] == 0x00.toByte() && header[3] == 0x2A.toByte()) return true

    // CR3 (Canon): box ISO BMFF con brand "crx "
    if (header.size >= 12 && String(header, 4, 8, Charsets.US_ASCII) == "ftypcrx ") return true

    // RAF (Fujifilm): "FUJIFILM"
    if (header.size >= 12 && String(header, 0, 8, Charsets.US_ASCII) == "FUJIFILM") return true

    // X3F (Sigma): "FOVb"
    if (String(header, 0, 4, Charsets.US_ASCII) == "FOVb") return true

    // 3FR/FFF (Hasselblad): "II" + 0x2A — già coperto sopra
    // ERF (Epson): TIFF-based — coperto sopra
    // IIQ (Phase One): "II" + 0x2A con magic specifico — coperto sopra

    return false
}

// Limite massimo di dimensione file per lo scan in memoria (200 MB)
private const val MAX_RAW_SCAN_BYTES = 200L * 1024 * 1024

// Limite massimo di dimensione del singolo JPEG embedded decodificato (30 MB)
private const val MAX_EMBEDDED_JPEG_BYTES = 30L * 1024 * 1024

// Numero massimo di marker SOI da provare (evita loop lenti su file strani)
private const val MAX_SOI_ATTEMPTS = 16

/**
 * Estrae il bitmap del JPEG embedded più grande da un file Raw.
 *
 * I formati Raw (DNG, CR2, CR3, NEF, ARW, ORF, RW2, PEF, SR2, RAF, ...)
 * embeddono uno o più JPEG: tipicamente una thumbnail piccola e una preview
 * a piena risoluzione (o full-size) elaborata dalla fotocamera. Questa
 * funzione scansiona il file alla ricerca dei marker SOI (FF D8 FF), prova
 * a decodificare da ciascun marker e restituisce il bitmap con l'area
 * maggiore — cioè la preview a più alta risoluzione disponibile.
 *
 * @param maxEdge dimensione massima del lato del bitmap restituito (sample
 *   size applicato in decode). Usa un valore piccolo (es. 96) per thumbnail,
 *   Int.MAX_VALUE per la piena risoluzione dell'embedded JPEG.
 *
 * Restituisce null se nessun JPEG embedded valido viene trovato.
 */
internal fun extractEmbeddedBitmap(resolver: ContentResolver, uri: Uri, maxEdge: Int = Int.MAX_VALUE): Bitmap? {
    // Controllo dimensione file prima di caricarlo in memoria
    val fileSize = runCatching {
        resolver.openFileDescriptor(uri, "r")?.use { it.statSize }
    }.getOrNull() ?: return null
    if (fileSize <= 0 || fileSize > MAX_RAW_SCAN_BYTES) return null

    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null

    var bestBitmap: Bitmap? = null
    var bestArea = 0L
    var attempts = 0
    var i = 0
    val size = bytes.size

    while (i < size - 3 && attempts < MAX_SOI_ATTEMPTS) {
        // Cerca il marker SOI: FF D8 FF
        if (bytes[i] == 0xFF.toByte() && bytes[i + 1] == 0xD8.toByte() && bytes[i + 2] == 0xFF.toByte()) {
            attempts++
            // Prova a decodificare da questo offset (limitiamo la lunghezza letta)
            val length = minOf((size - i).toLong(), MAX_EMBEDDED_JPEG_BYTES).toInt()
            val probe = ByteArrayInputStream(bytes, i, length)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(probe, null, bounds)
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                val area = bounds.outWidth.toLong() * bounds.outHeight.toLong()
                if (area > bestArea) {
                    // Decodifica con sample size per limitare la memoria
                    val sampleSize = if (maxEdge < Int.MAX_VALUE) {
                        calculateSampleSize(bounds, maxEdge, maxEdge)
                    } else {
                        1
                    }
                    val decodeStream = ByteArrayInputStream(bytes, i, length)
                    val bitmap = BitmapFactory.decodeStream(decodeStream, null, BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    })
                    if (bitmap != null) {
                        val bitmapArea = bitmap.width.toLong() * bitmap.height.toLong()
                        if (bitmapArea > bestArea) {
                            bestBitmap?.recycle()
                            bestBitmap = bitmap
                            bestArea = bitmapArea
                        } else {
                            bitmap.recycle()
                        }
                    }
                }
            }
            i += 3
        } else {
            i++
        }
    }

    return bestBitmap
}
