package ch.mathorck.easystock.viewmodels

import ch.mathorck.easystock.composables.attributes.AttributeError
import ch.mathorck.easystock.data.db.CategoryAttributeWithDefinition
import ch.mathorck.easystock.data.db.SkuOwner
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.AttributeDefinition
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.CategoryAttribute
import ch.mathorck.easystock.data.model.Tracking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArticleFormTest {
    private val article = Article(
        id = "a1", sku = "BLT-8", name = "Bolt", description = "Zinc", categoryId = "c1", unit = "pce",
        thresholdMilli = 1_500, createdAt = 0, updatedAt = 0,
    )
    private val categories = listOf(
        Category(id = "c1", name = "Hardware", createdAt = 0, updatedAt = 0),
        Category(id = "c2", name = "Tools", createdAt = 0, updatedAt = 0),
    )
    private val skus = listOf(SkuOwner("a1", "BLT-8"), SkuOwner("a2", "CT-200"), SkuOwner("deleted", "OLD-1"))

    private fun ArticleForm.errors(rejectedSku: String? = null, rejectedCategoryName: String? = null) =
        validate(article, skus, categories, rejectedSku, rejectedCategoryName)

    private val form = article.toForm()

    @Test
    fun formIsPrefilledFromTheArticle() {
        assertEquals(
            ArticleForm(
                name = "Bolt", sku = "BLT-8", description = "Zinc", unit = "pce",
                categoryMode = CategoryMode.EXISTING, categoryId = "c1", threshold = "1.5", nameAuto = false,
            ),
            form,
        )
        assertTrue(form.errors().isValid)
    }

    @Test
    fun articleWithoutOptionalValuesGivesEmptyFields() {
        val bare = article.copy(sku = null, description = null, categoryId = null, thresholdMilli = null).toForm()

        assertEquals("", bare.sku)
        assertEquals("", bare.description)
        assertEquals(CategoryMode.NONE, bare.categoryMode)
        assertEquals("", bare.threshold)
    }

    @Test
    fun nameAndUnitAreRequiredAndLimited() {
        val errors = form.copy(name = "  ", unit = "").errors()
        assertEquals(FieldError.REQUIRED, errors.name)
        assertEquals(FieldError.REQUIRED, errors.unit)
        assertFalse(errors.isValid)

        val tooLong = form.copy(name = "x".repeat(MAX_NAME_LENGTH + 1), unit = "u".repeat(MAX_UNIT_LENGTH + 1)).errors()
        assertEquals(FieldError.TOO_LONG, tooLong.name)
        assertEquals(FieldError.TOO_LONG, tooLong.unit)
    }

    @Test
    fun skuIsOptionalAndLimited() {
        assertNull(form.copy(sku = "   ").errors().sku)
        assertEquals(FieldError.TOO_LONG, form.copy(sku = "S".repeat(MAX_SKU_LENGTH + 1)).errors().sku)
        assertNull(form.copy(sku = " ${"S".repeat(MAX_SKU_LENGTH)} ").errors().sku)
    }

    @Test
    fun skuMustBeUniqueIgnoringCaseButNotAgainstItself() {
        assertNull(form.copy(sku = "blt-8").errors().sku)
        assertEquals(FieldError.DUPLICATE, form.copy(sku = "ct-200").errors().sku)
        // Soft-deleted articles keep their SKU
        assertEquals(FieldError.DUPLICATE, form.copy(sku = "OLD-1").errors().sku)
    }

    @Test
    fun skuRejectedByTheDatabaseIsShownAsDuplicate() {
        assertEquals(FieldError.DUPLICATE, form.copy(sku = "new-9").errors(rejectedSku = "NEW-9").sku)
        assertNull(form.copy(sku = "NEW-10").errors(rejectedSku = "NEW-9").sku)
    }

    @Test
    fun thresholdIsOptionalAndZeroIsAllowed() {
        assertNull(form.copy(threshold = "").errors().threshold)
        assertNull(form.copy(threshold = "0").errors().threshold)
        assertNull(form.copy(threshold = "2,5").errors().threshold)
        assertEquals(FieldError.INVALID_QUANTITY, form.copy(threshold = "-1").errors().threshold)
        assertEquals(FieldError.INVALID_QUANTITY, form.copy(threshold = "1.2345").errors().threshold)
        assertEquals(FieldError.INVALID_QUANTITY, form.copy(threshold = "abc").errors().threshold)
    }

    @Test
    fun existingCategoryMustBeActiveUnlessUnchanged() {
        assertNull(form.copy(categoryId = "c2").errors().category)
        assertEquals(FieldError.REQUIRED, form.copy(categoryId = "gone").errors().category)
        assertEquals(FieldError.REQUIRED, form.copy(categoryId = null).errors().category)
        // The stored category was soft-deleted meanwhile: keeping it stays valid
        val deletedCategory = article.copy(categoryId = "deleted-cat")
        assertNull(deletedCategory.toForm().validate(deletedCategory, skus, categories).category)
    }

    @Test
    fun newCategoryNameIsRequiredAndChecked() {
        val newMode = form.copy(categoryMode = CategoryMode.NEW, categoryId = null)
        assertEquals(FieldError.REQUIRED, newMode.errors().categoryName)
        assertEquals(FieldError.TOO_LONG, newMode.copy(categoryName = "c".repeat(MAX_CATEGORY_NAME_LENGTH + 1)).errors().categoryName)
        assertEquals(FieldError.DELETED_NAME, newMode.copy(categoryName = "old").errors(rejectedCategoryName = "OLD").categoryName)
        assertNull(newMode.copy(categoryName = "Paint").errors().categoryName)
    }

    @Test
    fun updateTrimsAndTurnsBlanksIntoNull() {
        val update = form.copy(name = " Bolt M8 ", sku = "  ", description = " ", unit = " kg ", threshold = "0", categoryMode = CategoryMode.NONE)
            .toUpdate(categories, now = 5, newId = { error("No id needed") })

        assertEquals("Bolt M8", update.changes.name)
        assertNull(update.changes.sku)
        assertNull(update.changes.description)
        assertEquals("kg", update.changes.unit)
        assertEquals(0L, update.changes.thresholdMilli)
        assertNull(update.changes.categoryId)
        assertNull(update.newCategory)
    }

    @Test
    fun serialArticleClearsTheThresholdAndIgnoresItsErrors() {
        val tool = form.copy(tracking = Tracking.SERIAL, threshold = "abc")
        assertNull(tool.errors().threshold)
        assertNull(tool.copy(threshold = "5").toUpdate(categories, now = 5, newId = { "x" }).changes.thresholdMilli)
        assertNull(tool.copy(threshold = "5").toNewArticle(categories, now = 5, newId = { "x" }).article.thresholdMilli)
    }

    @Test
    fun updateKeepsSkuAndBlankThresholdIsNull() {
        val update = form.copy(sku = " blt-9 ", threshold = " ").toUpdate(categories, now = 5, newId = { "x" })

        assertEquals("blt-9", update.changes.sku)
        assertNull(update.changes.thresholdMilli)
        assertEquals("c1", update.changes.categoryId)
    }

    @Test
    fun newCategoryReusesActiveNameIgnoringCase() {
        val update = form.copy(categoryMode = CategoryMode.NEW, categoryName = " tools ")
            .toUpdate(categories, now = 5, newId = { error("No id needed") })

        assertEquals("c2", update.changes.categoryId)
        assertNull(update.newCategory)
    }

    @Test
    fun newCategoryIsCreated() {
        val update = form.copy(categoryMode = CategoryMode.NEW, categoryName = " Paint ")
            .toUpdate(categories, now = 5, newId = { "c-new" })

        assertEquals(Category(id = "c-new", name = "Paint", createdAt = 5, updatedAt = 5), update.newCategory)
        assertEquals("c-new", update.changes.categoryId)
    }

    // ---------- Type (unit) ----------

    @Test
    fun serialArticleUnitIsNotCheckedAndSavedAsPieces() {
        val tool = form.copy(tracking = Tracking.SERIAL, unit = "")
        assertNull(tool.errors().unit)
        assertTrue(tool.errors().isValid)
        assertEquals(DEFAULT_UNIT, tool.toUpdate(categories, now = 5, newId = { "x" }).changes.unit)
    }

    @Test
    fun serialArticleIsPrefilledWithItsTracking() {
        assertEquals(Tracking.SERIAL, article.copy(tracking = Tracking.SERIAL).toForm().tracking)
    }

    // ---------- Create ----------

    private fun ArticleForm.createErrors(rejectedSku: String? = null) =
        validate(selfId = null, currentCategoryId = null, skus = skus, categories = categories, rejectedSku = rejectedSku)

    private fun ids(vararg values: String): () -> String {
        val queue = ArrayDeque(values.toList())
        return { queue.removeFirst() }
    }

    @Test
    fun emptyCreateFormIsInvalidOnlyBecauseOfTheName() {
        val errors = ArticleForm().createErrors()
        assertEquals(ArticleFormErrors(name = FieldError.REQUIRED), errors)
        assertFalse(errors.isValid)
    }

    @Test
    fun minimalCreateFormIsValid() {
        assertTrue(ArticleForm(name = "Nail").createErrors().isValid)
        assertTrue(ArticleForm(name = "Drill", tracking = Tracking.SERIAL, unit = "").createErrors().isValid)
        assertEquals(FieldError.REQUIRED, ArticleForm(name = "Nail", unit = " ").createErrors().unit)
    }

    @Test
    fun createChecksSkuAgainstEveryArticle() {
        // No own id: the SKU of a1 is taken too
        assertEquals(FieldError.DUPLICATE, ArticleForm(name = "Nail", sku = "blt-8").createErrors().sku)
        assertEquals(FieldError.DUPLICATE, ArticleForm(name = "Nail", sku = "old-1").createErrors().sku)
        assertEquals(FieldError.DUPLICATE, ArticleForm(name = "Nail", sku = "x-1").createErrors(rejectedSku = "X-1").sku)
    }

    @Test
    fun createNeedsAnActiveExistingCategory() {
        val existing = ArticleForm(name = "Nail", categoryMode = CategoryMode.EXISTING)
        assertEquals(FieldError.REQUIRED, existing.createErrors().category)
        assertEquals(FieldError.REQUIRED, existing.copy(categoryId = "gone").createErrors().category)
        assertNull(existing.copy(categoryId = "c1").createErrors().category)
    }

    @Test
    fun newArticleIsBuiltFromTheMinimalForm() {
        val new = ArticleForm(name = " Nail ", unit = " kg ").toNewArticle(categories, now = 7, newId = ids("a-new"))

        assertEquals(
            Article(id = "a-new", name = "Nail", unit = "kg", tracking = Tracking.QUANTITY, createdAt = 7, updatedAt = 7),
            new.article,
        )
        assertNull(new.newCategory)
    }

    @Test
    fun newArticleGetsTheScannedTag() {
        val form = ArticleForm(name = "Nail")

        assertEquals("04A2B3", form.toNewArticle(categories, now = 7, newId = ids("a-new"), tagId = "04A2B3").article.barcode)
        assertNull(form.toNewArticle(categories, now = 7, newId = ids("a-new")).article.barcode)
    }

    @Test
    fun newToolIsSerialTrackedInPieces() {
        val new = ArticleForm(name = "Drill", tracking = Tracking.SERIAL, unit = "kg", categoryMode = CategoryMode.EXISTING, categoryId = "c2")
            .toNewArticle(categories, now = 7, newId = ids("a-new"))

        assertEquals(Tracking.SERIAL, new.article.tracking)
        assertEquals(DEFAULT_UNIT, new.article.unit)
        assertEquals("c2", new.article.categoryId)
        assertNull(new.article.sku)
        assertNull(new.article.description)
        assertNull(new.article.thresholdMilli)
    }

    @Test
    fun newArticleWithNewCategoryLinksIt() {
        val new = ArticleForm(name = "Brush", categoryMode = CategoryMode.NEW, categoryName = " Paint ")
            .toNewArticle(categories, now = 7, newId = ids("c-new", "a-new"))

        assertEquals(Category(id = "c-new", name = "Paint", createdAt = 7, updatedAt = 7), new.newCategory)
        assertEquals("c-new", new.article.categoryId)
        assertEquals("a-new", new.article.id)
    }

    @Test
    fun newArticleReusesAnActiveCategoryIgnoringCase() {
        val new = ArticleForm(name = "Saw", categoryMode = CategoryMode.NEW, categoryName = "TOOLS")
            .toNewArticle(categories, now = 7, newId = ids("a-new"))

        assertNull(new.newCategory)
        assertEquals("c2", new.article.categoryId)
    }

    // ---------- Name template ----------

    private val ropes = Category(id = "c3", name = "Ropes", nameTemplate = "{type} rope {length}", createdAt = 0, updatedAt = 0)
    private val withTemplate = categories + ropes

    private fun ArticleForm.ropeErrors() =
        validate(selfId = null, currentCategoryId = null, skus = skus, categories = withTemplate)

    private fun link(key: String, type: AttributeDataType, unit: String? = null, required: Boolean = false) = CategoryAttributeWithDefinition(
        CategoryAttribute(id = "l_$key", categoryId = "c3", attributeId = "id_$key", required = required, createdAt = 0, updatedAt = 0),
        AttributeDefinition(id = "id_$key", key = key, label = key, dataType = type, unit = unit, createdAt = 0, updatedAt = 0),
    )

    private val ropeAttributes = listOf(link("type", AttributeDataType.TEXT, required = true), link("length", AttributeDataType.NUMBER, "m"))

    @OptIn(ExperimentalSerializationApi::class)
    private val ropeValues = JsonObject(mapOf("type" to JsonPrimitive("static"), "length" to JsonUnquotedLiteral("50")))

    private val ropeForm = ArticleForm(categoryMode = CategoryMode.EXISTING, categoryId = "c3", attributes = ropeValues)

    @Test
    fun templateNameComesFromTheChosenCategory() {
        assertEquals("Static rope 50 m", ropeForm.templateName(withTemplate, ropeAttributes))
        // No template, no existing category, or a new one
        assertNull(ropeForm.copy(categoryId = "c1").templateName(withTemplate, ropeAttributes))
        assertNull(ropeForm.copy(categoryMode = CategoryMode.NONE).templateName(withTemplate, ropeAttributes))
        assertNull(ropeForm.copy(categoryMode = CategoryMode.NEW, categoryName = "Ropes").templateName(withTemplate, ropeAttributes))
        // Not valid
        assertNull(ropeForm.templateName(categories + ropes.copy(nameTemplate = "{type"), ropeAttributes))
    }

    @Test
    fun templateNameIsEmptyWithoutValues() {
        assertEquals("Rope", ropeForm.copy(attributes = null).templateName(withTemplate, ropeAttributes))
        assertEquals("", ropeForm.copy(attributes = null).templateName(categories + ropes.copy(nameTemplate = "{type}"), ropeAttributes))
    }

    @Test
    fun nameFollowsTheTemplateUntilTyped() {
        assertEquals("Static rope 50 m", ropeForm.withTemplateName("Static rope 50 m").name)
        // No template: the name stays
        assertEquals("Bolt", ropeForm.copy(name = "Bolt").withTemplateName(null).name)

        val typed = ropeForm.withTemplateName("Static rope 50 m").withTypedName("My rope")
        assertFalse(typed.nameAuto)
        assertEquals("My rope", typed.withTemplateName("Static rope 50 m").name)
        // Back to the template
        assertEquals("Static rope 50 m", typed.copy(nameAuto = true).withTemplateName("Static rope 50 m").name)
    }

    @Test
    fun formStateAppliesTheTemplateAndChecksTheAttributes() {
        val state = articleFormState(
            ArticleFormMode.CREATE, ropeForm, withTemplate, FormAttributes("c3", ropeAttributes), FormSaveStatus(),
        ) { it.ropeErrors() }
        assertEquals("Static rope 50 m", state.form.name)
        assertEquals("Static rope 50 m", state.templateName)
        assertTrue(state.followsTemplate)
        assertTrue(state.canSave)

        val missing = articleFormState(
            ArticleFormMode.CREATE, ropeForm.copy(attributes = null), withTemplate.map { if (it.id == "c3") it.copy(nameTemplate = "{type}") else it },
            FormAttributes("c3", ropeAttributes), FormSaveStatus(),
        ) { it.ropeErrors() }
        // The rendered name is empty: the name is required, and so is the type
        assertEquals("", missing.form.name)
        assertEquals(FieldError.REQUIRED, missing.errors.name)
        assertEquals(mapOf("type" to AttributeError.REQUIRED), missing.attributeErrors)
        assertFalse(missing.canSave)
    }

    @Test
    fun formStateIgnoresAttributesOfAnotherCategory() {
        val state = articleFormState(
            ArticleFormMode.CREATE, ropeForm.copy(name = "Old"), withTemplate, FormAttributes("c1", ropeAttributes), FormSaveStatus(),
        ) { it.ropeErrors() }
        assertTrue(state.attributes.isEmpty())
        // Still loading the attributes of c3: the template renders without definitions
        assertEquals("Rope", state.form.name)
    }

    @Test
    fun typedNameDoesNotFollow() {
        val state = articleFormState(
            ArticleFormMode.EDIT, ropeForm.withTypedName("Mine"), withTemplate, FormAttributes("c3", ropeAttributes), FormSaveStatus(),
        ) { it.ropeErrors() }
        assertEquals("Mine", state.form.name)
        assertEquals("Static rope 50 m", state.templateName)
        assertFalse(state.followsTemplate)
    }

    @Test
    fun savedArticleKeepsTheValuesAndWhetherItFollows() {
        val form = ropeForm.copy(name = "Static rope 50 m")
        val new = form.toNewArticle(withTemplate, now = 7, newId = ids("a-new"), followsTemplate = true)
        assertTrue(new.article.nameAuto)
        assertEquals(ropeValues, new.article.attributes)
        assertFalse(form.toNewArticle(withTemplate, now = 7, newId = ids("a-new")).article.nameAuto)

        val update = form.toUpdate(withTemplate, now = 7, newId = ids(), followsTemplate = true)
        assertTrue(update.changes.nameAuto)
        assertEquals(ropeValues, update.changes.attributes)
        assertFalse(form.toUpdate(withTemplate, now = 7, newId = ids()).changes.nameAuto)
    }

    @Test
    fun storedArticleKeepsItsNameMode() {
        assertTrue(article.copy(nameAuto = true).toForm().nameAuto)
        assertFalse(article.toForm().nameAuto)
        // A new article follows the template by default
        assertTrue(ArticleForm().nameAuto)
    }
}
