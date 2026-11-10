package ch.mathorck.easystock.composables.attributes

import ch.mathorck.easystock.data.db.CategoryAttributeWithDefinition
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.ArticleStock
import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.AttributeDefinition
import ch.mathorck.easystock.data.model.CategoryAttribute
import ch.mathorck.easystock.viewmodels.filterByQuery
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
class MeasureTest {
    private fun def(key: String, type: AttributeDataType, unit: String?) =
        AttributeDefinition(id = "id_$key", key = key, label = key, dataType = type, unit = unit, createdAt = 0, updatedAt = 0)

    private val length = def("length", AttributeDataType.MEASURE, "m")
    private val diameter = def("diameter", AttributeDataType.MEASURE, "mm")
    private val weight = def("weight", AttributeDataType.MEASURE, "kg")
    private val oldNumber = def("old", AttributeDataType.NUMBER, "cm")

    private fun num(text: String) = JsonUnquotedLiteral(text)
    private fun measure(number: String, unit: String): JsonElement = measureValueFromInput(number, unit)!!
    private fun parse(json: String): JsonElement = Json.parseToJsonElement(json)

    // ---------- Units and conversion ----------

    @Test
    fun unitsAreFoundIgnoringCaseAndSpaces() {
        assertEquals("kN", MeasureUnits.find(" KN ")?.symbol)
        assertEquals("N", MeasureUnits.find("n")?.symbol)
        assertEquals("daN", MeasureUnits.find("DAN")?.symbol)
        assertEquals("mm", MeasureUnits.find("MM")?.symbol)
        assertNull(MeasureUnits.find("V"))
        assertNull(MeasureUnits.find(""))
        assertNull(MeasureUnits.find(null))
        assertEquals(listOf("mm", "cm", "m", "km"), MeasureUnits.of(MeasureFamily.LENGTH).map { it.symbol })
        // Symbols are unique ignoring case
        assertEquals(MeasureUnits.all.size, MeasureUnits.all.map { it.symbol.lowercase() }.toSet().size)
        // Each family has its default unit
        MeasureFamily.entries.forEach { assertEquals(it, MeasureUnits.find(it.defaultUnit)?.family) }
    }

    @Test
    fun exactDecimalsCompareByValue() {
        assertEquals(ExactDecimal.parse("50"), ExactDecimal.parse("50.0".let { normalizeAttributeNumber(it)!! }))
        assertEquals(ExactDecimal(false, "98", -1), ExactDecimal.parse("9.8"))
        assertEquals(ExactDecimal(false, "5", 1), ExactDecimal.parse("50"))
        assertEquals(ExactDecimal(false, "2", -2), ExactDecimal.parse("0.02"))
        assertEquals(ExactDecimal(true, "15", -1), ExactDecimal.parse("-1.5"))
        assertTrue(ExactDecimal.parse("0")!!.isZero)
        assertEquals("0.0098", ExactDecimal.parse("9.8")!!.shift(-3).toPlainString())
        assertEquals("50000", ExactDecimal.parse("50")!!.shift(3).toPlainString())
        assertEquals("-1.5", ExactDecimal.parse("-1.5")!!.toPlainString())
        assertEquals("12.34", ExactDecimal.parse("1234")!!.shift(-2).toPlainString())
    }

    @Test
    fun conversionIsExact() {
        val rope = MeasureValue("50", MeasureUnits.find("m")!!)
        assertEquals("5000", rope.numberIn(MeasureUnits.find("cm")!!))
        assertEquals("50000", rope.numberIn(MeasureUnits.find("mm")!!))
        assertEquals("0.05", rope.numberIn(MeasureUnits.find("km")!!))
        assertNull(rope.numberIn(MeasureUnits.find("kg")!!))
        // 0.1 + 0.2 style drift can't happen: no Double
        assertEquals("0.3", MeasureValue("300", MeasureUnits.find("mm")!!).numberIn(MeasureUnits.find("m")!!))
        assertEquals("22", MeasureValue("2200", MeasureUnits.find("daN")!!).numberIn(MeasureUnits.find("kN")!!))
        // 15 digits plus a large shift: no overflow
        assertEquals("123456789012345000000000", MeasureValue("123456789012345", MeasureUnits.find("t")!!).numberIn(MeasureUnits.find("mg")!!))
        assertEquals(
            MeasureValue("5000", MeasureUnits.find("cm")!!).magnitude,
            MeasureValue("0.05", MeasureUnits.find("km")!!).magnitude,
        )
    }

    // ---------- Parsing typed text ----------

    @Test
    fun parsesNumberWithUnit() {
        assertEquals(MeasureValue("50", MeasureUnits.find("m")!!), parseMeasureText("50m"))
        assertEquals(MeasureValue("50", MeasureUnits.find("m")!!), parseMeasureText(" 50 m "))
        assertEquals(MeasureValue("0.5", MeasureUnits.find("km")!!), parseMeasureText("0.5 km"))
        assertEquals(MeasureValue("9.8", MeasureUnits.find("mm")!!), parseMeasureText("9,8 mm"))
        assertEquals(MeasureValue("9.8", MeasureUnits.find("mm")!!), parseMeasureText("9,8MM"))
        assertEquals(MeasureValue("22", MeasureUnits.find("kN")!!), parseMeasureText("22kn"))
        assertNull(parseMeasureText("50"))
        assertNull(parseMeasureText("m"))
        assertNull(parseMeasureText("50 V"))
        assertNull(parseMeasureText("rope 50 m"))
        assertNull(parseMeasureText("5.0.0 m"))
    }

    // ---------- Stored values ----------

    @Test
    fun storesNumberAndUnitAsEntered() {
        assertEquals(parse("""{"value":9.8,"unit":"mm"}"""), measureValueFromInput("9,80", "mm"))
        assertEquals(parse("""{"value":"12,x","unit":"m"}"""), measureValueFromInput("12,x", "m"))
        assertNull(measureValueFromInput("  ", "m"))
        assertEquals("9.8", measureInputText(measure("9.8", "mm")))
        assertEquals("mm", measureInputUnit(measure("9.8", "mm"), "m"))
        assertEquals("m", measureInputUnit(num("50"), "m"))
        assertEquals("m", measureInputUnit(null, "m"))
        assertEquals("50", measureInputText(num("50")))
        assertEquals("9.8 mm", attributeInputText(measure("9.8", "mm")))
    }

    @Test
    fun oldPlainNumberIsReadInDefaultUnit() {
        assertEquals(MeasureValue("50", MeasureUnits.find("m")!!), readMeasure(length, num("50")))
        assertEquals("50 m", formatAttributeValue(length, num("50"))?.text)
        // Object without unit: default unit too
        assertEquals(MeasureValue("2", MeasureUnits.find("m")!!), readMeasure(length, parse("""{"value":2}""")))
        assertNull(readMeasure(length, JsonPrimitive("50")))
        assertNull(readMeasure(length, null))
    }

    @Test
    fun validation() {
        fun link(definition: AttributeDefinition, required: Boolean = false) = CategoryAttributeWithDefinition(
            link = CategoryAttribute(id = "l", categoryId = "c", attributeId = definition.id, required = required, createdAt = 0, updatedAt = 0),
            definition = definition,
        )
        fun errors(value: JsonElement?, required: Boolean = false) =
            validateAttributes(listOf(link(length, required)), value?.let { JsonObject(mapOf("length" to it)) })
        assertTrue(errors(measure("50", "m")).isEmpty())
        assertTrue(errors(measure("9.8", "mm")).isEmpty())
        assertTrue(errors(num("50")).isEmpty())
        assertTrue(errors(null).isEmpty())
        assertEquals(AttributeError.REQUIRED, errors(null, required = true)["length"])
        assertEquals(AttributeError.INVALID_NUMBER, errors(measure("12,x", "m"))["length"])
        assertEquals(AttributeError.INVALID_NUMBER, errors(JsonPrimitive("50 m"))["length"])
        // Other family or unknown unit
        assertEquals(AttributeError.INVALID, errors(measure("2", "kg"))["length"])
        assertEquals(AttributeError.INVALID, errors(measure("2", "V"))["length"])
        assertEquals(AttributeError.INVALID, errors(JsonPrimitive(true))["length"])
    }

    // ---------- Display and search ----------

    private fun article(id: String, name: String, vararg values: Pair<String, JsonElement>) =
        ArticleStock(Article(id = id, name = name, attributes = JsonObject(mapOf(*values)), createdAt = 0, updatedAt = 0), 0)

    private val catalog = AttributeCatalog(listOf(length, diameter, weight, oldNumber), emptyList())
    private val dynamicRope = article("rope", "Dynamic rope", "length" to measure("50", "m"), "diameter" to measure("9.8", "mm"))
    private val cord = article("cord", "Cord", "length" to measure("500", "cm"), "diameter" to num("4"))
    private val bag = article("bag", "Bag", "weight" to measure("1.2", "kg"), "old" to num("30"))
    private val items = listOf(dynamicRope, cord, bag)

    private fun ids(query: String) = items.filterByQuery(query) { catalog.format(it.article) }.map { it.article.id }

    @Test
    fun displaysAsEntered() {
        assertEquals("9.8 mm · 50 m", catalog.format(dynamicRope.article).summaryLine())
        assertEquals("4 mm · 500 cm", catalog.format(cord.article).summaryLine())
        assertEquals(AttributeText("9.8", "9.8 mm", MeasureValue("9.8", MeasureUnits.find("mm")!!).magnitude), formatAttributeValue(diameter, measure("9.80", "mm")))
    }

    @Test
    fun searchConvertsUnits() {
        assertEquals(listOf("rope"), ids("5000 cm"))
        assertEquals(listOf("rope"), ids("5000cm"))
        assertEquals(listOf("rope"), ids("50m"))
        assertEquals(listOf("rope"), ids("50 m"))
        assertEquals(listOf("rope"), ids("0.05 km"))
        assertEquals(listOf("rope"), ids("0,0098 m"))
        assertEquals(listOf("cord"), ids("5 m"))
        assertEquals(listOf("cord"), ids("0.4 cm"))
        assertEquals(listOf("bag"), ids("1200 g"))
        // A number attribute with a known unit converts too
        assertEquals(listOf("bag"), ids("300 mm"))
        // Other family: no match
        assertTrue(ids("50 kg").isEmpty())
        // Plain number: substring, as before
        assertEquals(listOf("rope", "cord"), ids("50"))
        assertEquals(listOf("rope"), ids("9.8"))
        assertFalse(listOf(AttributeText("1", "1 m")).matches("2 m"))
    }

    // ---------- Name templates ----------

    @Test
    fun templates() {
        val defs = listOf(length, diameter)
        val values = JsonObject(mapOf("length" to measure("50", "m"), "diameter" to measure("9.8", "mm")))
        assertEquals("Rope 50 m 9.8 mm", articleNameFromTemplate("Rope {length} {diameter}", defs, values))
        assertEquals("Rope 50", articleNameFromTemplate("Rope {length|raw}", defs, values))
        assertEquals("Rope 5000 cm", articleNameFromTemplate("Rope {length|cm}", defs, values))
        assertEquals("Rope 5000", articleNameFromTemplate("Rope {length|CM|raw}", defs, values))
        assertEquals("Rope 0.98 cm", articleNameFromTemplate("Rope {diameter|cm}", defs, values))
        // Other family: shown as entered
        assertEquals("Rope 50 m", articleNameFromTemplate("Rope {length|kg}", defs, values))
        // Old plain value: default unit
        assertEquals("Rope 50000 mm", articleNameFromTemplate("Rope {length|mm}", defs, JsonObject(mapOf("length" to num("50")))))
        // A number with a known unit converts too
        assertEquals("Rope 0.3 m", articleNameFromTemplate("Rope {old|m}", listOf(oldNumber), JsonObject(mapOf("old" to num("30")))))
        // Unit filters parse, unknown ones are still errors
        assertEquals(NameTemplatePart.Placeholder("length", emptyList(), "kN"), (parseNameTemplate("{length|kn}") as NameTemplateParseResult.Valid).template.parts.single())
        assertEquals(NameTemplateError(NameTemplateError.Kind.UNKNOWN_FILTER, "volts"), (parseNameTemplate("{length|volts}") as NameTemplateParseResult.Invalid).error)
        assertEquals("10 m", formatAttributeValue(length, sampleAttributeValues(listOf(length))["length"])?.text)
    }
}
