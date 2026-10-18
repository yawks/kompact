package com.kompact.util

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile

/**
 * Utility centralizzata per l'eliminazione dei file sorgente dopo il
 * processamento. Usa solo API Android standard (nessuna libreria esterna,
 * nessun permesso speciale) e applica le strategie nel modo più efficace
 * per ogni versione di Android.
 *
 * Strategie, in ordine di applicazione:
 * 1. DocumentsContract.deleteDocument() — per URI SAF (DocumentFile)
 * 2. ContentResolver.delete() — per URI MediaStore / content generici
 * 3. File system diretto — per URI file:// (legacy)
 * 4. Verifica esistenza — un file già inesistente è considerato eliminato
 *
 * Su Android 11+ (API 30+), quando il delete diretto fallisce, l'UI può
 * usare [createWriteAccessRequest] (MediaStore.createWriteRequest) per
 * ottenere accesso in scrittura tramite dialog di sistema, poi ritentare
 * con [deleteAfterWriteAccess].
 */
object SourceDeleter {

    /**
     * Risultato di un tentativo di eliminazione.
     */
    sealed class DeleteResult {
        /** File eliminato con successo (o già inesistente). */
        data object Deleted : DeleteResult()

        /**
         * Eliminazione fallita. Su Android 10 (Q) può contenere un
         * IntentSender per il dialog di consenso recuperabile
         * (RecoverableSecurityException).
         */
        data class Failed(val recoverableSender: IntentSender? = null) : DeleteResult()
    }

    /**
     * Prova a eliminare un file sorgente applicando tutte le strategie
     * disponibili in ordine di efficacia.
     *
     * @return [DeleteResult.Deleted] se il file è stato eliminato (o non
     *   esisteva già), [DeleteResult.Failed] altrimenti.
     */
    fun deleteSource(context: Context, uri: Uri): DeleteResult {
        val resolver = context.contentResolver

        // Strategia 1: DocumentsContract.deleteDocument() — il metodo corretto
        // per URI SAF (DocumentsProvider). Funziona quando l'app ha il
        // permesso di scrittura sul documento.
        runCatching {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.deleteDocument(resolver, uri)
                return DeleteResult.Deleted
            }
        }

        // Strategia 2: DocumentFile.delete() — fallback per URI SAF che
        // DocumentsContract non riconosce come document URI.
        runCatching {
            DocumentFile.fromSingleUri(context, uri)?.delete()?.let { deleted ->
                if (deleted) return DeleteResult.Deleted
            }
        }

        // Strategia 3: ContentResolver.delete() — per URI MediaStore e
        // content generici. Su Android 10 può lanciare
        // RecoverableSecurityException per file non propri dell'app.
        runCatching {
            val rows = resolver.delete(uri, null, null)
            if (rows > 0) return DeleteResult.Deleted
        }.getOrElse { error ->
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q &&
                error is android.app.RecoverableSecurityException
            ) {
                return DeleteResult.Failed(error.userAction.actionIntent.intentSender)
            }
            // Su Android 11+ MediaStore.delete può lanciare SecurityException
            // per file non propri: non è recuperabile qui, l'UI proverà con
            // createWriteRequest.
        }

        // Strategia 4: file:// URI — accesso diretto al file system (legacy,
        // solo per URI file:// in directory accessibili all'app).
        if (uri.scheme == "file") {
            runCatching {
                java.io.File(uri.path ?: return@runCatching).delete().let { deleted ->
                    if (deleted) return DeleteResult.Deleted
                }
            }
        }

        // Verifica finale: se il file non esiste più (già eliminato da un
        // tentativo precedente o manualmente dall'utente), consideralo
        // eliminato — non è un fallimento.
        if (!exists(context, uri)) {
            return DeleteResult.Deleted
        }

        return DeleteResult.Failed()
    }

    /**
     * Verifica se un file esiste ancora (tramite DocumentFile o query
     * ContentResolver). Restituisce false se non è possibile determinarlo
     * con certezza (in quel caso il chiamante tratta come "non eliminato").
     */
    fun exists(context: Context, uri: Uri): Boolean {
        // Prova con DocumentFile (funziona per URI SAF e MediaStore)
        runCatching {
            DocumentFile.fromSingleUri(context, uri)?.exists()?.let { return it }
        }

        // Fallback: query ContentResolver per SIZE (se risponde, esiste)
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null, null, null
            )?.use { cursor -> return cursor.moveToFirst() }
        }

        // Ultimo tentativo: per URI file://, controlla il file system
        if (uri.scheme == "file") {
            return java.io.File(uri.path ?: "").exists()
        }

        // Non determinabile: assumiamo che esista (non eliminato)
        return true
    }

    /**
     * Verifica se un URI è un URI MediaStore (o convertibile in tale).
     */
    fun isMediaStoreUri(uri: Uri): Boolean {
        return uri.authority == MediaStore.AUTHORITY ||
            uri.authority == "com.android.providers.media.documents"
    }

    /**
     * Converte un URI SAF in URI MediaStore quando possibile.
     * Gestisce i provider più comuni: MediaDocumentsProvider,
     * ExternalStorageProvider (file manager stock), Google Photos,
     * e provider di terze parti con document ID basato su path.
     *
     * @return URI MediaStore corrispondente, o null se non convertibile.
     */
    fun toMediaStoreUri(context: Context, uri: Uri): Uri? {
        val resolver = context.contentResolver
        return when (uri.authority) {
            MediaStore.AUTHORITY -> uri

            "com.android.providers.media.documents" -> {
                // URI dal MediaDocumentsProvider (selezione da Galleria)
                val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
                    ?: return null
                val type = documentId.substringBefore(':', "")
                val id = documentId.substringAfter(':', "").toLongOrNull() ?: return null
                val collection = when (type) {
                    "image" -> MediaStore.Images.Media.getContentUri("external")
                    "video" -> MediaStore.Video.Media.getContentUri("external")
                    "audio" -> MediaStore.Audio.Media.getContentUri("external")
                    "images" -> MediaStore.Images.Media.getContentUri("external")
                    "videos" -> MediaStore.Video.Media.getContentUri("external")
                    "audios" -> MediaStore.Audio.Media.getContentUri("external")
                    else -> return null
                }
                ContentUris.withAppendedId(collection, id)
            }

            "com.android.externalstorage.documents" -> {
                // URI SAF del file manager stock (ExternalStorageProvider)
                val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
                    ?: return null
                val splitIndex = documentId.indexOf(':')
                if (splitIndex <= 0) return null
                val volume = documentId.substring(0, splitIndex)
                val relativePath = documentId.substring(splitIndex + 1)
                val storagePath = if (volume.equals("primary", ignoreCase = true)) {
                    "/storage/emulated/0/$relativePath"
                } else {
                    "/storage/$volume/$relativePath"
                }
                queryMediaStoreByPath(resolver, storagePath)
            }

            "com.google.android.apps.photos.contentprovider" -> {
                // URI da Google Foto: il document ID è nel formato
                // "content_uri" o un path. Prova a estrarre un MediaStore ID.
                val lastSegment = uri.lastPathSegment ?: return null
                // Formato tipico: /content/.../<id>
                val id = lastSegment.substringAfterLast('/').toLongOrNull() ?: return null
                ContentUris.withAppendedId(
                    MediaStore.Images.Media.getContentUri("external"), id
                )
            }

            else -> {
                // Per altri provider SAF, prova a estrarre il path dal
                // document ID e cercarlo in MediaStore.
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
                    if (documentId != null) {
                        // Prova a interpretare il document ID come path
                        val path = documentId.substringAfter(':', documentId)
                            .removePrefix("/document/")
                        if (path.isNotEmpty() && !path.contains(':')) {
                            val fullPath = if (path.startsWith("/")) path
                                else "/storage/emulated/0/$path"
                            queryMediaStoreByPath(resolver, fullPath)?.let { return it }
                        }
                    }
                }
                null
            }
        }
    }

    /**
     * Cerca un file in MediaStore tramite path assoluto (colonna DATA).
     * Deprecato su Android 10+, ma necessario per la conversione SAF →
     * MediaStore su Android 11+ dove createDeleteRequest richiede URI
     * MediaStore.
     */
    @Suppress("DEPRECATION")
    private fun queryMediaStoreByPath(resolver: ContentResolver, storagePath: String): Uri? {
        val filesUri = MediaStore.Files.getContentUri("external")
        val cursor = resolver.query(
            filesUri,
            arrayOf(MediaStore.Files.FileColumns._ID),
            "${MediaStore.Files.FileColumns.DATA} = ?",
            arrayOf(storagePath),
            null
        )
        cursor?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                return ContentUris.withAppendedId(filesUri, id)
            }
        }
        return null
    }

    /**
     * Su Android 11+ (API 30+): crea un IntentSender per richiedere accesso
     * in scrittura a un insieme di URI MediaStore tramite il dialog di
     * sistema nativo (MediaStore.createWriteRequest).
     *
     * Questo permette all'utente di concedere accesso in scrittura a file
     * che l'app non possiede (es. foto scattate con la fotocamera di
     * sistema). Non richiede permessi speciali nel manifest, è approvato
     * da F-Droid, e il dialog è gestito dal sistema.
     *
     * @param mediaStoreUris URI MediaStore a cui richiedere accesso in scrittura
     * @return IntentSender da lanciare con StartIntentSenderForResult, o null
     *   se non è possibile crearlo (API < 30 o lista vuota).
     */
    fun createWriteAccessRequest(
        resolver: ContentResolver,
        mediaStoreUris: Collection<Uri>
    ): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        if (mediaStoreUris.isEmpty()) return null
        return runCatching {
            MediaStore.createWriteRequest(resolver, mediaStoreUris).intentSender
        }.getOrNull()
    }

    /**
     * Su Android 12+ (API 31+): crea un IntentSender per spostare i file nel
     * cestino (MediaStore.createTrashRequest) invece di eliminarli
     * definitivamente. Il cestino è più permissivo del delete diretto e
     * l'utente può ripristinare i file in seguito.
     *
     * @return IntentSender da lanciare, o null se non disponibile.
     */
    fun createTrashRequest(
        resolver: ContentResolver,
        mediaStoreUris: Collection<Uri>
    ): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        if (mediaStoreUris.isEmpty()) return null
        return runCatching {
            MediaStore.createTrashRequest(resolver, mediaStoreUris, true).intentSender
        }.getOrNull()
    }

    /**
     * Su Android 11+ (API 30+): crea un IntentSender per eliminare un
     * insieme di URI MediaStore tramite il dialog di sistema
     * (MediaStore.createDeleteRequest).
     *
     * @return IntentSender da lanciare, o null se non è possibile crearlo.
     */
    fun createDeleteRequest(
        resolver: ContentResolver,
        mediaStoreUris: Collection<Uri>
    ): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        if (mediaStoreUris.isEmpty()) return null
        return runCatching {
            MediaStore.createDeleteRequest(resolver, mediaStoreUris).intentSender
        }.getOrNull()
    }

    /**
     * Dopo che l'utente ha concesso accesso in scrittura (tramite
     * createWriteRequest) o confermato l'eliminazione (tramite
     * createDeleteRequest), ritenta l'eliminazione dei file.
     *
     * @return URI che sono stati eliminati con successo.
     */
    fun deleteAfterSystemConsent(context: Context, uris: List<Uri>): List<Uri> {
        val deleted = mutableListOf<Uri>()
        for (uri in uris) {
            when (deleteSource(context, uri)) {
                is DeleteResult.Deleted -> deleted += uri
                is DeleteResult.Failed -> { /* resta in sospeso */ }
            }
        }
        return deleted
    }
}
