package com.kompact.folder

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Environment
import android.provider.DocumentsContract
import android.system.Os
import android.util.AtomicFile
import androidx.annotation.RequiresApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit

/** In-place write retains the document URI, filename, inode/birth time and gallery identity.
 * A durable private backup + journal is written BEFORE truncation. Never delete a backup
 * when rollback fails; the next worker restores it before doing any other work. */
@RequiresApi(26)
internal class PhotoReplacement(private val context: Context) {
    private val directory = File(context.filesDir, "folder-recovery").apply { mkdirs() }
    private val backup = File(directory, "original.bin")
    private val journal = AtomicFile(File(directory, "pending.json"))

    fun hasPendingRecovery() = journal.baseFile.exists()

    suspend fun recover() = withContext(NonCancellable) {
        if (!hasPendingRecovery()) return@withContext
        val entry = journal.openRead().use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
        val original = backup.readBytes()
        check(sha256(original) == entry.getString("sha256")) { "Recovery backup checksum mismatch" }
        open(Uri.parse(entry.getString("uri"))).use { fd ->
            val stat = Os.fstat(fd.fileDescriptor)
            check(stat.st_ino == entry.getLong("inode") && stat.st_dev == entry.getLong("device")) {
                "Original document was replaced outside Kompact; recovery backup retained"
            }
            write(fd, original)
            restoreTime(fd, entry.getLong("modified"), entry.getLong("accessed"))
            check(read(fd).contentEquals(original)) { "Restored photo verification failed" }
        }
        journal.delete()
        backup.delete()
    }

    suspend fun replace(uri: Uri, expected: ByteArray, output: ByteArray) = withContext(NonCancellable) {
        check(!hasPendingRecovery()) { "Pending recovery must be completed first" }
        open(uri).use { fd ->
            val path = fdPath(fd)
            val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
            check(attributes.isRegularFile) { "Only local, seekable files can retain their dates" }
            val modified = attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS)
            val accessed = attributes.lastAccessTime().to(TimeUnit.NANOSECONDS)
            // Fail before any write when the provider cannot restore timestamps.
            restoreTime(fd, modified, accessed)
            check(Files.getLastModifiedTime(path).to(TimeUnit.NANOSECONDS) == modified) { "Provider cannot preserve file date" }
            check(read(fd).contentEquals(expected)) { "Photo changed since it was read" }
            val stat = Os.fstat(fd.fileDescriptor)
            FileOutputStream(backup).use { it.write(expected); it.fd.sync() }
            val entry = JSONObject().apply {
                put("uri", uri.toString()); put("inode", stat.st_ino); put("device", stat.st_dev)
                put("modified", modified); put("accessed", accessed); put("sha256", sha256(expected))
            }
            val stream = journal.startWrite()
            try {
                stream.write(entry.toString().toByteArray())
                journal.finishWrite(stream)
            } catch (e: Exception) {
                journal.failWrite(stream)
                throw e
            }
            try {
                write(fd, output)
                check(read(fd).contentEquals(output)) { "Written photo verification failed" }
                restoreTime(fd, modified, accessed)
                check(Files.getLastModifiedTime(path).to(TimeUnit.NANOSECONDS) == modified) { "File date verification failed" }
                // Refresh size/dimensions on local external storage without deleting the gallery row.
                val localPath = localMediaPath(uri)
                journal.delete()
                backup.delete()
                if (localPath != null) {
                    runCatching { MediaScannerConnection.scanFile(context, arrayOf(localPath), null, null) }
                }
            } catch (e: Exception) {
                try {
                    recover()
                } catch (rollback: Exception) {
                    throw RecoveryRequired("Restoration required; original retained in app storage. ${rollback.message}", rollback)
                }
                throw e
            }
        }
    }

    private fun open(uri: Uri) = requireNotNull(context.contentResolver.openFileDescriptor(uri, "rw")) { "Cannot open photo for writing" }
    private fun localMediaPath(uri: Uri): String? {
        if (uri.authority != "com.android.externalstorage.documents") return null
        return runCatching {
            val id = DocumentsContract.getDocumentId(uri)
            val volume = id.substringBefore(':')
            val relative = id.substringAfter(':')
            @Suppress("DEPRECATION")
            val root = if (volume == "primary") Environment.getExternalStorageDirectory() else File("/storage", volume)
            File(root, relative).path
        }.getOrNull()
    }
    private fun fdPath(fd: ParcelFileDescriptor): Path = Paths.get("/proc/self/fd/${fd.fd}")
    private fun restoreTime(fd: ParcelFileDescriptor, modified: Long, accessed: Long) {
        Files.getFileAttributeView(fdPath(fd), BasicFileAttributeView::class.java).setTimes(
            FileTime.from(modified, TimeUnit.NANOSECONDS), FileTime.from(accessed, TimeUnit.NANOSECONDS), null
        )
    }
    private fun write(fd: ParcelFileDescriptor, bytes: ByteArray) {
        Os.lseek(fd.fileDescriptor, 0, android.system.OsConstants.SEEK_SET)
        ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(fd.fileDescriptor)).use { stream ->
                stream.write(bytes)
                stream.channel.truncate(bytes.size.toLong())
                stream.fd.sync()
        }
    }
    private fun read(fd: ParcelFileDescriptor): ByteArray {
        Os.lseek(fd.fileDescriptor, 0, android.system.OsConstants.SEEK_SET)
        return ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(fd.fileDescriptor)).use { it.readBytes() }
    }
    class RecoveryRequired(message: String, cause: Throwable) : Exception(message, cause)
    private fun sha256(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
