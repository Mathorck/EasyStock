package ch.mathorck.easystock.composables.attributes

import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.AttributeDefinition
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Article name templates (category.name_template), e.g. "{rope_type} rope {diameter} {length}"
// -> "Static rope 10.5 mm 50 m".
//
// Syntax:
//   {key}          value of the attribute with this AttributeDefinition.key, formatted like the stock list
//                  (formatAttributeValue: number with its unit, bool = its label when true, date ISO)
//   {key|filter}   filters, applied left to right: raw (number without unit), upper, lower, capitalize
//   {key|mm}       a unit: a measure (or a number with a known unit) converted to it ("50 m" -> "50000 mm").
//                  Ignored for other values and other families. Combines with raw: {key|cm|raw} -> "5000"
//   {category}     current name of the article's category. Built in: valid without attribute. An attribute
//                  whose key is "category" (created before this key existed) wins over it; new attributes
//                  never get this key (see newAttributeKey). Filters apply, a unit filter is ignored.
//   {{ and }}      literal braces
// Spaces inside the braces are ignored ("{ diameter | raw }").
//
// Rendering:
//   - An empty value (unset, blank, false bool) drops its placeholder, with the separator it leaves
//     behind ("{a} - {b} - {c}" without b -> "A - C"), and an empty bracket pair ("{a} ({b})" -> "A").
//   - Runs of spaces collapse to one, the result is trimmed, its first letter is upper-cased.

/** Built-in placeholder key: the name of the category. See [articleNameFromTemplate]. */
const val CATEGORY_TEMPLATE_KEY = "category"

/** Max length of a template (same as the article name, see docs/schema.dbml). */
const val MAX_NAME_TEMPLATE_LENGTH = 255

/** A filter after "|" in a placeholder. */
enum class NameTemplateFilter(val id: String) {
    /** Number without its unit ("10.5" instead of "10.5 mm"). Same as the value for other types. */
    RAW("raw"),
    UPPER("upper"),
    LOWER("lower"),
    /** First letter upper-cased. */
    CAPITALIZE("capitalize"),
    ;

    companion object {
        fun fromId(id: String): NameTemplateFilter? = entries.firstOrNull { it.id == id }
    }
}

sealed interface NameTemplatePart {
    data class Literal(val text: String) : NameTemplatePart
    /** [unit]: symbol of the unit filter ("mm"), null when none. */
    data class Placeholder(
        val key: String,
        val filters: List<NameTemplateFilter> = emptyList(),
        val unit: String? = null,
    ) : NameTemplatePart
}

/** A parsed, syntactically valid template. */
data class NameTemplate(val parts: List<NameTemplatePart>) {
    /** Keys of the placeholders, in order of first use. */
    val keys: List<String> get() = parts.filterIsInstance<NameTemplatePart.Placeholder>().map { it.key }.distinct()
}

/** Why a template is not valid. [detail] is the unknown key or filter. */
data class NameTemplateError(val kind: Kind, val detail: String? = null) {
    enum class Kind {
        /** Unclosed "{", single "}", or a placeholder without key ("{}", "{|upper}"). */
        SYNTAX,
        /** No attribute of the category has this key (and it is not [CATEGORY_TEMPLATE_KEY]). */
        UNKNOWN_KEY,
        UNKNOWN_FILTER,
        TOO_LONG,
    }
}

sealed interface NameTemplateParseResult {
    data class Valid(val template: NameTemplate) : NameTemplateParseResult
    data class Invalid(val error: NameTemplateError) : NameTemplateParseResult
}

/** Parses [text]. Checks the syntax and the filters, not the keys (see [validateNameTemplate]). */
fun parseNameTemplate(text: String): NameTemplateParseResult {
    if (text.length > MAX_NAME_TEMPLATE_LENGTH) return invalid(NameTemplateError.Kind.TOO_LONG)
    val parts = mutableListOf<NameTemplatePart>()
    val literal = StringBuilder()
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '{' && text.getOrNull(i + 1) == '{' -> { literal.append('{'); i += 2 }
            c == '}' && text.getOrNull(i + 1) == '}' -> { literal.append('}'); i += 2 }
            c == '}' -> return invalid(NameTemplateError.Kind.SYNTAX)
            c == '{' -> {
                val end = text.indexOf('}', i + 1)
                if (end < 0) return invalid(NameTemplateError.Kind.SYNTAX)
                val body = text.substring(i + 1, end)
                if ('{' in body) return invalid(NameTemplateError.Kind.SYNTAX)
                val tokens = body.split('|').map { it.trim() }
                val key = tokens.first()
                if (key.isEmpty()) return invalid(NameTemplateError.Kind.SYNTAX)
                var unit: String? = null
                val filters = tokens.drop(1).mapNotNull { id ->
                    NameTemplateFilter.fromId(id.lowercase()) ?: run {
                        unit = MeasureUnits.find(id)?.symbol ?: return invalid(NameTemplateError.Kind.UNKNOWN_FILTER, id)
                        null
                    }
                }
                if (literal.isNotEmpty()) {
                    parts += NameTemplatePart.Literal(literal.toString())
                    literal.clear()
                }
                parts += NameTemplatePart.Placeholder(key, filters, unit)
                i = end + 1
            }
            else -> { literal.append(c); i++ }
        }
    }
    if (literal.isNotEmpty()) parts += NameTemplatePart.Literal(literal.toString())
    return NameTemplateParseResult.Valid(NameTemplate(parts))
}

private fun invalid(kind: NameTemplateError.Kind, detail: String? = null) =
    NameTemplateParseResult.Invalid(NameTemplateError(kind, detail))

/**
 * Checks [text] against the keys of the category's attributes, plus the built-in [CATEGORY_TEMPLATE_KEY].
 * A blank text means "no template" and is valid.
 * Returns the first error, null when valid.
 */
fun validateNameTemplate(text: String, keys: Collection<String>): NameTemplateError? {
    if (text.isBlank()) return null
    return when (val result = parseNameTemplate(text.trim())) {
        is NameTemplateParseResult.Invalid -> result.error
        is NameTemplateParseResult.Valid -> result.template.keys.firstOrNull { it !in keys && it != CATEGORY_TEMPLATE_KEY }
            ?.let { NameTemplateError(NameTemplateError.Kind.UNKNOWN_KEY, it) }
    }
}

/** The valid template of a category, null when [text] is null, blank or not valid. */
fun nameTemplateOrNull(text: String?): NameTemplate? {
    if (text.isNullOrBlank()) return null
    return (parseNameTemplate(text.trim()) as? NameTemplateParseResult.Valid)?.template
}

/**
 * Renders [template]. [value] gives the text of a placeholder, null or blank when empty.
 * Returns the cleaned name (may be empty when every value is empty and there is no literal text).
 */
fun NameTemplate.render(value: (NameTemplatePart.Placeholder) -> String?): String {
    val pieces = mutableListOf<Piece>()
    parts.forEach { part ->
        when (part) {
            is NameTemplatePart.Literal -> pieces += Piece.Text(part.text)
            is NameTemplatePart.Placeholder -> {
                val text = value(part)?.trim()
                pieces += if (text.isNullOrEmpty()) Piece.Gap else Piece.Value(part.filters.fold(text, ::applyFilter))
            }
        }
    }
    resolveGaps(pieces)
    val joined = pieces.joinToString("") {
        when (it) {
            is Piece.Text -> it.text
            is Piece.Value -> it.text
            Piece.Gap -> ""
        }
    }
    return joined
        .replace(EMPTY_BRACKETS, " ")
        .replace(WHITESPACE, " ")
        .trim()
        .replaceFirstChar { it.uppercaseChar() }
}

/**
 * Name of an article from [template] and its attribute [values]. [definitions]: active attributes of its category;
 * a key without definition renders as empty, except [CATEGORY_TEMPLATE_KEY] which renders [categoryName]
 * (an attribute with that key wins). Returns null without valid template, the name otherwise
 * (possibly empty), cut to [maxLength].
 */
fun articleNameFromTemplate(
    template: String?,
    definitions: Collection<AttributeDefinition>,
    values: JsonObject?,
    maxLength: Int = MAX_NAME_TEMPLATE_LENGTH,
    categoryName: String? = null,
): String? {
    val parsed = nameTemplateOrNull(template) ?: return null
    val byKey = definitions.associateBy { it.key }
    return parsed.render { placeholder ->
        val definition = byKey[placeholder.key]
            ?: return@render categoryName.takeIf { placeholder.key == CATEGORY_TEMPLATE_KEY }
        val value = values.attributeValue(placeholder.key)
        val text = placeholder.unit?.let { convertedAttributeText(definition, value, it) }
            ?: formatAttributeValue(definition, value)
        text?.let { if (NameTemplateFilter.RAW in placeholder.filters) it.raw else it.text }
    }.take(maxLength).trimEnd()
}

/**
 * Example values for the template preview of a category without article: the label for text and bool,
 * "10" (with unit) for numbers, the first option for enums, a fixed date.
 */
fun sampleAttributeValues(definitions: Collection<AttributeDefinition>): JsonObject = JsonObject(
    definitions.associate { definition ->
        val value: JsonElement? = when (definition.dataType) {
            AttributeDataType.TEXT -> JsonPrimitive(definition.label)
            AttributeDataType.NUMBER -> parseAttributeNumber("10")
            AttributeDataType.BOOL -> JsonPrimitive(true)
            AttributeDataType.DATE -> JsonPrimitive("2026-01-31")
            AttributeDataType.ENUM -> definition.options?.firstOrNull()?.let { JsonPrimitive(it) }
            AttributeDataType.MEASURE -> measureValueFromInput("10", definition.measureUnit?.symbol.orEmpty())
        }
        definition.key to (value ?: JsonPrimitive(definition.label))
    },
)

/**
 * [value] of a measure (or number with a known unit) converted to the unit [symbol], null when it can't be
 * (other type, unknown unit, other family): the placeholder then shows the value as entered.
 */
private fun convertedAttributeText(definition: AttributeDefinition, value: JsonElement?, symbol: String): AttributeText? {
    val target = MeasureUnits.find(symbol) ?: return null
    val measure = when (definition.dataType) {
        AttributeDataType.MEASURE -> readMeasure(definition, value)
        AttributeDataType.NUMBER -> definition.measureUnit?.let { unit ->
            (value as? JsonPrimitive)?.takeIf { !it.isString }?.content?.let(::normalizeAttributeNumber)?.let { MeasureValue(it, unit) }
        }
        else -> null
    } ?: return null
    val number = measure.numberIn(target) ?: return null
    return AttributeText(number, "$number ${target.symbol}")
}

/**
 * Inserts the placeholder of [key] into [text] in place of the selection [start]..[end],
 * with a space before and after when it would touch other text. Returns the new text and the cursor position.
 */
fun insertPlaceholder(text: String, start: Int, end: Int, key: String): Pair<String, Int> {
    val from = start.coerceIn(0, text.length)
    val to = end.coerceIn(from, text.length)
    val before = text.substring(0, from)
    val after = text.substring(to)
    val prefix = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
    val suffix = if (after.isNotEmpty() && !after.first().isWhitespace()) " " else ""
    val inserted = "$prefix{$key}$suffix"
    return (before + inserted + after) to (before.length + inserted.length)
}

private fun applyFilter(text: String, filter: NameTemplateFilter): String = when (filter) {
    NameTemplateFilter.RAW -> text
    NameTemplateFilter.UPPER -> text.uppercase()
    NameTemplateFilter.LOWER -> text.lowercase()
    NameTemplateFilter.CAPITALIZE -> text.replaceFirstChar { it.uppercaseChar() }
}

private sealed interface Piece {
    data class Text(val text: String) : Piece
    data class Value(val text: String) : Piece
    data object Gap : Piece
}

/** Characters that only separate values: dropped next to an empty value. */
private fun Char.isSeparator(): Boolean = isWhitespace() || this in "-–—,;:/|·•_+&×*"

private val EMPTY_BRACKETS = Regex("""\(\s*\)|\[\s*]""")
private val WHITESPACE = Regex("""\s+""")

/**
 * Removes the empty values ([Piece.Gap]) and the separators they leave: between two texts only one separator
 * is kept (the one before the empty value), at the start or the end none.
 */
private fun resolveGaps(pieces: MutableList<Piece>) {
    while (true) {
        normalize(pieces)
        val i = pieces.indexOfFirst { it == Piece.Gap }
        if (i < 0) return
        val prev = pieces.getOrNull(i - 1)
        val next = pieces.getOrNull(i + 1)
        when {
            prev is Piece.Text && next is Piece.Text -> {
                val left = prev.text.trimEnd { it.isSeparator() }
                val right = next.text.trimStart { it.isSeparator() }
                val atStart = i - 1 == 0 && left.isEmpty()
                val atEnd = i + 1 == pieces.lastIndex && right.isEmpty()
                val separator = when {
                    atStart || atEnd -> ""
                    else -> prev.text.substring(left.length).ifEmpty { next.text.substring(0, next.text.length - right.length) }
                }
                pieces[i - 1] = Piece.Text(left + separator + right)
                pieces.removeAt(i + 1)
                pieces.removeAt(i)
            }
            prev == null && next is Piece.Text -> {
                pieces[i + 1] = Piece.Text(next.text.trimStart { it.isSeparator() })
                pieces.removeAt(i)
            }
            next == null && prev is Piece.Text -> {
                pieces[i - 1] = Piece.Text(prev.text.trimEnd { it.isSeparator() })
                pieces.removeAt(i)
            }
            else -> pieces.removeAt(i)
        }
    }
}

/** Drops empty texts, merges adjacent texts and adjacent gaps. */
private fun normalize(pieces: MutableList<Piece>) {
    pieces.removeAll { it is Piece.Text && it.text.isEmpty() }
    var i = 1
    while (i < pieces.size) {
        val a = pieces[i - 1]
        val b = pieces[i]
        when {
            a is Piece.Text && b is Piece.Text -> { pieces[i - 1] = Piece.Text(a.text + b.text); pieces.removeAt(i) }
            a == Piece.Gap && b == Piece.Gap -> pieces.removeAt(i)
            else -> i++
        }
    }
}
