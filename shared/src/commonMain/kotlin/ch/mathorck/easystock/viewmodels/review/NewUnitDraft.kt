package ch.mathorck.easystock.viewmodels.review

import ch.mathorck.easystock.data.dao.attributes.CategoryAttributeWithDefinition
import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.viewmodels.article.StockActionForm
import ch.mathorck.easystock.viewmodels.stock.SerialIndex

internal data class NewUnitDraft(
    val article: Article,
    val locationId: String,
    val form: StockActionForm,
    val itemAttributes: List<CategoryAttributeWithDefinition>,
    /** Every serial number in use, soft-deleted units included, indexed once when the form opens. */
    val serials: SerialIndex,
    val saving: Boolean = false,
    val failed: Boolean = false,
    val rejectedSerial: String? = null,
)
