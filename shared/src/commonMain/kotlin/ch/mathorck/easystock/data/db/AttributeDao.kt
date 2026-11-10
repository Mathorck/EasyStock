package ch.mathorck.easystock.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.AttributeDefinition
import ch.mathorck.easystock.data.model.CategoryAttribute
import kotlinx.coroutines.flow.Flow

/** Max length of [AttributeDefinition.key] (schema: varchar(50)). */
const val MAX_ATTRIBUTE_KEY_LENGTH = 50

/** Key used when the label has no usable letter or digit. */
const val FALLBACK_ATTRIBUTE_KEY = "attribute"

// Letters folded to ASCII. German umlauts follow the usual "ae/oe/ue" spelling.
private val ASCII_FOLDING: Map<Char, String> = buildMap {
    fun map(chars: String, ascii: String) = chars.forEach { put(it, ascii) }
    map("àáâãåā", "a"); map("ä", "ae"); map("æ", "ae")
    map("ç", "c"); map("èéêëē", "e"); map("ìíîïī", "i"); map("ñ", "n")
    map("òóôõøō", "o"); map("ö", "oe"); map("œ", "oe")
    map("ùúûū", "u"); map("ü", "ue"); map("ýÿ", "y"); map("ß", "ss")
}

/**
 * Slug of [label]: lowercase ASCII letters and digits, words joined by "_"
 * ("Breaking strength" -> "breaking_strength", "Länge" -> "laenge"), at most [MAX_ATTRIBUTE_KEY_LENGTH] chars.
 * Returns [FALLBACK_ATTRIBUTE_KEY] when nothing is left.
 */
fun attributeKeySlug(label: String): String {
    val folded = buildString {
        label.lowercase().forEach { c ->
            when {
                c in 'a'..'z' || c in '0'..'9' -> append(c)
                else -> append(ASCII_FOLDING[c] ?: "_")
            }
        }
    }
    val slug = folded.split('_').filter { it.isNotEmpty() }.joinToString("_")
        .take(MAX_ATTRIBUTE_KEY_LENGTH).trimEnd('_')
    return slug.ifEmpty { FALLBACK_ATTRIBUTE_KEY }
}

/**
 * Unique key for a new attribute: the slug of [label], suffixed "_2", "_3"... when it is taken.
 * [existingKeys] must include soft-deleted definitions: a reused key would revive their old values.
 * The result never exceeds [MAX_ATTRIBUTE_KEY_LENGTH] chars.
 */
fun newAttributeKey(label: String, existingKeys: Collection<String>): String {
    val taken = existingKeys.toHashSet()
    val base = attributeKeySlug(label)
    if (base !in taken) return base
    var n = 2
    while (true) {
        val suffix = "_$n"
        val candidate = base.take(MAX_ATTRIBUTE_KEY_LENGTH - suffix.length).trimEnd('_') + suffix
        if (candidate !in taken) return candidate
        n++
    }
}

/** An attribute linked to a category, with its definition. */
data class CategoryAttributeWithDefinition(
    @Embedded val link: CategoryAttribute,
    @Relation(parentColumn = "attribute_id", entityColumn = "id")
    val definition: AttributeDefinition,
)

/** One wanted link of a category, see [AttributeDao.setCategoryAttributes]. */
data class CategoryAttributeInput(val attributeId: String, val required: Boolean)

/** The attribute definition was deleted (or never existed). */
class AttributeNotFoundException(val id: String) : IllegalStateException("No active attribute: $id")

/** Attribute definitions and their links to categories. */
@Dao
abstract class AttributeDao {
    /** Active definitions, by label. */
    @Query("SELECT * FROM attribute_definition WHERE deleted_at IS NULL ORDER BY label COLLATE NOCASE, id")
    abstract fun observeDefinitions(): Flow<List<AttributeDefinition>>

    /** Keys of all definitions, soft-deleted ones included (the unique index covers them). */
    @Query("SELECT `key` FROM attribute_definition")
    abstract suspend fun allKeys(): List<String>

    @Query("SELECT * FROM attribute_definition WHERE id = :id AND deleted_at IS NULL")
    abstract suspend fun getActiveDefinition(id: String): AttributeDefinition?

    @Insert
    protected abstract suspend fun insertDefinitionRow(definition: AttributeDefinition)

    @Update
    protected abstract suspend fun updateDefinitionRow(definition: AttributeDefinition)

    /**
     * Creates a definition. Its key is derived from [label] ([newAttributeKey]) and never changes afterwards.
     * [options] is only kept for [AttributeDataType.ENUM]. Returns the stored row.
     */
    @Transaction
    open suspend fun createDefinition(
        id: String,
        label: String,
        dataType: AttributeDataType,
        unit: String?,
        options: List<String>?,
        now: Long,
    ): AttributeDefinition {
        val definition = AttributeDefinition(
            id = id,
            key = newAttributeKey(label, allKeys()),
            label = label,
            dataType = dataType,
            unit = unit,
            options = options.takeIf { dataType == AttributeDataType.ENUM },
            createdAt = now,
            updatedAt = now,
        )
        insertDefinitionRow(definition)
        return definition
    }

    /**
     * Changes the label, unit and options. The key and the data type are kept: stored values depend on them.
     * Throws [AttributeNotFoundException] if the definition is deleted.
     */
    @Transaction
    open suspend fun updateDefinition(id: String, label: String, unit: String?, options: List<String>?, now: Long) {
        val stored = getActiveDefinition(id) ?: throw AttributeNotFoundException(id)
        updateDefinitionRow(
            stored.copy(
                label = label,
                unit = unit,
                options = options.takeIf { stored.dataType == AttributeDataType.ENUM },
                updatedAt = now,
            ),
        )
    }

    @Query("UPDATE attribute_definition SET deleted_at = :now, updated_at = :now WHERE id = :id AND deleted_at IS NULL")
    protected abstract suspend fun softDeleteDefinitionRow(id: String, now: Long)

    @Query("UPDATE category_attribute SET deleted_at = :now, updated_at = :now WHERE attribute_id = :attributeId AND deleted_at IS NULL")
    protected abstract suspend fun softDeleteLinksOfAttribute(attributeId: String, now: Long)

    /**
     * Soft-deletes the definition and its category links. Values stored in `attributes` are kept,
     * the key stays reserved.
     */
    @Transaction
    open suspend fun softDeleteDefinition(id: String, now: Long) {
        softDeleteDefinitionRow(id, now)
        softDeleteLinksOfAttribute(id, now)
    }

    /** Active attributes of the category, by sort order. Deleted links and definitions are left out. */
    @Transaction
    @Query(
        """
        SELECT category_attribute.* FROM category_attribute
        JOIN attribute_definition ON attribute_definition.id = category_attribute.attribute_id
        WHERE category_attribute.category_id = :categoryId
          AND category_attribute.deleted_at IS NULL
          AND attribute_definition.deleted_at IS NULL
        ORDER BY category_attribute.sort_order, attribute_definition.label COLLATE NOCASE
        """,
    )
    abstract fun observeCategoryAttributes(categoryId: String): Flow<List<CategoryAttributeWithDefinition>>

    /** All links of the category, soft-deleted ones included. */
    @Query("SELECT * FROM category_attribute WHERE category_id = :categoryId")
    protected abstract suspend fun allLinksOfCategory(categoryId: String): List<CategoryAttribute>

    @Insert
    protected abstract suspend fun insertLink(link: CategoryAttribute)

    @Update
    protected abstract suspend fun updateLink(link: CategoryAttribute)

    /**
     * Sets the attributes of a category, in this order (sort_order = index), in one transaction.
     * Links not in [attributes] are soft-deleted, a previously deleted link is restored (the unique index
     * covers deleted rows), others are inserted. Unchanged links keep their `updated_at`.
     * Every attribute must be an active definition, listed once.
     */
    @Transaction
    open suspend fun setCategoryAttributes(
        categoryId: String,
        attributes: List<CategoryAttributeInput>,
        now: Long,
        newId: () -> String,
    ) {
        require(attributes.map { it.attributeId }.toSet().size == attributes.size) { "Attribute listed twice" }
        attributes.forEach { if (getActiveDefinition(it.attributeId) == null) throw AttributeNotFoundException(it.attributeId) }
        val existing = allLinksOfCategory(categoryId).associateBy { it.attributeId }
        val wanted = attributes.map { it.attributeId }.toSet()
        existing.values
            .filter { it.deletedAt == null && it.attributeId !in wanted }
            .forEach { updateLink(it.copy(deletedAt = now, updatedAt = now)) }
        attributes.forEachIndexed { index, input ->
            val link = existing[input.attributeId]
            when {
                link == null -> insertLink(
                    CategoryAttribute(
                        id = newId(),
                        categoryId = categoryId,
                        attributeId = input.attributeId,
                        required = input.required,
                        sortOrder = index,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                link.deletedAt != null || link.required != input.required || link.sortOrder != index ->
                    updateLink(link.copy(required = input.required, sortOrder = index, deletedAt = null, updatedAt = now))
            }
        }
    }
}
