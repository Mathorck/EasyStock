package ch.mathorck.easystock.reset

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

        wipeAppFiles(filesDir, cacheDir)

        data.forEach { assertFalse(it.exists(), it.path) }
        assertFalse(File(cacheDir, "server").exists())
    }

    @Test
    fun autoBackupsCanBeKept() = runBlocking {
        val backup = file(filesDir, "auto-backups/auto-1.easystock")
        val photo = file(filesDir, "images/a.jpg")

        wipeAppFiles(filesDir, cacheDir, keepAutoBackups = true)

        assertTrue(backup.exists())
        assertFalse(photo.exists())
    }
}
