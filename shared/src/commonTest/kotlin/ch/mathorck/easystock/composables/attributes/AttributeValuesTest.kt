package ch.mathorck.easystock.composables.attributes

import ch.mathorck.easystock.data.db.CategoryAttributeWithDefinition
import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.AttributeDefinition
import ch.mathorck.easystock.data.model.CategoryAttribute
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttributeValuesTest {
    private fun attribute(
        key: String,
        type: AttributeDataType,
        required: Boolean = false,
        unit: String? = null,
        options: List<String>? = null,
    ) = CategoryAttributeWithDefinition(
        link = CategoryAttribute(id = "l-$key", categoryId = "ropes", attributeId = key, required = required, createdAt = 0, updatedAt = 0),
        definition = AttributeDefinition(id = key, key = key, label = key, dataType = type, unit = unit, options = options, createdAt = 0, updatedAt = 0),
    )

    // User example: category "Ropes"
    private val ropes = listOf(
        attribute("breaking_strength", AttributeDataType.NUMBER, required = true, unit = "N"),
        attribute("length", AttributeDataType.NUMBER, unit = "m"),
    )

    // ---------- numbers ----------

    @Test
    fun numbersAcceptCommaOrDotAndAreStoredAsJsonNumbers() {
        assertEquals("1.5", parseAttributeNumber("1,5").toString())
        assertEquals("1.5", parseAttributeNumber("1.50").toString())
        assertEquals("22000", parseAttributeNumber(" 22000 ").toString())
        assertEquals("0.5", parseAttributeNumber(".5").toString())
        assertEquals("2", parseAttributeNumber("2.").toString())
        assertEquals("-3.25", parseAttributeNumber("-03,250").toString())
        assertEquals("0", parseAttributeNumber("-0").toString())
        assertFalse(parseAttributeNumber("1.5")!!.isString)
    }

    @Test
    fun invalidNumbersAreRejected() {
        listOf("", "-", ".", "1.2.3", "1,2,3", "1e3", "abc", "+1", "1 000", "1234567890123456").forEach {
            assertNull(parseAttributeNumber(it), it)
        }
        assertEquals("123456789012345", parseAttributeNumber("123456789012345").toString())
    }

    @Test
    fun numberFieldKeepsInvalidTextAsString() {
        assertEquals(JsonPrimitive("12,x"), attributeValueFromText(AttributeDataType.NUMBER, "12,x"))
        assertNull(attributeValueFromText(AttributeDataType.NUMBER, "  "))
        assertEquals(JsonPrimitive("red"), attributeValueFromText(AttributeDataType.TEXT, "red"))
    }

    // ---------- JSON helpers ----------

    @Test
    fun setKeepsOtherKeysAndRemoveDropsEmptyObject() {
        val values = buildJsonObject {
            put("length", 20)
            put("old_key", "kept")
        }
        val updated = values.withAttributeValue("breaking_strength", parseAttributeNumber("22000"))!!
        assertEquals(setOf("length", "old_key", "breaking_strength"), updated.keys)
        assertEquals("kept", attributeInputText(updated.attributeValue("old_key")))

        val removed = updated.withAttributeValue("length", null)!!
        assertFalse("length" in removed)
        assertNull(JsonObject(mapOf("a" to JsonPrimitive(1))).withAttributeValue("a", null))
        assertEquals(JsonObject(mapOf("a" to JsonPrimitive(true))), (null as JsonObject?).withAttributeValue("a", JsonPrimitive(true)))
    }

    @Test
    fun readHelpers() {
        assertEquals("", attributeInputText(null))
        assertEquals("20", attributeInputText(parseAttributeNumber("20")))
        assertTrue(attributeBool(JsonPrimitive(true)))
        assertFalse(attributeBool(null))
        assertFalse(attributeBool(JsonPrimitive("true")))
        assertEquals(LocalDate(2026, 11, 10), attributeDate(JsonPrimitive("2026-11-10")))
        assertNull(attributeDate(JsonPrimitive("10.11.2026")))
        assertEquals(JsonPrimitive("2026-01-05"), attributeDateValue(LocalDate(2026, 1, 5)))
    }

    // ---------- validation ----------

    @Test
    fun ropeExampleIsValidWithNumbers() {
        val values = buildJsonObject {
            put("breaking_strength", 22000)
            put("length", 60.5)
        }
        assertEquals(emptyMap(), validateAttributes(ropes, values))
    }

    @Test
    fun requiredMissingIsReported() {
        assertEquals(mapOf("breaking_strength" to AttributeError.REQUIRED), validateAttributes(ropes, null))
        val blank = buildJsonObject { put("breaking_strength", " ") }
        assertEquals(AttributeError.REQUIRED, validateAttributes(ropes, blank)["breaking_strength"])
    }

    @Test
    fun numberStoredAsStringIsInvalid() {
        val values = buildJsonObject {
            put("breaking_strength", 1)
            put("length", "12,x")
        }
        assertEquals(mapOf("length" to AttributeError.INVALID_NUMBER), validateAttributes(ropes, values))
    }

    @Test
    fun unknownKeysAreIgnored() {
        val values = buildJsonObject {
            put("breaking_strength", 1)
            put("color", JsonObject(emptyMap()))
        }
        assertEquals(emptyMap(), validateAttributes(ropes, values))
    }

    @Test
    fun typeChecks() {
        val attributes = listOf(
            attribute("note", AttributeDataType.TEXT),
            attribute("dynamic", AttributeDataType.BOOL, required = true),
            attribute("inspected", AttributeDataType.DATE),
            attribute("color", AttributeDataType.ENUM, options = listOf("Red", "Blue")),
        )
        // Required has no effect on bool: unset means false
        assertEquals(emptyMap(), validateAttributes(attributes, null))

        val valid = buildJsonObject {
            put("note", "ok")
            put("dynamic", false)
            put("inspected", "2026-02-28")
            put("color", "Blue")
        }
        assertEquals(emptyMap(), validateAttributes(attributes, valid))

        val invalid = buildJsonObject {
            put("note", "x".repeat(MAX_ATTRIBUTE_TEXT_LENGTH + 1))
            put("dynamic", "yes")
            put("inspected", "2026-02-30")
            put("color", "Yellow")
        }
        assertEquals(
            mapOf(
                "note" to AttributeError.TOO_LONG,
                "dynamic" to AttributeError.INVALID,
                "inspected" to AttributeError.INVALID_DATE,
                "color" to AttributeError.INVALID_OPTION,
            ),
            validateAttributes(attributes, invalid),
        )
    }
}
