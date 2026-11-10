package ch.mathorck.easystock.backup

import ch.mathorck.easystock.backup.BackupFixtures.article
import ch.mathorck.easystock.backup.BackupFixtures.attribute
import ch.mathorck.easystock.backup.BackupFixtures.category
import ch.mathorck.easystock.backup.BackupFixtures.item
import ch.mathorck.easystock.backup.BackupFixtures.link
import ch.mathorck.easystock.backup.BackupFixtures.location
import ch.mathorck.easystock.backup.BackupFixtures.movement
import ch.mathorck.easystock.composables.attributes.MeasureUnits
import ch.mathorck.easystock.composables.attributes.MeasureValue
import ch.mathorck.easystock.composables.attributes.readMeasure
import ch.mathorck.easystock.data.model.AttributeDataType
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupMergeTest {
    private fun conflictTypes(plan: MergePlan) = plan.conflicts.map { it.type }

    @Test
    fun rowsOnOneSideOnlyAreAdded() {
        val local = BackupData(categories = listOf(category("c1")))
        val incoming = BackupData(categories = listOf(category("c2")))

        val plan = planMerge(local, incoming)

        assertEquals(listOf("c2"), plan.inserts.categories.map { it.id })
        assertTrue(plan.updates.isEmpty)
        assertEquals(setOf("c1", "c2"), plan.result.categories.map { it.id }.toSet())
    }

    @Test
    fun newerIncomingRowWins() {
        val local = BackupData(categories = listOf(category("c1", "Old name", at = 10)))
        val incoming = BackupData(categories = listOf(category("c1", "New name", at = 20)))

        val plan = planMerge(local, incoming)

        assertEquals("New name", plan.updates.categories.single().name)
        assertTrue(plan.inserts.isEmpty)
    }

    @Test
    fun newerLocalRowIsKept() {
        val local = BackupData(categories = listOf(category("c1", "Local", at = 20)))
        val incoming = BackupData(categories = listOf(category("c1", "Incoming", at = 10)))

        assertTrue(planMerge(local, incoming).isEmpty)
    }

    @Test
    fun tieKeepsLocalRow() {
        val local = BackupData(categories = listOf(category("c1", "Local", at = 10)))
        val incoming = BackupData(categories = listOf(category("c1", "Incoming", at = 10)))

        assertTrue(planMerge(local, incoming).isEmpty)
    }

    @Test
    fun identicalBackupChangesNothing() {
        val data = BackupFixtures.full().withItemLocations()

        val plan = planMerge(data, data)

        assertTrue(plan.isEmpty)
        assertTrue(plan.conflicts.isEmpty())
    }

    @Test
    fun newerSoftDeleteWins() {
        val local = BackupData(articles = listOf(article("a", at = 10)))
        val incoming = BackupData(articles = listOf(article("a", at = 20, deletedAt = 20)))

        assertEquals(20L, planMerge(local, incoming).updates.articles.single().deletedAt)
    }

    @Test
    fun olderSoftDeleteLosesToANewerEdit() {
        val local = BackupData(articles = listOf(article("a", name = "Edited", at = 30)))
        val incoming = BackupData(articles = listOf(article("a", at = 20, deletedAt = 20)))

        val plan = planMerge(local, incoming)

        assertTrue(plan.isEmpty)
        assertNull(plan.result.articles.single().deletedAt)
    }

    @Test
    fun deletedRowsAreImportedToo() {
        val incoming = BackupData(categories = listOf(category("c1", deletedAt = 5)))

        assertEquals(5L, planMerge(BackupData(), incoming).inserts.categories.single().deletedAt)
    }

    // ---------- Movements ----------

    @Test
    fun missingMovementsAreAppended() {
        val base = BackupData(articles = listOf(article("a")), locations = listOf(location("shelf")))
        val local = base.copy(movements = listOf(movement("m1", "a", "shelf", 1000)))
        val incoming = base.copy(movements = listOf(movement("m1", "a", "shelf", 1000), movement("m2", "a", "shelf", 500)))

        val plan = planMerge(local, incoming)

        assertEquals(listOf("m2"), plan.inserts.movements.map { it.id })
        assertTrue(plan.updates.movements.isEmpty())
        assertEquals(1500L, plan.result.movements.sumOf { it.quantityMilli })
    }

    @Test
    fun existingMovementIsNeverChanged() {
        val base = BackupData(articles = listOf(article("a")), locations = listOf(location("shelf")))
        val local = base.copy(movements = listOf(movement("m1", "a", "shelf", 1000)))
        val incoming = base.copy(movements = listOf(movement("m1", "a", "shelf", 9000, at = 99)))

        val plan = planMerge(local, incoming)

        assertTrue(plan.inserts.movements.isEmpty())
        assertTrue(plan.updates.movements.isEmpty())
        assertEquals(1000L, plan.result.movements.single().quantityMilli)
        assertEquals(listOf(ConflictType.MOVEMENT_KEPT), conflictTypes(plan))
    }

    @Test
    fun localMovementsMissingFromTheBackupStay() {
        val base = BackupData(articles = listOf(article("a")), locations = listOf(location("shelf")))
        val local = base.copy(movements = listOf(movement("m1", "a", "shelf", 1000)))

        assertEquals(listOf("m1"), planMerge(local, base).result.movements.map { it.id })
    }

    // ---------- Unique columns ----------

    @Test
    fun categoryNameTakenRenamesTheImportedOne() {
        val local = BackupData(categories = listOf(category("c1", "Tools")))
        val incoming = BackupData(categories = listOf(category("c2", "tools")))

        val plan = planMerge(local, incoming)

        assertEquals("tools (2)", plan.inserts.categories.single().name)
        assertEquals(MergeConflict(ConflictType.CATEGORY_RENAMED, BackupTable.CATEGORY, "c2", "tools", "tools (2)"), plan.conflicts.single())
    }

    @Test
    fun renameSkipsSuffixesAlreadyUsed() {
        val local = BackupData(categories = listOf(category("c1", "Tools"), category("c3", "Tools (2)")))
        val incoming = BackupData(categories = listOf(category("c2", "Tools")))

        assertEquals("Tools (3)", planMerge(local, incoming).inserts.categories.single().name)
    }

    @Test
    fun renamedNameKeepsTheMaxLength() {
        val long = "x".repeat(MAX_CATEGORY_NAME_LENGTH)
        val local = BackupData(categories = listOf(category("c1", long)))
        val incoming = BackupData(categories = listOf(category("c2", long)))

        val name = planMerge(local, incoming).inserts.categories.single().name
        assertEquals(MAX_CATEGORY_NAME_LENGTH, name.length)
        assertTrue(name.endsWith(" (2)"))
    }

    @Test
    fun updateFreesANameForAnotherRow() {
        // Local c1 "Tools" is renamed to "Hand tools" by the backup, which also brings a new c2 "Tools"
        val local = BackupData(categories = listOf(category("c1", "Tools", at = 10)))
        val incoming = BackupData(categories = listOf(category("c1", "Hand tools", at = 20), category("c2", "Tools")))

        val plan = planMerge(local, incoming)

        assertTrue(plan.conflicts.isEmpty())
        assertEquals(setOf("Hand tools", "Tools"), plan.result.categories.map { it.name }.toSet())
    }

    @Test
    fun barcodeTakenIsClearedOnTheImportedArticle() {
        val local = BackupData(articles = listOf(article("a1", barcode = "TAG")))
        val incoming = BackupData(articles = listOf(article("a2", name = "Imported", barcode = "TAG")))

        val plan = planMerge(local, incoming)

        assertNull(plan.inserts.articles.single().barcode)
        assertEquals("TAG", plan.result.articles.single { it.id == "a1" }.barcode)
        assertEquals(MergeConflict(ConflictType.BARCODE_CLEARED, BackupTable.ARTICLE, "a2", "TAG", "Imported"), plan.conflicts.single())
    }

    @Test
    fun barcodeOfADeletedArticleIsFree() {
        // softDeleted() clears the barcode, so the tag can move to the imported article
        val local = BackupData(articles = listOf(article("a1", at = 5).softDeleted(now = 6)))
        val incoming = BackupData(articles = listOf(article("a2", barcode = "TAG")))

        assertEquals("TAG", planMerge(local, incoming).inserts.articles.single().barcode)
    }

    @Test
    fun skuTakenGetsASuffix() {
        val local = BackupData(articles = listOf(article("a1", sku = "S-1")))
        val incoming = BackupData(articles = listOf(article("a2", sku = "s-1")))

        val plan = planMerge(local, incoming)

        assertEquals("s-1 (2)", plan.inserts.articles.single().sku)
        assertEquals(listOf(ConflictType.SKU_RENAMED), conflictTypes(plan))
    }

    @Test
    fun serialTakenGetsASuffix() {
        val base = BackupData(articles = listOf(article("a")))
        val local = base.copy(items = listOf(item("i1", "a", serial = "SN-1")))
        val incoming = base.copy(items = listOf(item("i2", "a", serial = "SN-1")))

        val plan = planMerge(local, incoming)

        assertEquals("SN-1 (2)", plan.inserts.items.single().serialNumber)
        assertEquals(listOf(ConflictType.SERIAL_RENAMED), conflictTypes(plan))
    }

    @Test
    fun sameAttributeKeyIsTheSameAttribute() {
        val local = BackupData(categories = listOf(category("c1")), attributeDefinitions = listOf(attribute("a-local", key = "color", label = "Colour", at = 10)))
        val incoming = BackupData(
            categories = listOf(category("c1")),
            attributeDefinitions = listOf(attribute("a-remote", key = "color", label = "Color", at = 20)),
            categoryAttributes = listOf(link("l1", "c1", "a-remote")),
        )

        val plan = planMerge(local, incoming)

        // No second "color": the link uses the local id, the newer label wins
        assertTrue(plan.inserts.attributeDefinitions.isEmpty())
        assertEquals("a-local", plan.inserts.categoryAttributes.single().attributeId)
        val merged = plan.updates.attributeDefinitions.single()
        assertEquals("a-local", merged.id)
        assertEquals("Color", merged.label)
        assertEquals(listOf(ConflictType.ATTRIBUTE_MERGED), conflictTypes(plan))
    }

    @Test
    fun attributeKeyNeverChanges() {
        val local = BackupData(attributeDefinitions = listOf(attribute("a1", key = "color", label = "Color", at = 10)))
        val incoming = BackupData(attributeDefinitions = listOf(attribute("a1", key = "colour", label = "Colour", at = 20)))

        val plan = planMerge(local, incoming)

        val updated = plan.updates.attributeDefinitions.single()
        assertEquals("color", updated.key)
        assertEquals("Colour", updated.label)
        assertEquals(listOf(ConflictType.ATTRIBUTE_KEY_KEPT), conflictTypes(plan))
    }

    @Test
    fun categoryAttributePairTakenIsSkipped() {
        val base = BackupData(categories = listOf(category("c1")), attributeDefinitions = listOf(attribute("a1")))
        val local = base.copy(categoryAttributes = listOf(link("l1", "c1", "a1")))
        val incoming = base.copy(categoryAttributes = listOf(link("l2", "c1", "a1")))

        val plan = planMerge(local, incoming)

        assertTrue(plan.inserts.categoryAttributes.isEmpty())
        assertEquals(listOf(ConflictType.CATEGORY_ATTRIBUTE_SKIPPED), conflictTypes(plan))
    }

    @Test
    fun conflictsResolveTheSameWayEveryTime() {
        val local = BackupData(categories = listOf(category("c1", "A"), category("c2", "B")))
        val incoming = BackupData(categories = listOf(category("c4", "A"), category("c3", "a"), category("c5", "B")))

        val first = planMerge(local, incoming)
        val second = planMerge(local, incoming.copy(categories = incoming.categories.reversed()))

        assertEquals(first, second)
        assertEquals(mapOf("c3" to "a (2)", "c4" to "A (3)", "c5" to "B (2)"), first.inserts.categories.associate { it.id to it.name })
    }

    // ---------- Locations ----------

    @Test
    fun newLocationsComeParentsFirst() {
        val incoming = BackupData(locations = listOf(location("bin", parent = "shelf"), location("shelf", parent = "room"), location("room")))

        assertEquals(listOf("room", "shelf", "bin"), planMerge(BackupData(), incoming).inserts.locations.map { it.id })
    }

    @Test
    fun parentCycleKeepsTheLocalParent() {
        // Local: b in a. Backup (newer): a in b. Both together would be a cycle
        val local = BackupData(locations = listOf(location("a", at = 10), location("b", parent = "a", at = 10)))
        val incoming = BackupData(locations = listOf(location("a", parent = "b", at = 20), location("b", parent = "a", at = 10)))

        val plan = planMerge(local, incoming)

        assertNull(locationCycle(plan.result.locations))
        assertNull(plan.result.locations.single { it.id == "a" }.parentId)
        assertEquals(listOf(ConflictType.LOCATION_PARENT_KEPT), conflictTypes(plan))
    }

    // ---------- Item location cache ----------

    @Test
    fun itemLocationIsRecomputedFromMergedMovements() {
        // Local: unit on the shelf. Backup: the same unit was moved to the van (older item row, new movements)
        val base = BackupData(articles = listOf(article("a")), locations = listOf(location("shelf"), location("van")))
        val bought = movement("m1", "a", "shelf", 1000, item = "i")
        val local = base.copy(items = listOf(item("i", "a", location = "shelf", at = 30)), movements = listOf(bought))
        val incoming = base.copy(
            items = listOf(item("i", "a", location = "van", at = 20)),
            movements = listOf(bought, movement("m2", "a", "shelf", -1000, item = "i"), movement("m3", "a", "van", 1000, item = "i")),
        )

        val plan = planMerge(local, incoming)

        assertEquals(mapOf("i" to "van"), plan.itemLocationFixes)
        assertEquals("van", plan.result.items.single().locationId)
        assertTrue(plan.updates.items.isEmpty())
    }

    @Test
    fun insertedItemGetsItsLocationFromMovements() {
        val base = BackupData(articles = listOf(article("a")), locations = listOf(location("shelf")))
        val incoming = base.copy(
            items = listOf(item("i", "a", location = null)),
            movements = listOf(movement("m1", "a", "shelf", 1000, item = "i")),
        )

        assertEquals("shelf", planMerge(base, incoming).inserts.items.single().locationId)
    }

    // ---------- Photos ----------

    @Test
    fun replacedPhotoIsReleasedAndNewOneNeeded() {
        val old = "images/00000000-0000-0000-0000-000000000001.jpg"
        val new = "images/00000000-0000-0000-0000-000000000002.jpg"
        val local = BackupData(articles = listOf(article("a", image = old, at = 10)))
        val incoming = BackupData(articles = listOf(article("a", image = new, at = 20)))

        val plan = planMerge(local, incoming)

        assertEquals(setOf(new), plan.photosNeeded)
        assertEquals(setOf(old), plan.photosReleased)
    }

    @Test
    fun keptPhotoIsNotReleased() {
        val photo = "images/00000000-0000-0000-0000-000000000001.jpg"
        val local = BackupData(articles = listOf(article("a", image = photo, at = 10)))
        val incoming = BackupData(articles = listOf(article("a", name = "Renamed", image = photo, at = 20)))

        assertTrue(planMerge(local, incoming).photosReleased.isEmpty())
    }

    private fun number(id: String, unit: String?, at: Long) = attribute(id, key = "length", label = "Length", at = at).copy(dataType = AttributeDataType.NUMBER, unit = unit)
    private fun measure(id: String, unit: String, at: Long) = attribute(id, key = "length", label = "Length", at = at).copy(dataType = AttributeDataType.MEASURE, unit = unit)

    @Test
    fun oldBackupWithPlainNumbersKeepsTheLocalMeasure() {
        val local = BackupData(attributeDefinitions = listOf(measure("a1", "m", at = 20)))
        val old = article("rope").copy(attributes = buildJsonObject { put("length", JsonPrimitive(50)) })
        val incoming = BackupData(attributeDefinitions = listOf(number("a1", "m", at = 10)), articles = listOf(old))

        val plan = planMerge(local, incoming)

        assertTrue(plan.updates.attributeDefinitions.isEmpty())
        val definition = plan.result.attributeDefinitions.single()
        assertEquals(AttributeDataType.MEASURE, definition.dataType)
        // The plain number is read in the default unit
        val value = plan.result.articles.single().attributes!!["length"]
        assertEquals(MeasureValue("50", MeasureUnits.find("m")!!), readMeasure(definition, value))
    }

    @Test
    fun newerNumberDoesNotDowngradeAMeasure() {
        val local = BackupData(attributeDefinitions = listOf(measure("a1", "m", at = 10)))
        val incoming = BackupData(attributeDefinitions = listOf(number("a1", "m", at = 20).copy(label = "Rope length")))

        val updated = planMerge(local, incoming).updates.attributeDefinitions.single()

        assertEquals("Rope length", updated.label)
        assertEquals(AttributeDataType.MEASURE, updated.dataType)
        assertEquals("m", updated.unit)
    }

    @Test
    fun olderIncomingMeasureUpgradesALocalNumber() {
        val local = BackupData(attributeDefinitions = listOf(number("a1", null, at = 20)))
        val incoming = BackupData(attributeDefinitions = listOf(measure("a1", "mm", at = 10)))

        val updated = planMerge(local, incoming).updates.attributeDefinitions.single()

        assertEquals(AttributeDataType.MEASURE, updated.dataType)
        assertEquals("mm", updated.unit)
        assertEquals(20, updated.updatedAt)
    }

    @Test
    fun sameKeyMeasureWinsOverNumber() {
        val local = BackupData(attributeDefinitions = listOf(number("a-local", "cm", at = 20)))
        val incoming = BackupData(attributeDefinitions = listOf(measure("a-remote", "cm", at = 10)))

        val plan = planMerge(local, incoming)

        assertTrue(plan.inserts.attributeDefinitions.isEmpty())
        val merged = plan.updates.attributeDefinitions.single()
        assertEquals("a-local", merged.id)
        assertEquals(AttributeDataType.MEASURE, merged.dataType)
    }
}
