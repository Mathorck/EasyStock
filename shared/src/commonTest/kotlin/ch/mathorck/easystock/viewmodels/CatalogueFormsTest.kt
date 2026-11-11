package ch.mathorck.easystock.viewmodels

import ch.mathorck.easystock.data.db.CategoryAttributeInput
import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.AttributeDefinition
import ch.mathorck.easystock.data.model.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogueFormsTest {
    private val length = AttributeDefinition(
        id = "a1", key = "length", label = "Length", dataType = AttributeDataType.NUMBER, unit = "m", createdAt = 0, updatedAt = 0,
    )

    @Test
    fun templatePreviewShowsTheCategoryName() {
        val state = nameTemplateState("{category} {length}", listOf(length), null, 0, false, "Cordes")
        assertNull(state.error)
        assertEquals("Cordes 10 m", state.preview)
        assertNull(nameTemplateState("{category}", emptyList(), null, 0, false, "Cordes").error)
    }

    @Test
    fun attributeFormRules() {
        assertEquals(CatalogueError.REQUIRED, AttributeForm(label = " ").validate(emptyList(), null).label)
        assertEquals(CatalogueError.DUPLICATE, AttributeForm(label = "length ").validate(listOf(length), null).label)
        assertNull(AttributeForm(label = "Length").validate(listOf(length), "a1").label)
        assertEquals(CatalogueError.TOO_LONG, AttributeForm(label = "x", unit = "u".repeat(21)).validate(emptyList(), null).unit)
        assertTrue(AttributeForm(label = "Breaking strength", dataType = AttributeDataType.NUMBER, unit = "N").validate(listOf(length), null).isValid)
    }

    @Test
    fun measureFormRules() {
        fun def(type: AttributeDataType, unit: String?) =
            AttributeDefinition(id = "a2", key = "k", label = "K", dataType = type, unit = unit, createdAt = 0, updatedAt = 0)
        // New attribute: any type, a measure gets a known unit
        assertEquals(AttributeDataType.entries, allowedTypes(null))
        assertEquals("mm", AttributeForm(label = "D", unit = "MM").withType(AttributeDataType.MEASURE).unit)
        assertEquals("m", AttributeForm(label = "D", unit = "V").withType(AttributeDataType.MEASURE).unit)
        assertEquals(CatalogueError.REQUIRED, AttributeForm(label = "D", dataType = AttributeDataType.MEASURE, unit = "V").validate(emptyList(), null).unit)
        assertTrue(AttributeForm(label = "D", dataType = AttributeDataType.MEASURE, unit = "kN").validate(emptyList(), null).isValid)
        // Number with a known unit: can become a measure in that unit, unit locked
        val numberM = def(AttributeDataType.NUMBER, "M")
        assertEquals(listOf(AttributeDataType.NUMBER, AttributeDataType.MEASURE), allowedTypes(numberM))
        assertTrue(measureUnitLocked(numberM))
        val converted = numberM.toForm().withType(AttributeDataType.MEASURE).copy(unit = "kg").lockedFor(numberM)
        assertEquals(AttributeDataType.MEASURE, converted.dataType)
        assertEquals("m", converted.unit)
        // Number without unit: free choice of the unit
        val numberBlank = def(AttributeDataType.NUMBER, null)
        assertEquals(listOf(AttributeDataType.NUMBER, AttributeDataType.MEASURE), allowedTypes(numberBlank))
        assertEquals(false, measureUnitLocked(numberBlank))
        assertEquals("kg", numberBlank.toForm().withType(AttributeDataType.MEASURE).copy(unit = "kg").lockedFor(numberBlank).unit)
        // Number with an unknown unit: stays a number
        val numberV = def(AttributeDataType.NUMBER, "V")
        assertEquals(listOf(AttributeDataType.NUMBER), allowedTypes(numberV))
        assertEquals(AttributeDataType.NUMBER, numberV.toForm().withType(AttributeDataType.MEASURE).lockedFor(numberV).dataType)
        // Stored measure: type and unit locked
        val measure = def(AttributeDataType.MEASURE, "mm")
        assertEquals(listOf(AttributeDataType.MEASURE), allowedTypes(measure))
        assertTrue(measureUnitLocked(measure))
        val edited = measure.toForm().copy(dataType = AttributeDataType.NUMBER, unit = "m").lockedFor(measure)
        assertEquals(AttributeDataType.MEASURE, edited.dataType)
        assertEquals("mm", edited.unit)
        // Other types stay locked
        assertEquals(AttributeDataType.TEXT, def(AttributeDataType.TEXT, null).toForm().withType(AttributeDataType.MEASURE).lockedFor(def(AttributeDataType.TEXT, null)).dataType)
    }

    @Test
    fun enumOptionsRules() {
        val enumForm = AttributeForm(label = "Color", dataType = AttributeDataType.ENUM)
        assertEquals(CatalogueError.NO_OPTIONS, enumForm.copy(optionsText = " \n ").validate(emptyList(), null).options)
        assertEquals(CatalogueError.DUPLICATE_OPTION, enumForm.copy(optionsText = "Red\nRed ").validate(emptyList(), null).options)
        assertNull(enumForm.copy(optionsText = "Red\n\n Blue ").validate(emptyList(), null).options)
        assertEquals(listOf("Red", "Blue"), parseAttributeOptions("Red\n\n Blue \n"))
    }

    @Test
    fun categoryNameRules() {
        val ropes = Category(id = "c1", name = "Ropes", createdAt = 0, updatedAt = 0)
        assertEquals(CatalogueError.REQUIRED, validateCategoryName("", listOf(ropes), null))
        assertEquals(CatalogueError.DUPLICATE, validateCategoryName("ropes", listOf(ropes), null))
        assertNull(validateCategoryName("ROPES", listOf(ropes), "c1"))
        assertEquals(CatalogueError.DELETED_NAME, validateCategoryName("Old", emptyList(), null, "old", CatalogueError.DELETED_NAME))
    }

    @Test
    fun draftEdits() {
        val list = listOf(CategoryAttributeInput("a", false), CategoryAttributeInput("b", true))
        assertEquals(listOf("a", "b", "c"), list.withAttribute("c").map { it.attributeId })
        assertEquals(list, list.withAttribute("a"))
        assertEquals(listOf("b"), list.withoutAttribute("a").map { it.attributeId })
        assertTrue(list.withRequired("a", true)[0].required)
        assertEquals(listOf("b", "a"), list.moved("b", -1).map { it.attributeId })
        assertEquals(list, list.moved("a", -1))
        assertEquals(list, list.moved("b", 1))
        assertEquals(list, list.moved("zzz", 1))
    }
}
