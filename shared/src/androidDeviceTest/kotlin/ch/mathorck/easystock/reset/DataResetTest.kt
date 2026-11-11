package ch.mathorck.easystock.reset

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.backup.BackupData
import ch.mathorck.easystock.backup.DeviceFixtures.PHOTO
import ch.mathorck.easystock.backup.DeviceFixtures.article
import ch.mathorck.easystock.backup.DeviceFixtures.sample
import ch.mathorck.easystock.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The reset of all data, on an in-memory database and a temporary file folder, never the app ones. */
class DataResetTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "reset-test-${System.nanoTime()}")
    private val filesDir = File(root, "files")
    private val cacheDir = File(root, "cache")
    private lateinit var db: AppDatabase
    private lateinit var reset: DataReset

    @Before
    fun setUp() = runBlocking {
        // A file, not in memory: a test reads its write-ahead log
        root.mkdirs()
        db = Room.databaseBuilder<AppDatabase>(context, File(root, "reset.db").path).setDriver(BundledSQLiteDriver()).build()
        reset = DataReset(db) { wipeAppFiles(filesDir, cacheDir) }
        // Every table, soft-deleted rows included
        db.backupDao().replaceAll(sample())
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    private fun write(file: File) = file.apply {
        parentFile!!.mkdirs()
        writeText("x")
    }

    @Test
    fun emptiesEveryTable() = runBlocking {
        val before = db.backupDao().snapshot()
        assertTrue(before.articles.any { it.deletedAt != null }, "sample() has a soft-deleted row")
        assertTrue(before.reviewLines.isNotEmpty() && before.movements.isNotEmpty())

        reset.run()

        assertEquals(BackupData(), db.backupDao().snapshot())
    }

    /** No copy of the deleted rows left in the write-ahead log: it is written back and emptied. */
    @Test
    fun leavesTheWriteAheadLogEmpty() = runBlocking {
        db.backupDao().replaceAll(BackupData(articles = (1..500).map { article("a$it", sku = "x".repeat(200) + it) }))
        val wal = File(root, "reset.db-wal")
        assertTrue(wal.length() > 0, "the database is in WAL mode")

        reset.run()

        assertEquals(0L, wal.length())
    }

    @Test
    fun theDatabaseStillWorksAfterTheReset() = runBlocking {
        reset.run()
        // The tag of a wiped article can be linked again
        val again = BackupData(articles = listOf(article("new", barcode = "04:A2")))
        db.backupDao().replaceAll(again)

        assertEquals(again.articles, db.backupDao().snapshot().articles)
    }

    @Test
    fun deletesPhotosAndTemporaryFilesOnly() = runBlocking {
        val photo = write(File(filesDir, PHOTO))
        val leftovers = listOf(write(File(filesDir, "images.incoming/$PHOTO")), write(File(filesDir, "images.old/a.jpg")))
        val capture = write(File(cacheDir, "camera/capture.jpg"))
        val export = write(File(cacheDir, "backup/easystock.zip"))
        val other = write(File(filesDir, "other/keep.txt"))
        val database = write(File(root, "databases/easystock.db"))

        reset.run()

        assertFalse(photo.exists())
        assertFalse(File(filesDir, "images").exists())
        leftovers.forEach { assertFalse(it.exists(), it.path) }
        assertFalse(capture.exists())
        assertFalse(export.exists())
        assertTrue(other.exists())
        assertTrue(database.exists())
    }

    @Test
    fun runsTwiceWithNothingLeft() = runBlocking {
        reset.run()
        reset.run()

        assertEquals(BackupData(), db.backupDao().snapshot())
    }
}
