package com.kompact.folder

import android.content.Context
import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.system.Os
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.security.MessageDigest

/** Synthetic files only; never reads or modifies the phone's photo collection. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26)
class PhotoReplacementTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var photo: File
    private lateinit var replacement: PhotoReplacement

    @Before fun setup() {
        photo = File(context.cacheDir, "synthetic-folder-photo.jpg")
        replacement = PhotoReplacement(context)
        check(!replacement.hasPendingRecovery()) { "A real recovery is pending: refusing to run test" }
        photo.writeBytes(ByteArray(20_000) { (it % 251).toByte() })
    }
    @After fun cleanup() { photo.delete() }

    @Test fun replacementRetainsNameInodeAndExactModificationTimeAndTruncates() = runBlocking {
        val original = photo.readBytes()
        val modified = FileTime.fromMillis(1_600_000_000_123)
        Files.setLastModifiedTime(photo.toPath(), modified)
        val inode = Os.stat(photo.path).st_ino
        val output = ByteArray(2048) { 42 }
        replacement.replace(Uri.fromFile(photo), original, output)
        assertArrayEquals(output, photo.readBytes())
        assertEquals(inode, Os.stat(photo.path).st_ino)
        assertEquals(modified, Files.getLastModifiedTime(photo.toPath()))
        assertEquals("synthetic-folder-photo.jpg", photo.name)
        assertFalse(replacement.hasPendingRecovery())
    }

    @Test fun concurrentModificationIsRejectedBeforeWriting() = runBlocking {
        val expected = photo.readBytes()
        val changed = byteArrayOf(1, 2, 3)
        photo.writeBytes(changed)
        try {
            replacement.replace(Uri.fromFile(photo), expected, byteArrayOf(9))
            fail("Concurrent change should prevent replacement")
        } catch (_: IllegalStateException) { }
        assertArrayEquals(changed, photo.readBytes())
        assertFalse(replacement.hasPendingRecovery())
    }

    @Test fun interruptedWriteCanBeRestoredFromTheDurableJournal() = runBlocking {
        val original = photo.readBytes()
        val modified = 1_600_000_000_123_000_000L
        val stat = Os.stat(photo.path)
        val recovery = File(context.filesDir, "folder-recovery").apply { mkdirs() }
        File(recovery, "original.bin").writeBytes(original)
        File(recovery, "pending.json").writeText(JSONObject().apply {
            put("uri", Uri.fromFile(photo).toString()); put("inode", stat.st_ino); put("device", stat.st_dev)
            put("modified", modified); put("accessed", modified)
            put("sha256", MessageDigest.getInstance("SHA-256").digest(original).joinToString("") { "%02x".format(it) })
        }.toString())
        photo.writeBytes(byteArrayOf(1, 2)) // Simulate process death halfway through an in-place write.
        replacement.recover()
        assertArrayEquals(original, photo.readBytes())
        assertEquals(FileTime.fromMillis(1_600_000_000_123), Files.getLastModifiedTime(photo.toPath()))
        assertFalse(replacement.hasPendingRecovery())
    }

    @Test fun jpegExifSurvivesRealBitmapReencodingAndRemainsReadable() {
        val bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        try {
            photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            ExifInterface(photo).apply {
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2020:09:13 12:26:40")
                setAttribute(ExifInterface.TAG_MAKE, "Synthetic Camera")
                setAttribute(ExifInterface.TAG_ORIENTATION, "6")
                setLatLong(48.8566, 2.3522)
                saveAttributes()
            }
            val original = photo.readBytes()
            val resized = Bitmap.createScaledBitmap(bitmap, 600, 400, true)
            val encoded = try {
                ByteArrayOutputStream().use {
                    resized.compress(Bitmap.CompressFormat.JPEG, 85, it)
                    ImageMetadata.copy(original, it.toByteArray(), ImageMetadata.Format.JPEG)
                }
            } finally { resized.recycle() }
            val resultExif = ExifInterface(encoded.inputStream())
            assertEquals("2020:09:13 12:26:40", resultExif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
            assertEquals("Synthetic Camera", resultExif.getAttribute(ExifInterface.TAG_MAKE))
            assertEquals(6, resultExif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
            assertEquals(48.8566, resultExif.latLong!![0], 0.0001)
            val decoded = BitmapFactory.decodeByteArray(encoded, 0, encoded.size)
            assertEquals(600, decoded.width)
            assertEquals(400, decoded.height)
            decoded.recycle()
        } finally { bitmap.recycle() }
    }

    @Test fun queueResumesOnlyUnfinishedPhotosAndKeepsResults() {
        FolderJobStore(context).use {
            val job = "synthetic-${System.nanoTime()}"
            it.add(job, "content://test/one", "one.jpg")
            it.add(job, "content://test/two", "two.jpg")
            it.add(job, "content://test/one", "one.jpg")
            it.finish(job, "content://test/one", "reduced", "", 1000)
            it.finishScan(job)
            assertEquals("content://test/two", it.next(job)?.first)
            assertTrue(it.scanned(job))
            assertEquals(2, it.summary(job).total)
            assertEquals(1, it.summary(job).reduced)
            assertEquals(1000L, it.summary(job).saved)
        }
    }

    @Test fun backgroundWorkerShrinksAQueuedPhotoAndPreservesExifNameAndDate() = runBlocking {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.adoptShellPermissionIdentity(Manifest.permission.ACCESS_MEDIA_LOCATION)
        try {
            val bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
            try { photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) } }
            finally { bitmap.recycle() }
            ExifInterface(photo).apply {
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2020:09:13 12:26:40")
                setAttribute(ExifInterface.TAG_ORIENTATION, "6")
                setLatLong(48.8566, 2.3522)
                saveAttributes()
            }
            val modified = FileTime.fromMillis(1_600_000_000_123)
            Files.setLastModifiedTime(photo.toPath(), modified)
            val inode = Os.stat(photo.path).st_ino
            val worker = TestListenableWorkerBuilder<FolderShrinkWorker>(context)
                .setInputData(workDataOf("tree" to "content://com.android.externalstorage.documents/tree/primary%3APictures", "megapixels" to 0.1))
                .build()
            FolderJobStore(context).use {
                it.add(worker.id.toString(), Uri.fromFile(photo).toString(), photo.name)
                it.finishScan(worker.id.toString())
            }
            val result = worker.doWork()
            assertTrue("Worker result: $result", result is ListenableWorker.Result.Success)
            val bytes = photo.readBytes()
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertTrue(decoded.width.toLong() * decoded.height <= 100_000)
            decoded.recycle()
            assertEquals(inode, Os.stat(photo.path).st_ino)
            assertEquals(modified, Files.getLastModifiedTime(photo.toPath()))
            val exif = ExifInterface(photo)
            assertEquals("2020:09:13 12:26:40", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
            assertEquals(6, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
            assertEquals(48.8566, exif.latLong!![0], 0.0001)
            FolderJobStore(context).use { assertEquals(1, it.summary(worker.id.toString()).reduced) }
        } finally { automation.dropShellPermissionIdentity() }
    }

    @SdkSuppress(minSdkVersion = 30)
    @Test fun pngAndWebpMetadataReassemblyProducesDecodableImagesWithAlpha() {
        val source = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val resized = Bitmap.createScaledBitmap(source, 400, 300, true)
        try {
            for ((format, compression) in listOf(ImageMetadata.Format.PNG to Bitmap.CompressFormat.PNG,
                ImageMetadata.Format.WEBP to Bitmap.CompressFormat.WEBP_LOSSLESS)) {
                fun encode(bitmap: Bitmap) = ByteArrayOutputStream().use {
                    bitmap.compress(compression, 100, it)
                    it.toByteArray()
                }
                val result = ImageMetadata.copy(encode(source), encode(resized), format)
                val decoded = BitmapFactory.decodeByteArray(result, 0, result.size)
                assertNotNull(decoded)
                assertEquals(400, decoded.width)
                assertEquals(300, decoded.height)
                assertEquals(0, decoded.getPixel(0, 0))
                decoded.recycle()
            }
        } finally { source.recycle(); resized.recycle() }
    }
}
