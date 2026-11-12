package ch.mathorck.easystock.sync

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.backup.AndroidBackupService
import ch.mathorck.easystock.backup.DeviceFixtures.sample
import ch.mathorck.easystock.backup.ImportInput
import ch.mathorck.easystock.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The automatic backup written before a reset wipe: a kept file, a normal backup, the newest three. */
class AutoBackupDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "auto-backup-test-${System.nanoTime()}")
    private lateinit var db: AppDatabase
    private lateinit var service: AndroidBackupService
    private val dir get() = File(root, "auto-backups")

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context).setDriver(BundledSQLiteDriver()).build()
        db.backupDao().replaceAll(sample())
        service = AndroidBackupService(context, db.backupDao(), File(root, "files").apply { mkdirs() }, File(root, "work"))
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    @Test
    fun theBackupFileExistsAndOpensAsAManualBackup() = runBlocking {
        val name = AndroidAutoBackups(dir, service).create()
        assertTrue(name.startsWith(AutoBackups.PREFIX))
        val file = File(dir, name)
        assertTrue(file.isFile && file.length() > 0)
        // No part file left, nothing left in the work folder
        assertEquals(listOf(name), dir.list()!!.toList())
        assertFalse(File(root, "work").listFiles().orEmpty().any { it.isFile })
        val opened = service.open(ImportInput.File(file.path), null)
        service.discard(opened)
    }

    @Test
    fun onlyTheNewestThreeStay() = runBlocking {
        dir.mkdirs()
        val old = listOf("20250101-0900", "20250102-0900", "20250103-0900").map { "auto-easystock-$it.easystock" }
        old.forEach { File(dir, it).writeText("x") }
        val name = AndroidAutoBackups(dir, service).create()
        assertEquals(setOf(old[1], old[2], name), dir.list()!!.toSet())
    }
}
