package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.backup.BackupData
import ch.mathorck.easystock.backup.ConflictType
import ch.mathorck.easystock.backup.DeviceFixtures.article
import ch.mathorck.easystock.backup.DeviceFixtures.category
import ch.mathorck.easystock.backup.DeviceFixtures.item
import ch.mathorck.easystock.backup.DeviceFixtures.location
import ch.mathorck.easystock.backup.DeviceFixtures.movement
import ch.mathorck.easystock.backup.DeviceFixtures.sample
import ch.mathorck.easystock.backup.DeviceFixtures.sorted
import ch.mathorck.easystock.backup.withItemLocations
import androidx.room.useReaderConnection
import ch.mathorck.easystock.backup.BackupTable
import ch.mathorck.easystock.backup.backupJson
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class BackupDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: BackupDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context).setDriver(BundledSQLiteDriver()).build()
        dao = db.backupDao()
    }

    @After
    fun tearDown() = db.close()

    /**
     * Every column of every table is in the backup, under its column name. Catches a new entity
     * field without its @SerialName (the backup would silently use another key or lose it).
     */
    @Test
    fun everyColumnIsInTheBackupUnderItsName() = runBlocking {
        val json = backupJson.encodeToJsonElement(BackupData.serializer(), sample()).jsonObject
        for (table in BackupTable.entries) {
            val columns = db.useReaderConnection { connection ->
                connection.usePrepared("PRAGMA table_info(`${table.tableName}`)") { statement ->
                    buildSet { while (statement.step()) add(statement.getText(1)) }
                }
            }
            assertTrue(columns.isNotEmpty(), table.tableName)
            val rows = json.getValue(table.tableName).jsonArray
            assertTrue(rows.isNotEmpty(), "sample() has no ${table.tableName} row")
            rows.forEach { assertEquals(columns, it.jsonObject.keys, table.tableName) }
        }
    }

    @Test
    fun replaceWritesEveryRowAndSnapshotReadsThemBack() = runBlocking {
        dao.replaceAll(sample())

        val snapshot = dao.snapshot()
        assertEquals(sample().withItemLocations().sorted(), snapshot.sorted())
        // Soft-deleted rows are part of the backup
        assertTrue(snapshot.articles.any { it.id == "gone" && it.deletedAt != null })
        assertEquals("van", snapshot.items.single().locationId)
    }

    @Test
    fun replaceWipesTheExistingRows() = runBlocking {
        dao.replaceAll(sample())
        val other = BackupData(locations = listOf(location("garage")), articles = listOf(article("saw")))

        dao.replaceAll(other)

        assertEquals(other.sorted(), dao.snapshot().sorted())
    }

    @Test
    fun foreignKeysAreEnforcedAndAFailedReplaceChangesNothing() = runBlocking {
        dao.replaceAll(sample())
        val broken = BackupData(articles = listOf(article("a", category = "missing")))

        assertFails { dao.replaceAll(broken) }

        assertEquals(sample().withItemLocations().sorted(), dao.snapshot().sorted())
    }

    @Test
    fun mergeWritesExactlyThePlannedResult() = runBlocking {
        dao.replaceAll(sample())
        val incoming = BackupData(
            // Same name as a local category, other id
            categories = listOf(category("c9", "Tools"), category("c1", "Tools renamed", at = 50)),
            locations = listOf(location("room"), location("garage")),
            articles = listOf(
                article("bolt", sku = "B-2", at = 50),
                // SKU and tag already used here
                article("saw", sku = "D-1", barcode = "04:A2"),
            ),
            items = listOf(item("i2", "saw", serial = "SN1")),
            movements = listOf(movement("m4", "bolt", "shelf", 25_500, at = 8), movement("m9", "saw", "garage", 1000, item = "i2")),
        )

        val plan = dao.merge(incoming)

        assertEquals(plan.result.sorted(), dao.snapshot().sorted())
        val types = plan.conflicts.map { it.type }.toSet()
        assertTrue(ConflictType.SKU_RENAMED in types && ConflictType.BARCODE_CLEARED in types && ConflictType.SERIAL_RENAMED in types, "$types")
        assertEquals("B-2", dao.snapshot().articles.single { it.id == "bolt" }.sku)
        assertEquals("garage", dao.snapshot().items.single { it.id == "i2" }.locationId)
        // Movement already here: not added twice
        assertEquals(5, dao.snapshot().movements.size)
    }

    @Test
    fun mergeCanSwapUniqueValuesBetweenRows() = runBlocking {
        dao.replaceAll(BackupData(articles = listOf(article("a", sku = "X"), article("b", sku = "Y"))))
        val incoming = BackupData(articles = listOf(article("a", sku = "Y", at = 50), article("b", sku = "X", at = 50)))

        val plan = dao.merge(incoming)

        assertEquals(plan.result.sorted(), dao.snapshot().sorted())
    }

    @Test
    fun mergeIntoAnEmptyDatabaseCopiesEverything() = runBlocking {
        val plan = dao.merge(sample())

        assertEquals(sample().withItemLocations().sorted(), dao.snapshot().sorted())
        assertTrue(plan.conflicts.isEmpty())
    }

    @Test
    fun mergingTwiceChangesNothing() = runBlocking {
        dao.replaceAll(sample())

        val plan = dao.merge(sample())

        assertTrue(plan.isEmpty, "$plan")
        assertEquals(sample().withItemLocations().sorted(), dao.snapshot().sorted())
    }
}
