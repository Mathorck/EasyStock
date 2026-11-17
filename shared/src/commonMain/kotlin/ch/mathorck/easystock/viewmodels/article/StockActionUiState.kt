package ch.mathorck.easystock.viewmodels.article

import ch.mathorck.easystock.data.dao.attributes.CategoryAttributeWithDefinition
import ch.mathorck.easystock.enums.article.ItemStatus
import ch.mathorck.easystock.viewmodels.stock.StockLocationNode
import kotlinx.serialization.json.JsonObject

// ---------- ViewModel ----------

data class StockActionUiState(
    val form: StockActionForm,
    val errors: StockActionErrors,
    /** Active locations as a tree, for the pickers. */
    val locations: List<StockLocationNode>,
    /** Path of [StockActionForm.sourceLocationId], or of the unit's location. */
    val sourcePath: String?,
    /** Stock at the source, for remove and move. */
    val availableMilli: Long?,
    /** Serial of the unit, for unit actions. */
    val unitSerial: String?,
    /** Stored status of the unit being edited (the form status may differ). */
    val currentStatus: ItemStatus? = null,
    /** Attributes of the article category: the fields of the unit attribute values. */
    val itemAttributes: List<CategoryAttributeWithDefinition> = emptyList(),
    /** Attribute values of the article, shown as placeholders for the unit ones. */
    val inheritedAttributes: JsonObject? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val canSave: Boolean get() = errors.isValid && !saving
}
