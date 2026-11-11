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
    fun schema4LocationColumnsAreWritten() {
        val data = BackupData(locations = listOf(location("l1").copy(icon = "warehouse", color = "teal")))
        val locations = BackupCodec.encodeTable(data, BackupTable.LOCATION).decodeToString()

        assertTrue("\"icon\":\"warehouse\"" in locations)
        assertTrue("\"color\":\"teal\"" in locations)
        assertEquals(data, BackupCodec.decodeTables { BackupCodec.encodeTable(data, it) })
    }

    /** A schema 3 backup has no icon nor color on its locations: both null (default look). */
    @Test
    fun schema3LocationsDecodeWithTheDefaultAppearance() {
        val data = BackupData(locations = listOf(location("l1").copy(icon = "warehouse", color = "teal")))
        val rows = backupJson.parseToJsonElement(BackupCodec.encodeTable(data, BackupTable.LOCATION).decodeToString()).jsonArray
        val stripped = JsonArray(rows.map { JsonObject(it.jsonObject - "icon" - "color") }).toString().encodeToByteArray()
        BackupCodec.checkManifest(manifest.copy(schemaVersion = 3))

        val decoded = BackupCodec.decodeTables { if (it == BackupTable.LOCATION) stripped else null }

        assertEquals(location("l1"), decoded.locations.single())
    }

    @Test
    fun schema6LocationTagIsWritten() {
        val locations = BackupCodec.encodeTable(full(), BackupTable.LOCATION).decodeToString()

        assertTrue("\"barcode\":\"04:B5:6C\"" in locations)
    }

    /** A schema 5 backup has no tag on its locations: null. */
    @Test
    fun schema5LocationsDecodeWithoutTag() {
        val data = BackupData(locations = listOf(location("l1").copy(barcode = "TAG")))
        val rows = backupJson.parseToJsonElement(BackupCodec.encodeTable(data, BackupTable.LOCATION).decodeToString()).jsonArray
        val stripped = JsonArray(rows.map { JsonObject(it.jsonObject - "barcode") }).toString().encodeToByteArray()
        BackupCodec.checkManifest(manifest.copy(schemaVersion = 5))

        val decoded = BackupCodec.decodeTables { if (it == BackupTable.LOCATION) stripped else null }

        assertEquals(location("l1"), decoded.locations.single())
    }

    @Test
    fun schema5ReviewTablesAreWritten() {
        val reviews = BackupCodec.encodeTable(full(), BackupTable.STOCK_REVIEW).decodeToString()
        val lines = BackupCodec.encodeTable(full(), BackupTable.STOCK_REVIEW_LINE).decodeToString()

        assertTrue("\"status\":\"finished\"" in reviews)
        assertTrue("\"result\":\"elsewhere\"" in lines)
        assertTrue("\"expected_quantity\":2500" in lines)
        assertTrue("\"found_location_id\":\"room\"" in lines)
    }

    /** A new unit found during a review is kept in a backup, and a review line written before has none. */
    @Test
    fun pendingUnitOfAReviewLineRoundTripsAndOlderLinesHaveNone() {
        val pending = ch.mathorck.easystock.data.model.PendingUnit("SN-9", ch.mathorck.easystock.data.model.ItemStatus.REPAIR).toJson()
        val line = full().reviewLines.first().copy(itemId = null, pendingItem = pending)
        val data = BackupData(reviewLines = listOf(line))

        val encoded = BackupCodec.encodeTable(data, BackupTable.STOCK_REVIEW_LINE).decodeToString()
        assertTrue("\"pending_item\":{" in encoded)
        assertEquals(line, BackupCodec.decodeTables { if (it == BackupTable.STOCK_REVIEW_LINE) encoded.encodeToByteArray() else null }.reviewLines.single())

        val rows = backupJson.parseToJsonElement(encoded).jsonArray
        val old = JsonArray(rows.map { row -> JsonObject(row.jsonObject - "pending_item") }).toString()
        val decoded = BackupCodec.decodeTables { if (it == BackupTable.STOCK_REVIEW_LINE) old.encodeToByteArray() else null }
        assertNull(decoded.reviewLines.single().pendingItem)
    }

    /** A schema 4 backup has no review tables: none, and the rest is read as before. */
    @Test
    fun schema4BackupHasNoReviews() {
        val data = full()
        val tables = BackupTable.entries
            .filter { it != BackupTable.STOCK_REVIEW && it != BackupTable.STOCK_REVIEW_LINE }
            .associateWith { BackupCodec.encodeTable(data, it) }
        BackupCodec.checkManifest(manifest.copy(schemaVersion = 4))

        val decoded = BackupCodec.decodeTables { tables[it] }

        assertEquals(data.copy(reviews = emptyList(), reviewLines = emptyList()), decoded)
        validateBackup(decoded)
    }

    @Test
    fun reviewLineOfUnknownReviewIsDamaged() {
        val data = full().copy(reviews = emptyList())
        assertEquals(BackupError.DAMAGED, assertFailsWith<BackupException> { validateBackup(data) }.error)
    }

    @Test
    fun reviewOfUnknownLocationIsDamaged() {
        val data = full().let { it.copy(reviews = it.reviews.map { r -> r.copy(locationId = "nowhere") }) }
        assertEquals(BackupError.DAMAGED, assertFailsWith<BackupException> { validateBackup(data) }.error)
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
    fun measureAttributesRoundTrip() {
        val base = full()
        val length = base.attributeDefinitions.single().copy(
            id = "a2", key = "length", label = "Length", dataType = ch.mathorck.easystock.data.model.AttributeDataType.MEASURE, unit = "m", options = null,
        )
        val data = base.copy(
            attributeDefinitions = base.attributeDefinitions + length,
            articles = base.articles.map { article ->
                article.copy(attributes = article.attributes?.let { JsonObject(it + ("length" to kotlinx.serialization.json.Json.parseToJsonElement("""{"value":9.8,"unit":"mm"}"""))) })
            },
        )
        val tables = BackupTable.entries.associateWith { BackupCodec.encodeTable(data, it) }

        assertTrue("\"data_type\":\"measure\"" in tables.getValue(BackupTable.ATTRIBUTE_DEFINITION).decodeToString())
        assertEquals(data, BackupCodec.decodeTables { tables[it] })
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
        assertEquals(6, DB_VERSION)
        assertEquals(BACKUP_FORMAT_VERSION, manifest.formatVersion)
    }

    @Test
    fun olderSchemaIsAccepted() {
        BackupCodec.checkManifest(manifest.copy(schemaVersion = 1))
        BackupCodec.checkManifest(manifest.copy(schemaVersion = 4))
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
    fun tagOnAnArticleAndALocationIsDamaged() {
        val data = BackupData(locations = listOf(location("crate").copy(barcode = "TAG")), articles = listOf(article("a", barcode = "TAG")))
        assertEquals(BackupError.DAMAGED, assertFailsWith<BackupException> { validateBackup(data) }.error)
    }

    @Test
    fun locationTagsAreAccepted() {
        val data = BackupData(locations = listOf(location("crate").copy(barcode = "TAG"), location("box")), articles = listOf(article("a", barcode = "OTHER")))
        validateBackup(data)
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
