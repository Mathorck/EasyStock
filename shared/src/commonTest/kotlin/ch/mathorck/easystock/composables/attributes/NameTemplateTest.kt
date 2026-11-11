package ch.mathorck.easystock.composables.attributes

import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.AttributeDefinition
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalSerializationApi::class)
class NameTemplateTest {
    private fun def(key: String, type: AttributeDataType, label: String = key, unit: String? = null, options: List<String>? = null) =
        AttributeDefinition(id = "id_$key", key = key, label = label, dataType = type, unit = unit, options = options, createdAt = 0, updatedAt = 0)

    private val type = def("rope_type", AttributeDataType.ENUM, "Type", options = listOf("static", "dynamic"))
    private val diameter = def("diameter", AttributeDataType.NUMBER, "Diameter", "mm")
    private val length = def("length", AttributeDataType.NUMBER, "Length", "m")
    private val dry = def("dry", AttributeDataType.BOOL, "Dry treated")
    private val bought = def("bought", AttributeDataType.DATE, "Bought")
    private val note = def("note", AttributeDataType.TEXT, "Note")
    private val all = listOf(type, diameter, length, dry, bought, note)
    private val keys = all.map { it.key }

    private fun values(vararg pairs: Pair<String, JsonElement>) = JsonObject(mapOf(*pairs))
    private fun num(text: String) = JsonUnquotedLiteral(text)

    private val rope = values(
        "rope_type" to JsonPrimitive("static"), "diameter" to num("10.50"), "length" to num("50"),
        "dry" to JsonPrimitive(true), "bought" to JsonPrimitive("2026-03-01"), "note" to JsonPrimitive("red"),
    )

    private fun name(template: String, values: JsonObject? = rope, maxLength: Int = MAX_NAME_TEMPLATE_LENGTH) =
        articleNameFromTemplate(template, all, values, maxLength)

    private fun JsonObject.without(vararg keys: String) = JsonObject(filterKeys { it !in keys })

    private fun error(text: String) = (parseNameTemplate(text) as NameTemplateParseResult.Invalid).error

    // ---------- Parsing ----------

    @Test
    fun parsesLiteralsPlaceholdersAndFilters() {
        val parsed = assertIs<NameTemplateParseResult.Valid>(parseNameTemplate("Rope { diameter | RAW|upper }mm {length}"))
        assertEquals(
            listOf(
                NameTemplatePart.Literal("Rope "),
                NameTemplatePart.Placeholder("diameter", listOf(NameTemplateFilter.RAW, NameTemplateFilter.UPPER)),
                NameTemplatePart.Literal("mm "),
                NameTemplatePart.Placeholder("length"),
            ),
            parsed.template.parts,
        )
    }

    @Test
    fun keysAreListedOnceInOrder() {
        val parsed = assertIs<NameTemplateParseResult.Valid>(parseNameTemplate("{b} {a} {b|upper}"))
        assertEquals(listOf("b", "a"), parsed.template.keys)
    }

    @Test
    fun doubledBracesAreLiteral() {
        val parsed = assertIs<NameTemplateParseResult.Valid>(parseNameTemplate("{{x}} {note}"))
        assertEquals(listOf(NameTemplatePart.Literal("{x} "), NameTemplatePart.Placeholder("note")), parsed.template.parts)
        assertEquals("{x} red", name("{{x}} {note}"))
    }

    @Test
    fun badBracesAndEmptyKeysAreSyntaxErrors() {
        listOf("{note", "note}", "{}", "{ }", "{|upper}", "{a{b}}", "Rope {").forEach {
            assertEquals(NameTemplateError.Kind.SYNTAX, error(it).kind, it)
        }
    }

    @Test
    fun unknownFilterIsReported() {
        assertEquals(NameTemplateError(NameTemplateError.Kind.UNKNOWN_FILTER, "bold"), error("{note|bold}"))
        assertEquals(NameTemplateError.Kind.UNKNOWN_FILTER, error("{note|}").kind)
    }

    @Test
    fun tooLongTemplateIsRejected() {
        assertEquals(NameTemplateError.Kind.TOO_LONG, error("x".repeat(MAX_NAME_TEMPLATE_LENGTH + 1)).kind)
        assertIs<NameTemplateParseResult.Valid>(parseNameTemplate("x".repeat(MAX_NAME_TEMPLATE_LENGTH)))
    }

    // ---------- Validation ----------

    @Test
    fun blankTemplateIsValid() {
        assertNull(validateNameTemplate("", keys))
        assertNull(validateNameTemplate("   ", emptyList()))
    }

    @Test
    fun unknownKeyIsAnError() {
        assertNull(validateNameTemplate("{rope_type} rope {diameter}", keys))
        assertEquals(NameTemplateError(NameTemplateError.Kind.UNKNOWN_KEY, "colour"), validateNameTemplate("{note} {colour} {x}", keys))
        // Keys are case-sensitive, as AttributeDefinition.key
        assertEquals(NameTemplateError.Kind.UNKNOWN_KEY, validateNameTemplate("{Note}", keys)?.kind)
    }

    @Test
    fun syntaxErrorComesBeforeUnknownKeys() {
        assertEquals(NameTemplateError.Kind.SYNTAX, validateNameTemplate("{colour} {", keys)?.kind)
    }

    @Test
    fun templateWithoutPlaceholderIsValid() {
        assertNull(validateNameTemplate("Rope", emptyList()))
        assertEquals("Rope", name("rope"))
    }

    @Test
    fun invalidOrMissingTemplateGivesNoName() {
        assertNull(nameTemplateOrNull(null))
        assertNull(nameTemplateOrNull("  "))
        assertNull(nameTemplateOrNull("{note"))
        assertNull(name("{note"))
        assertNull(articleNameFromTemplate(null, all, rope))
        assertNull(articleNameFromTemplate("", all, rope))
    }

    // ---------- Rendering ----------

    @Test
    fun rendersTheRopeExample() {
        assertEquals("Static rope 10.5 mm 50 m", name("{rope_type} rope {diameter} {length}"))
    }

    @Test
    fun formatsEachType() {
        assertEquals("Dry treated", name("{dry}"))
        assertEquals("Bought 2026-03-01", name("bought {bought}"))
        assertEquals("Red", name("{note}"))
    }

    @Test
    fun rawFilterDropsTheUnit() {
        assertEquals("Ø10.5mm x 50m", name("Ø{diameter|raw}mm x {length|raw}m"))
        // No effect on other types
        assertEquals("Static", name("{rope_type|raw}"))
    }

    @Test
    fun caseFilters() {
        assertEquals("Rope STATIC", name("rope {rope_type|upper}"))
        assertEquals("Rope 10.5 MM", name("rope {diameter|upper}"))
        assertEquals("X red", name("x {note|lower}", rope + ("note" to JsonPrimitive("RED"))))
        assertEquals("X Red", name("x {note|capitalize}"))
        assertEquals("X RED", name("x {note|lower|upper}"))
    }

    @Test
    fun firstLetterIsCapitalizedOnly() {
        assertEquals("Static rope", name("{rope_type} rope"))
        assertEquals("ÉCorde", name("éCorde"))
        assertEquals("10.5 mm static", name("{diameter} {rope_type}"))
    }

    @Test
    fun missingValueInTheMiddleKeepsOneSeparator() {
        assertEquals("Static rope 50 m", name("{rope_type} rope {diameter} {length}", rope.without("diameter")))
        assertEquals("Static - 50 m", name("{rope_type} - {diameter} - {length}", rope.without("diameter")))
        assertEquals("Static, 50 m", name("{rope_type}, {diameter}, {length}", rope.without("diameter")))
    }

    @Test
    fun severalMissingValuesInARow() {
        assertEquals("Static, red", name("{rope_type}, {diameter} {length}, {note}", rope.without("diameter", "length")))
        assertEquals("Static red", name("{rope_type} {diameter}{length} {note}", rope.without("diameter", "length")))
    }

    @Test
    fun missingValueAtTheStartOrEndDropsItsSeparator() {
        assertEquals("10.5 mm - 50 m", name("{rope_type} - {diameter} - {length}", rope.without("rope_type")))
        assertEquals("Static - 10.5 mm", name("{rope_type} - {diameter} - {length}", rope.without("length")))
        assertEquals("Rope", name("{note}, rope / {length}", rope.without("note", "length")))
    }

    @Test
    fun emptyBracketsAreRemoved() {
        assertEquals("Static rope", name("{rope_type} rope ({diameter})", rope.without("diameter")))
        assertEquals("Static rope red", name("{rope_type} rope [ {diameter} ] {note}", rope.without("diameter")))
        assertEquals("Static rope (10.5 mm)", name("{rope_type} rope ({diameter})"))
    }

    @Test
    fun falseBoolBlankTextAndUnknownKeyAreEmpty() {
        val values = rope + ("dry" to JsonPrimitive(false)) + ("note" to JsonPrimitive("  "))
        assertEquals("Static", name("{rope_type} {dry} {note}", values))
        // A key whose definition is gone (soft-deleted) renders as empty
        assertEquals("Static", articleNameFromTemplate("{rope_type} {note}", listOf(type), rope))
        // A value of the wrong type is empty too
        assertEquals("Static", name("{rope_type} {length}", rope + ("length" to JsonPrimitive("long"))))
    }

    @Test
    fun noValueAtAllGivesAnEmptyName() {
        assertEquals("", name("{rope_type} {diameter}", null))
        assertEquals("", name("{rope_type} - {diameter}", JsonObject(emptyMap())))
        // Literal text stays
        assertEquals("Rope", name("{rope_type} rope {diameter}", null))
    }

    @Test
    fun whitespaceIsCollapsedAndTrimmed() {
        assertEquals("Static rope 50 m", name("  {rope_type}   rope\t{length}  "))
        assertEquals("Static", name("{rope_type}", rope + ("rope_type" to JsonPrimitive("  static "))))
    }

    @Test
    fun nameIsCutToTheMaxLength() {
        val long = rope + ("note" to JsonPrimitive("n".repeat(300)))
        assertEquals(255, name("{note}", long)!!.length)
        assertEquals("Static", name("{rope_type} rope", maxLength = 7))
    }

    // ---------- Sample values ----------

    @Test
    fun sampleValuesFillEveryPlaceholder() {
        val sample = sampleAttributeValues(all + def("plain", AttributeDataType.ENUM, "Plain"))
        assertEquals(
            "Static 10 mm 10 m Dry treated 2026-01-31 Note Plain",
            articleNameFromTemplate("{rope_type} {diameter} {length} {dry} {bought} {note} {plain}", all + def("plain", AttributeDataType.ENUM, "Plain"), sample),
        )
    }

    // ---------- Insertion ----------

    @Test
    fun insertsAtTheCursorWithSpaces() {
        assertEquals("{a}" to 3, insertPlaceholder("", 0, 0, "a"))
        assertEquals("Rope {d}" to 8, insertPlaceholder("Rope", 4, 4, "d"))
        assertEquals("Rope {d} mm" to 8, insertPlaceholder("Rope mm", 4, 4, "d"))
        assertEquals("{d} rope" to 4, insertPlaceholder("rope", 0, 0, "d"))
        assertEquals("Rope {d}" to 8, insertPlaceholder("Rope ", 5, 5, "d"))
    }

    @Test
    fun insertReplacesTheSelection() {
        assertEquals("A {k} B" to 5, insertPlaceholder("A xx B", 2, 4, "k"))
    }

    @Test
    fun insertClampsTheSelection() {
        assertEquals("ab {k}" to 6, insertPlaceholder("ab", 10, 20, "k"))
        assertEquals("{k} ab" to 4, insertPlaceholder("ab", -3, -1, "k"))
    }

    // ---------- {category} ----------

    private fun categoryName(template: String, category: String? = "Cordes", definitions: List<AttributeDefinition> = all) =
        articleNameFromTemplate(template, definitions, rope, MAX_NAME_TEMPLATE_LENGTH, category)

    @Test
    fun categoryKeyIsValidWithoutAttribute() {
        assertNull(validateNameTemplate("{category} {diameter}", keys))
        assertNull(validateNameTemplate("{ category | upper }", emptyList()))
        // Case-sensitive like the attribute keys
        assertEquals(NameTemplateError(NameTemplateError.Kind.UNKNOWN_KEY, "Category"), validateNameTemplate("{Category}", keys))
        assertEquals(NameTemplateError.Kind.UNKNOWN_FILTER, validateNameTemplate("{category|bold}", keys)?.kind)
    }

    @Test
    fun categoryKeyRendersTheCategoryName() {
        assertEquals("Cordes 10.5 mm 50 m", categoryName("{category} {diameter} {length}"))
        assertEquals("Static Cordes", categoryName("{rope_type} {category}"))
    }

    @Test
    fun categoryKeyTakesFilters() {
        assertEquals("CORDES", categoryName("{category|upper}"))
        assertEquals("X cordes", categoryName("x {category|lower}"))
        assertEquals("X Cordes", categoryName("x {category|capitalize}", "cordes"))
        assertEquals("X cordes", categoryName("x {category|raw}", "cordes"))
        // A unit filter is ignored
        assertEquals("Cordes", categoryName("{category|mm}"))
        assertEquals("CORDES", categoryName("{category|cm|raw|upper}"))
    }

    @Test
    fun categoryKeyWithoutCategoryNameIsEmpty() {
        assertEquals("Static rope", categoryName("{category} - {rope_type} rope", category = null))
        assertEquals("Static", categoryName("{rope_type} ({category})", category = " "))
    }

    @Test
    fun categoryAttributeWinsOverTheCategoryName() {
        val legacy = def("category", AttributeDataType.TEXT, "Category")
        val values = rope + ("category" to JsonPrimitive("climbing"))
        assertEquals("Climbing 10.5 mm", articleNameFromTemplate("{category} {diameter}", all + legacy, values, categoryName = "Cordes"))
        // The attribute without value is empty, not the category name
        assertEquals("10.5 mm", articleNameFromTemplate("{category} {diameter}", all + legacy, rope, categoryName = "Cordes"))
    }

    private operator fun JsonObject.plus(pair: Pair<String, JsonElement>) = JsonObject(this.toMap() + pair)
}
