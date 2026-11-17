package ch.mathorck.easystock.data.dao.article

import kotlinx.serialization.json.JsonObject

/** Edited fields of an article. Everything else is kept from the stored row. */
data class ArticleChanges(
    val name: String,
    /** The name follows the category template. */
    val nameAuto: Boolean,
    val sku: String?,
    val description: String?,
    val categoryId: String?,
    val unit: String,
    val thresholdMilli: Long?,
    /** Photo path, see [ch.mathorck.easystock.services.images.newImagePath]. */
    val imagePath: String?,
    /** Custom attribute values, keyed by attribute key. Replaces the stored object. */
    val attributes: JsonObject?,
    /** Web link where the article is ordered, null for none. */
    val orderUrl: String?,
)
