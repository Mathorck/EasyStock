package ch.mathorck.easystock.viewmodels.article

import ch.mathorck.easystock.enums.article.Tracking
import ch.mathorck.easystock.enums.categories.CategoryMode
import kotlinx.serialization.json.JsonObject

/** Raw input of the article form, as typed. */
data class ArticleForm(
    val tracking: Tracking = Tracking.QUANTITY,
    val name: String = "",
    val sku: String = "",
    val description: String = "",
    val unit: String = DEFAULT_UNIT,
    val categoryMode: CategoryMode = CategoryMode.NONE,
    val categoryId: String? = null,
    val categoryName: String = "",
    val threshold: String = "",
    /** Optional web link where the article is ordered, see [checkOrderUrl]. */
    val orderUrl: String = "",
    /** Photo path (see [ch.mathorck.easystock.services.images.newImagePath]), null without photo. */
    val imagePath: String? = null,
    /** Custom attribute values, keyed by attribute key (see [ch.mathorck.easystock.composables.attributes.AttributeFields]). */
    val attributes: JsonObject? = null,
    /**
     * The name follows the template of the category (see [withTemplateName]). Typing a name clears it.
     * New articles start following it, so the name fills in once a category with a template is chosen.
     */
    val nameAuto: Boolean = true,
) {
    /** The unit that is saved: fixed for serial-tracked articles. */
    val effectiveUnit: String get() = if (tracking == Tracking.SERIAL) DEFAULT_UNIT else unit.trim()
}
