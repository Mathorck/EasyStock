package ch.mathorck.easystock.backup

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.backup.DeviceFixtures.PHOTO
import ch.mathorck.easystock.backup.DeviceFixtures.article
import ch.mathorck.easystock.backup.DeviceFixtures.sample
import ch.mathorck.easystock.backup.DeviceFixtures.sorted
import ch.mathorck.easystock.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Export on one "phone", import on another: two databases and two file folders. */
class AndroidBackupServiceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "backup-test-${System.nanoTime()}")
    private val photoBytes = ByteArray(4000) { (it * 31).toByte() }

    private inner class Phone(name: String) {
        val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>(context).setDriver(BundledSQLiteDriver()).build()
        val files = File(root, "$name/files").apply { mkdirs() }
        val service = AndroidBackupService(context, db.backupDao(), files, File(root, "$name/work"))

        fun photo(path: String = PHOTO) = File(files, path)
    }

    private lateinit var sender: Phone
    private lateinit var receiver: Phone

    @Before
    fun setUp() = runBlocking {
        sender = Phone("sender")
        receiver = Phone("receiver")
        sender.db.backupDao().replaceAll(sample())
        sender.photo().apply { parentFile!!.mkdirs() }.writeBytes(photoBytes)
    }

    @After
    fun tearDown() {
        sender.db.close()
        receiver.db.close()
        root.deleteRecursively()
    }

    private suspend fun transfer(input: ImportInput, mode: ImportMode, password: CharArray? = null): ImportSummary {
        val opened = receiver.service.open(input, password)
        try {
            return receiver.service.import(opened, mode)
        } finally {
            receiver.service.discard(opened)
        }
    }

    @Test
    fun fileExportReplacesTheOtherPhone() = runBlocking {
        receiver.db.backupDao().replaceAll(BackupData(articles = listOf(article("local", image = "images/11111111-2222-3333-4444-555555555555.jpg"))))
        receiver.photo("images/11111111-2222-3333-4444-555555555555.jpg").apply { parentFile!!.mkdirs() }.writeBytes(ByteArray(3))
        val export = sender.service.export(null)
        assertFalse(export.encrypted)
        assertEquals(1, export.photos)
        assertEquals(QrDecision.TooLarge, export.qr)

        val input = ImportInput.File(export.path)
        assertFalse(receiver.service.needsPassword(input))
        val summary = transfer(input, ImportMode.REPLACE)

        assertEquals(sender.db.backupDao().snapshot().sorted(), receiver.db.backupDao().snapshot().sorted())
        assertContentEquals(photoBytes, receiver.photo().readBytes())
        assertFalse(receiver.photo("images/11111111-2222-3333-4444-555555555555.jpg").exists())
        assertEquals(1, summary.photos)
        assertEquals(4, summary.added[BackupTable.STOCK_MOVEMENT])
        sender.service.discard(export)
        assertFalse(File(export.path).exists())
    }

    @Test
    fun passwordProtectedExportMergesIntoTheOtherPhone() = runBlocking {
        receiver.db.backupDao().replaceAll(BackupData(articles = listOf(article("local", sku = "D-1"))))
        val export = sender.service.export("s3cret".toCharArray())
        assertTrue(export.encrypted)
        val input = ImportInput.File(export.path)

        assertTrue(receiver.service.needsPassword(input))
        assertEquals(BackupError.PASSWORD_REQUIRED, assertFailsWith<BackupException> { receiver.service.open(input, null) }.error)
        assertEquals(BackupError.WRONG_PASSWORD, assertFailsWith<BackupException> { receiver.service.open(input, "nope".toCharArray()) }.error)
        val summary = transfer(input, ImportMode.MERGE, "s3cret".toCharArray())

        val articles = receiver.db.backupDao().snapshot().articles
        assertEquals(4, articles.size)
        assertEquals(listOf(ConflictType.SKU_RENAMED), summary.conflicts.map { it.type })
        assertEquals(1, summary.photos)
        assertContentEquals(photoBytes, receiver.photo().readBytes())
    }

    @Test
    fun smallBackupFitsInAQrCode() = runBlocking {
        sender.photo().delete()
        val export = sender.service.export("pw".toCharArray())
        val text = assertIs<QrDecision.Fits>(export.qr).text

        val scanned = assertIs<ScannedQr.Data>(QrTransfer.parse(text))
        val input = ImportInput.Qr(scanned.payload)
        assertTrue(receiver.service.needsPassword(input))
        val summary = transfer(input, ImportMode.REPLACE, "pw".toCharArray())

        assertEquals(sender.db.backupDao().snapshot().sorted(), receiver.db.backupDao().snapshot().sorted())
        assertEquals(0, summary.photos)
    }

    @Test
    fun wifiTransferOnThisDevice() = runBlocking {
        val export = sender.service.export(null)
        val wifi = try {
            sender.service.startWifi(export)
        } catch (e: BackupException) {
            assumeTrue("No Wi-Fi network on this device", e.error != BackupError.NOT_ON_WIFI)
            throw e
        }
        try {
            val scanned = assertIs<ScannedQr.Wifi>(QrTransfer.parse(QrTransfer.wifiQrText(wifi.ticket)))
            val input = receiver.service.download(scanned.ticket) {}
            transfer(input, ImportMode.MERGE)
            receiver.service.discard(input)

            withTimeout(10.seconds) { wifi.state.first { it == WifiState.Done } }
            assertEquals(sender.db.backupDao().snapshot().sorted(), receiver.db.backupDao().snapshot().sorted())
            assertContentEquals(photoBytes, receiver.photo().readBytes())
        } finally {
            wifi.stop()
        }
    }

    @Test
    fun otherFileIsNotABackup() = runBlocking {
        val file = File(root, "notes.txt").apply { writeText("hello") }
        val error = assertFailsWith<BackupException> { receiver.service.needsPassword(ImportInput.File(file.path)) }
        assertEquals(BackupError.NOT_A_BACKUP, error.error)
    }
}
