package ch.mathorck.easystock.services.backup

import ch.mathorck.easystock.data.dto.backup.BackupData
import ch.mathorck.easystock.data.dto.backup.BackupManifest
import ch.mathorck.easystock.enums.backup.BackupError
import ch.mathorck.easystock.enums.backup.BackupKind
import ch.mathorck.easystock.exceptions.backup.BackupException
import ch.mathorck.easystock.services.backup.BackupFixtures.full
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackupArchiveTest {
    private val dir: File = Files.createTempDirectory("backup-test").toFile()
    private val filesDir = File(dir, "files").apply { mkdirs() }
    private val manifest = BackupManifest(appVersion = "1.0.0", createdAt = "2026-11-10T12:00:00Z")
    private val photo = full().articles.first().imagePath!!
    private val photoBytes = ByteArray(5000) { (it * 7).toByte() }

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun writeArchive(data: BackupData = full()): Pair<File, Int> {
        File(filesDir, photo).apply { parentFile!!.mkdirs() }.writeBytes(photoBytes)
        val file = File(dir, "backup.zip")
        val photos = file.outputStream().use { BackupArchive.write(it, manifest, data, filesDir) }
        return file to photos
    }

    @Test
    fun archiveRoundTripsWithPhotos() {
        val (file, photos) = writeArchive()

        assertEquals(1, photos)
        assertEquals(BackupKind.ZIP, backupKind(file))
        val content = BackupArchive.read(file)
        assertEquals(full(), content.data)
        assertEquals(setOf(photo), content.photos)
        assertEquals(1, content.manifest.counts["images"])
        assertEquals(2, content.manifest.counts["stock_movement"])

        val target = File(dir, "target")
        assertEquals(setOf(photo), BackupArchive.extractPhotos(file, listOf(photo), target))
        assertContentEquals(photoBytes, File(target, photo).readBytes())
    }

    @Test
    fun missingPhotoFileIsLeftOut() {
        val file = File(dir, "backup.zip")
        val photos = file.outputStream().use { BackupArchive.write(it, manifest, full(), filesDir) }

        assertEquals(0, photos)
        assertEquals(emptySet(), BackupArchive.read(file).photos)
    }

    @Test
    fun encryptedArchiveRoundTrips() {
        val (plain, _) = writeArchive()
        val secret = BackupSecret.Password("pw".toCharArray(), iterations = 1_000)
        val encrypted = File(dir, "backup.easystock")
        encrypted.outputStream().use { out ->
            EncryptingOutputStream(BackupCipher.encryptingSink(out.asByteSink(), secret)).use { plain.inputStream().copyTo(it) }
        }

        assertEquals(BackupKind.ENCRYPTED, backupKind(encrypted))
        val decrypted = BackupCipher.decrypt(encrypted.readBytes(), BackupSecret.Password("pw".toCharArray(), iterations = 1_000))
        assertContentEquals(plain.readBytes(), decrypted)
    }

    @Test
    fun archiveWithoutManifestIsNotABackup() {
        val file = zip("tables/category.json" to "[]")
        assertEquals(BackupError.NOT_A_BACKUP, assertFailsWith<BackupException> { BackupArchive.read(file) }.error)
    }

    @Test
    fun archiveOfANewerAppIsRejected() {
        val newer = BackupCodec.encodeManifest(manifest.copy(schemaVersion = 99)).decodeToString()
        val file = zip("manifest.json" to newer)
        assertEquals(BackupError.NEWER_VERSION, assertFailsWith<BackupException> { BackupArchive.read(file) }.error)
    }

    @Test
    fun inconsistentArchiveIsDamaged() {
        val file = zip(
            "manifest.json" to BackupCodec.encodeManifest(manifest).decodeToString(),
            "tables/article.json" to """[{"id":"a","name":"A","category_id":"missing","tracking":"quantity","created_at":1,"updated_at":1}]""",
        )
        assertEquals(BackupError.DAMAGED, assertFailsWith<BackupException> { BackupArchive.read(file) }.error)
    }

    @Test
    fun randomFileIsDamagedOrUnknown() {
        val file = File(dir, "random.bin").apply { writeBytes(ByteArray(100) { it.toByte() }) }
        assertEquals(BackupKind.UNKNOWN, backupKind(file))
        assertFailsWith<BackupException> { BackupArchive.read(file) }
    }

    @Test
    fun onlyValidPhotoPathsAreExtracted() {
        val file = zip(
            "manifest.json" to BackupCodec.encodeManifest(manifest).decodeToString(),
            "../evil.jpg" to "x",
            "images/not-a-uuid.jpg" to "x",
        )
        val target = File(dir, "target")
        assertEquals(emptySet(), BackupArchive.extractPhotos(file, listOf("../evil.jpg", "images/not-a-uuid.jpg"), target))
        assertTrue(!File(dir, "evil.jpg").exists())
    }

    @Test
    fun oversizedEntryIsTooLarge() {
        val input = ByteArray(2000).inputStream()
        val error = assertFailsWith<BackupException> { copyBounded(input, ByteArrayOutputStream(), 1000) }
        assertEquals(BackupError.TOO_LARGE, error.error)
    }

    private fun zip(vararg entries: Pair<String, String>): File {
        val file = File(dir, "custom.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.encodeToByteArray())
                zip.closeEntry()
            }
        }
        return file
    }

    private fun photoZip(vararg entries: Pair<String, ByteArray>): File {
        val file = File(dir, "photos.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun aFailedPhotoLeavesNoPhotoBehind() {
        val first = "images/00000000-0000-0000-0000-000000000001.jpg"
        val second = "images/00000000-0000-0000-0000-000000000002.jpg"
        val file = photoZip(first to ByteArray(100), second to ByteArray(300))
        val target = File(dir, "target")

        val error = assertFailsWith<BackupException> {
            BackupArchive.extractPhotos(file, listOf(first, second), target, maxImageBytes = 200)
        }

        assertEquals(BackupError.TOO_LARGE, error.error)
        assertTrue(target.walk().none { it.isFile })
        assertTrue(!File(target, PHOTO_STAGING_DIR).exists())
    }

    @Test
    fun photosTogetherAreCapped() {
        val first = "images/00000000-0000-0000-0000-000000000001.jpg"
        val second = "images/00000000-0000-0000-0000-000000000002.jpg"
        val file = photoZip(first to ByteArray(150), second to ByteArray(150))
        val target = File(dir, "target")

        val error = assertFailsWith<BackupException> {
            BackupArchive.extractPhotos(file, listOf(first, second), target, maxImageBytes = 200, maxTotalBytes = 250)
        }

        assertEquals(BackupError.TOO_LARGE, error.error)
        assertTrue(target.walk().none { it.isFile })
        // Under the cap, both are moved into place
        assertEquals(setOf(first, second), BackupArchive.extractPhotos(file, listOf(first, second), target, maxTotalBytes = 300))
        assertEquals(150, File(target, second).length())
        assertTrue(!File(target, PHOTO_STAGING_DIR).exists())
    }

}
