package ch.mathorck.easystock.backup

import ch.mathorck.easystock.backup.BackupFixtures.article
import ch.mathorck.easystock.backup.BackupFixtures.category
import ch.mathorck.easystock.backup.BackupFixtures.full
import ch.mathorck.easystock.backup.BackupFixtures.item
import ch.mathorck.easystock.backup.BackupFixtures.location
import ch.mathorck.easystock.backup.BackupFixtures.movement
import ch.mathorck.easystock.data.db.DB_VERSION
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupFormatTest {
    private val manifest = BackupManifest(appVersion = "1.0.0", createdAt = "2026-11-10T12:00:00Z")

    @Test
    fun tablesRoundTripWithEveryColumn() {
        val data = full()
        val tables = BackupTable.entries.associateWith { BackupCodec.encodeTable(data, it) }

        assertEquals(data, BackupCodec.decodeTables { tables[it] })
    }

    @Test
    fun schema3ColumnsAreWritten() {
        val data = BackupData(
            categories = listOf(category("c1").copy(nameTemplate = "{color} rope")),
            articles = listOf(article("a1").copy(nameAuto = true)),
        )
        val categories = BackupCodec.encodeTable(data, BackupTable.CATEGORY).decodeToString()
        val articles = BackupCodec.encodeTable(data, BackupTable.ARTICLE).decodeToString()

        assertTrue("\"name_template\":\"{color} rope\"" in categories)
        assertTrue("\"name_auto\":true" in articles)
        assertEquals(data, BackupCodec.decodeTables { BackupCodec.encodeTable(data, it) })
    }

    /** A schema 2 backup has no name_template nor name_auto: null and false. */
    @Test
    fun schema2TablesDecodeWithTheNewColumnsDefaults() {
        val data = BackupData(
            categories = listOf(category("c1").copy(nameTemplate = "{color} rope")),
            articles = listOf(article("a1").copy(nameAuto = true)),
        )
        fun withoutColumn(table: BackupTable, column: String): ByteArray {
            val rows = backupJson.parseToJsonElement(BackupCodec.encodeTable(data, table).decodeToString()).jsonArray
            val stripped = JsonArray(rows.map { row -> JsonObject(row.jsonObject - column) })
            return stripped.toString().encodeToByteArray()
        }
        val tables = mapOf(
            BackupTable.CATEGORY to withoutColumn(BackupTable.CATEGORY, "name_template"),
            BackupTable.ARTICLE to withoutColumn(BackupTable.ARTICLE, "name_auto"),
        )
        BackupCodec.checkManifest(manifest.copy(schemaVersion = 2))

        val decoded = BackupCodec.decodeTables { tables[it] }

        assertNull(decoded.categories.single().nameTemplate)
        assertEquals(false, decoded.articles.single().nameAuto)
        assertEquals(data.articles.single().copy(nameAuto = false), decoded.articles.single())
    }

    @Test
    fun missingTableFileIsAnEmptyTable() {
        assertEquals(BackupData(), BackupCodec.decodeTables { null })
    }

    @Test
    fun columnsUseSchemaNamesAndEnumsTheirDatabaseValue() {
        val json = BackupCodec.encodeTable(full(), BackupTable.ARTICLE).decodeToString()

        assertTrue("\"image_path\":\"images/" in json)
        assertTrue("\"threshold\":1500" in json)
        assertTrue("\"tracking\":\"serial\"" in json)
        assertTrue("\"deleted_at\":null" in json)
        val movements = BackupCodec.encodeTable(full(), BackupTable.STOCK_MOVEMENT).decodeToString()
        assertTrue("\"reason\":\"loan\"" in movements)
        assertTrue("\"quantity\":-1000" in movements)
    }

    @Test
    fun unknownEnumValueIsDamaged() {
        val json = BackupCodec.encodeTable(full(), BackupTable.ARTICLE).decodeToString().replace("\"serial\"", "\"weight\"")

        val error = assertFailsWith<BackupException> {
            BackupCodec.decodeTables { if (it == BackupTable.ARTICLE) json.encodeToByteArray() else null }
        }
        assertEquals(BackupError.DAMAGED, error.error)
    }

    @Test
    fun invalidJsonIsDamaged() {
        val error = assertFailsWith<BackupException> { BackupCodec.decodeTables { "[{".encodeToByteArray() } }
        assertEquals(BackupError.DAMAGED, error.error)
    }

    @Test
    fun bundleRoundTrip() {
        val bundle = BackupBundle(manifest.copy(counts = full().counts()), full())

        assertEquals(bundle, BackupCodec.decodeBundle(BackupCodec.encodeBundle(bundle)))
    }

    // ---------- Manifest ----------

    @Test
    fun manifestRoundTrip() {
        assertEquals(manifest, BackupCodec.decodeManifest(BackupCodec.encodeManifest(manifest)))
    }

    @Test
    fun manifestOfCurrentVersionsIsAccepted() {
        BackupCodec.checkManifest(manifest)
        assertEquals(DB_VERSION, manifest.schemaVersion)
        assertEquals(3, DB_VERSION)
        assertEquals(BACKUP_FORMAT_VERSION, manifest.formatVersion)
    }

    @Test
    fun olderSchemaIsAccepted() {
        BackupCodec.checkManifest(manifest.copy(schemaVersion = 1))
    }

    @Test
    fun newerSchemaIsRejected() {
        val error = assertFailsWith<BackupException> { BackupCodec.checkManifest(manifest.copy(schemaVersion = DB_VERSION + 1)) }
        assertEquals(BackupError.NEWER_VERSION, error.error)
    }

    @Test
    fun newerFormatIsRejected() {
        val error = assertFailsWith<BackupException> { BackupCodec.checkManifest(manifest.copy(formatVersion = BACKUP_FORMAT_VERSION + 1)) }
        assertEquals(BackupError.NEWER_VERSION, error.error)
    }

    @Test
    fun otherFormatIsNotABackup() {
        val error = assertFailsWith<BackupException> { BackupCodec.checkManifest(manifest.copy(format = "other")) }
        assertEquals(BackupError.NOT_A_BACKUP, error.error)
    }

    @Test
    fun zeroVersionIsDamaged() {
        val error = assertFailsWith<BackupException> { BackupCodec.checkManifest(manifest.copy(schemaVersion = 0)) }
        assertEquals(BackupError.DAMAGED, error.error)
    }

    @Test
    fun unreadableManifestIsNotABackup() {
        val error = assertFailsWith<BackupException> { BackupCodec.decodeManifest("hello".encodeToByteArray()) }
        assertEquals(BackupError.NOT_A_BACKUP, error.error)
    }

    // ---------- Validation ----------

    @Test
    fun fullBackupIsValid() {
        validateBackup(full())
    }

    @Test
    fun missingReferenceIsDamaged() {
        val data = BackupData(articles = listOf(article("a", category = "nope")))
        assertEquals(BackupError.DAMAGED, assertFailsWith<BackupException> { validateBackup(data) }.error)
    }

    @Test
    fun movementOfUnknownLocationIsDamaged() {
        val data = BackupData(articles = listOf(article("a")), movements = listOf(movement("m", "a", "nowhere", 1000)))
        assertEquals(BackupError.DAMAGED, assertFailsWith<BackupException> { validateBackup(data) }.error)
    }

    @Test
    fun duplicateUniqueValueIsDamaged() {
        val data = BackupData(categories = listOf(category("c1", "Tools"), category("c2", "Tools")))
        assertEquals(BackupError.DAMAGED, assertFailsWith<BackupException> { validateBackup(data) }.error)
    }

    @Test
    fun locationCycleIsDamaged() {
        val data = BackupData(locations = listOf(location("a", parent = "b"), location("b", parent = "a")))
        assertEquals(BackupError.DAMAGED, assertFailsWith<BackupException> { validateBackup(data) }.error)
    }

    @Test
    fun imagePathOutsideThePhotoFolderIsDamaged() {
        val data = BackupData(articles = listOf(article("a", image = "../databases/easystock.db")))
        assertEquals(BackupError.DAMAGED, assertFailsWith<BackupException> { validateBackup(data) }.error)
    }

    @Test
    fun parentsComeFirst() {
        val sorted = parentsFirst(listOf(location("bin", "shelf"), location("shelf", "room"), location("room")))
        assertEquals(listOf("room", "shelf", "bin"), sorted.map { it.id })
    }

    // ---------- Item location cache ----------

    @Test
    fun itemLocationIsWhereItsMovementsSumIsPositive() {
        val movements = listOf(
            movement("m1", "a", "shelf", 1000, item = "i"),
            movement("m2", "a", "shelf", -1000, item = "i"),
            movement("m3", "a", "van", 1000, item = "i"),
        )
        val locations = itemLocations(listOf(item("i", "a"), item("j", "a")), movements)

        assertEquals("van", locations["i"])
        assertNull(locations["j"])
    }

    @Test
    fun itemOutOfStockHasNoLocation() {
        val movements = listOf(movement("m1", "a", "shelf", 1000, item = "i"), movement("m2", "a", "shelf", -1000, item = "i"))
        val data = BackupData(items = listOf(item("i", "a", location = "shelf")), movements = movements).withItemLocations()

        assertNull(data.items.single().locationId)
    }
}
