package ch.mathorck.easystock.viewmodels

import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.Tracking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddStockEntryFormTest {
    private fun article(id: String, sku: String = id, tracking: Tracking = Tracking.QUANTITY, archived: Boolean = false) =
        Article(id = id, sku = sku, name = "Article $id", tracking = tracking, archived = archived, createdAt = 0, updatedAt = 0)

    private fun location(id: String, name: String = id, parentId: String? = null) =
        Location(id = id, name = name, parentId = parentId, createdAt = 0, updatedAt = 0)

    private val articles = listOf(article("a1", sku = "BLT-8"), article("s1", tracking = Tracking.SERIAL), article("old", archived = true))
    private val categories = listOf(Category(id = "c1", name = "Tools", createdAt = 0, updatedAt = 0))
    private val locations = listOf(location("l1", "Shelf"))

    private val existingForm = AddStockEntryForm(articleId = "a1", locationId = "l1", quantity = "2")
    private val newForm = AddStockEntryForm(
        articleMode = ChoiceMode.NEW,
        name = "Nail",
        sku = "NL-1",
        locationMode = ChoiceMode.NEW,
        locationName = "Garage",
        quantity = "1,5",
    )

    private fun AddStockEntryForm.errors() = validate(articles, categories, locations)

    // ---------- validate ----------

    @Test
    fun existingArticleAndLocationIsValid() {
        assertTrue(existingForm.errors().isValid)
    }

    @Test
    fun newArticleAndLocationIsValid() {
        assertTrue(newForm.errors().isValid)
    }

    @Test
    fun emptyFormIsInvalid() {
        val errors = AddStockEntryForm().errors()
        assertFalse(errors.isValid)
        assertEquals(FieldError.REQUIRED, errors.article)
        assertEquals(FieldError.REQUIRED, errors.location)
        assertEquals(FieldError.REQUIRED, errors.quantity)
    }

    @Test
    fun serialArchivedOrUnknownArticlesCannotBePicked() {
        listOf("s1", "old", "gone").forEach {
            assertEquals(FieldError.REQUIRED, existingForm.copy(articleId = it).errors().article, it)
        }
    }

    @Test
    fun deletedLocationCannotBePicked() {
        assertEquals(FieldError.REQUIRED, existingForm.copy(locationId = "gone").errors().location)
    }

    @Test
    fun quantityMustBeAPositiveNumber() {
        assertEquals(FieldError.NOT_POSITIVE, existingForm.copy(quantity = "0").errors().quantity)
        assertEquals(FieldError.NOT_POSITIVE, existingForm.copy(quantity = "0,000").errors().quantity)
        assertEquals(FieldError.INVALID_QUANTITY, existingForm.copy(quantity = "-1").errors().quantity)
        assertEquals(FieldError.INVALID_QUANTITY, existingForm.copy(quantity = "1.2345").errors().quantity)
        assertEquals(FieldError.REQUIRED, existingForm.copy(quantity = " ").errors().quantity)
        assertNull(existingForm.copy(quantity = "0.001").errors().quantity)
    }

    @Test
    fun newArticleNeedsNameSkuAndUnit() {
        val errors = newForm.copy(name = " ", sku = "", unit = "").errors()
        assertEquals(FieldError.REQUIRED, errors.name)
        assertEquals(FieldError.REQUIRED, errors.sku)
        assertEquals(FieldError.REQUIRED, errors.unit)
    }

    @Test
    fun newArticleFieldsAreIgnoredWhenPickingAnExistingOne() {
        assertTrue(existingForm.copy(name = "", sku = "BLT-8", unit = "", threshold = "x").errors().isValid)
    }

    @Test
    fun skuMustBeUniqueIgnoringCaseAndSpaces() {
        assertEquals(FieldError.DUPLICATE, newForm.copy(sku = " blt-8 ").errors().sku)
    }

    @Test
    fun skuOfASerialOrArchivedArticleIsTakenToo() {
        assertEquals(FieldError.DUPLICATE, newForm.copy(sku = "s1").errors().sku)
        assertEquals(FieldError.DUPLICATE, newForm.copy(sku = "old").errors().sku)
    }

    @Test
    fun skuRejectedByTheDatabaseIsShownAsDuplicate() {
        val errors = newForm.validate(articles, categories, locations, rejectedSku = "nl-1")
        assertEquals(FieldError.DUPLICATE, errors.sku)
    }

    @Test
    fun tooLongValuesAreRejected() {
        assertEquals(FieldError.TOO_LONG, newForm.copy(sku = "x".repeat(MAX_SKU_LENGTH + 1)).errors().sku)
        assertNull(newForm.copy(sku = "x".repeat(MAX_SKU_LENGTH)).errors().sku)
        assertEquals(FieldError.TOO_LONG, newForm.copy(locationName = "x".repeat(MAX_LOCATION_NAME_LENGTH + 1)).errors().locationName)
    }

    @Test
    fun thresholdIsOptionalAndCanBeZero() {
        assertNull(newForm.copy(threshold = "").errors().threshold)
        assertNull(newForm.copy(threshold = "0").errors().threshold)
        assertEquals(FieldError.INVALID_QUANTITY, newForm.copy(threshold = "abc").errors().threshold)
    }

    @Test
    fun categoryChoice() {
        assertEquals(FieldError.REQUIRED, newForm.copy(categoryMode = CategoryMode.EXISTING).errors().category)
        assertNull(newForm.copy(categoryMode = CategoryMode.EXISTING, categoryId = "c1").errors().category)
        assertEquals(FieldError.REQUIRED, newForm.copy(categoryMode = CategoryMode.NEW).errors().categoryName)
        val rejected = newForm.copy(categoryMode = CategoryMode.NEW, categoryName = "Old")
            .validate(articles, categories, locations, rejectedCategoryName = "old")
        assertEquals(FieldError.DELETED_NAME, rejected.categoryName)
    }

    // ---------- toEntry ----------

    private fun ids(): () -> String {
        var n = 0
        return { "id${n++}" }
    }

    @Test
    fun entryForExistingRowsIsOnlyAMovement() {
        val entry = existingForm.copy(comment = "  ", reason = MovementReason.RETURN).toEntry(categories, now = 42, newId = ids())
        assertNull(entry.newArticle)
        assertNull(entry.newLocation)
        assertNull(entry.newCategory)
        with(entry.movement) {
            assertEquals("a1", articleId)
            assertEquals("l1", locationId)
            assertEquals(2_000L, quantityMilli)
            assertEquals(MovementReason.RETURN, reason)
            assertNull(comment)
            assertEquals(42L, occurredAt)
            assertEquals(42L, createdAt)
        }
    }

    @Test
    fun entryForNewRowsLinksThemTogether() {
        val form = newForm.copy(
            name = " Nail ",
            sku = " NL-1 ",
            unit = " kg ",
            threshold = "0,5",
            categoryMode = CategoryMode.NEW,
            categoryName = " Hardware ",
            locationParentId = "l1",
            comment = " first batch ",
        )
        val entry = form.toEntry(categories, now = 7, newId = ids())
        val article = entry.newArticle!!
        val location = entry.newLocation!!
        val category = entry.newCategory!!
        assertEquals("Hardware", category.name)
        assertEquals(category.id, article.categoryId)
        assertEquals("Nail", article.name)
        assertEquals("NL-1", article.sku)
        assertEquals("kg", article.unit)
        assertEquals(500L, article.thresholdMilli)
        assertEquals(Tracking.QUANTITY, article.tracking)
        assertEquals("Garage", location.name)
        assertEquals("l1", location.parentId)
        assertEquals(article.id, entry.movement.articleId)
        assertEquals(location.id, entry.movement.locationId)
        assertEquals(1_500L, entry.movement.quantityMilli)
        assertEquals("first batch", entry.movement.comment)
        assertEquals(4, setOf(article.id, location.id, category.id, entry.movement.id).size)
    }

    @Test
    fun newCategoryWithAnExistingNameReusesIt() {
        val entry = newForm.copy(categoryMode = CategoryMode.NEW, categoryName = "tools").toEntry(categories, now = 0, newId = ids())
        assertNull(entry.newCategory)
        assertEquals("c1", entry.newArticle!!.categoryId)
    }

    @Test
    fun invalidFormCannotBecomeAnEntry() {
        assertFailsWith<IllegalArgumentException> { existingForm.copy(quantity = "0").toEntry(categories, 0, ids()) }
        assertFailsWith<IllegalArgumentException> {
            existingForm.copy(reason = MovementReason.LOSS).toEntry(categories, 0, ids())
        }
    }

    // ---------- helpers ----------

    @Test
    fun candidatesAreQuantityTrackedAndActive() {
        assertEquals(listOf("a1"), articles.entryCandidates().map { it.id })
    }

    @Test
    fun matchingSearchesNameAndSku() {
        assertEquals(listOf("a1"), articles.matching("blt").map { it.id })
        assertEquals(listOf("s1"), articles.matching("article S").map { it.id })
        assertEquals(3, articles.matching("  ").size)
    }

    @Test
    fun locationPathsFollowTheHierarchy() {
        val options = buildLocationOptions(
            listOf(
                location("bin", "Bin 2", parentId = "shelf"),
                location("shelf", "Shelf A", parentId = "room"),
                location("room", "Workshop"),
                location("attic", "Attic"),
            ),
        )
        assertEquals(listOf("Attic", "Workshop", "Workshop › Shelf A", "Workshop › Shelf A › Bin 2"), options.map { it.path })
        assertEquals(listOf(0, 0, 1, 2), options.map { it.depth })
    }

    @Test
    fun locationWithMissingParentIsTopLevel() {
        val options = buildLocationOptions(listOf(location("shelf", "Shelf", parentId = "deleted")))
        assertEquals("Shelf", options.single().path)
        assertEquals(0, options.single().depth)
    }

    @Test
    fun locationCycleDoesNotLoop() {
        val options = buildLocationOptions(listOf(location("a", "A", parentId = "b"), location("b", "B", parentId = "a")))
        assertEquals(setOf("B › A", "A › B"), options.map { it.path }.toSet())
    }
}
