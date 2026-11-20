package ch.mathorck.easystock.services.reset

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class WipeAppFilesTest {
    private val dir: File = Files.createTempDirectory("wipe-test").toFile()
    private val filesDir = File(dir, "files")
    private val cacheDir = File(dir, "cache")

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun file(parent: File, path: String) = File(parent, path).apply {
        parentFile!!.mkdirs()
        writeText("x")
    }

    @Test
    fun everyDataFileIsDeleted() = runBlocking {
        val data = listOf(
            file(filesDir, "images/a.jpg"),
            file(filesDir, "images.incoming/images/b.jpg"),
            file(filesDir, "images.old/c.jpg"),
            file(filesDir, "images.extracting/images/d.jpg"),
            file(filesDir, "auto-backups/auto-1.easystock"),
            file(cacheDir, "camera/photo.jpg"),
            file(cacheDir, "backup/export.easystock"),
            // Admin screens: a full server backup and the invite PDF with a live token
            file(cacheDir, "server/share/easystock-server.easystock"),
            file(cacheDir, "server/share/easystock-qr.pdf"),
        )

        wipeAppFiles(filesDir, cacheDir, File(filesDir, "auto-backups"))

        data.forEach { assertFalse(it.exists(), it.path) }
        assertFalse(File(cacheDir, "server").exists())
    }

    @Test
    fun autoBackupsCanBeKept() = runBlocking {
        val backup = file(filesDir, "auto-backups/auto-1.easystock")
        val photo = file(filesDir, "images/a.jpg")

        wipeAppFiles(filesDir, cacheDir, File(filesDir, "auto-backups"), keepAutoBackups = true)

        assertTrue(backup.exists())
        assertFalse(photo.exists())
    }

    @Test
    fun theLegacyWipeKeepsTheOtherProfiles() = runBlocking {
        val other = file(filesDir, "profiles/0b6f3c3e-2d1a-4c8e-9f5b-7a2e4d6c8b10/images/a.jpg")
        val otherBackup = file(filesDir, "profiles/0b6f3c3e-2d1a-4c8e-9f5b-7a2e4d6c8b10/auto-backups/auto-1.easystock")

        wipeAppFiles(filesDir, cacheDir, File(filesDir, "auto-backups"))

        assertTrue(other.exists())
        assertTrue(otherBackup.exists())
    }
}
