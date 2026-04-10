package com.kompact.util

import android.os.Environment
import java.io.File
import java.util.Locale

object DefaultDestinationResolver {

    private const val DEFAULT_SUBFOLDER = "Kompact"

    fun picturesRelativePath(): String = "${Environment.DIRECTORY_PICTURES}/$DEFAULT_SUBFOLDER"

    fun documentsRelativePath(): String = "${Environment.DIRECTORY_DOCUMENTS}/$DEFAULT_SUBFOLDER"
    fun defaultPicturesLabel(): String = buildLabel(Environment.DIRECTORY_PICTURES)
    
    fun defaultDocumentsLabel(): String = buildLabel(Environment.DIRECTORY_DOCUMENTS)

    private fun buildLabel(directory: String): String {
        val folderName = externalDirectoryDisplayName(directory)
        val parts = listOfNotNull(folderName, DEFAULT_SUBFOLDER.takeIf { it.isNotBlank() })
        return parts.joinToString(" / ")
    }

    @Suppress("DEPRECATION")
    private fun externalDirectoryDisplayName(directory: String): String {
        val fallback = directory
            .lowercase(Locale.getDefault())
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        return runCatching {
            Environment.getExternalStoragePublicDirectory(directory)
        }.getOrNull()
            ?.takeIf { it.exists() }
            ?.let { file ->
                when {
                    file.name.isNotBlank() -> file.name
                    else -> file.path.substringAfterLast(File.separatorChar, fallback)
                }
            }
            ?: fallback
    }


    fun defaultDownloadsLabel(): String = buildLabel(Environment.DIRECTORY_DOWNLOADS)
    fun downloadsRelativePath(): String = "${Environment.DIRECTORY_DOWNLOADS}/$DEFAULT_SUBFOLDER"
}
