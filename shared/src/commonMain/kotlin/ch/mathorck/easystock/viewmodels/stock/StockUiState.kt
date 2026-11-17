package ch.mathorck.easystock.viewmodels.stock

import ch.mathorck.easystock.data.model.article.ArticleStock
import ch.mathorck.easystock.data.model.attributes.AttributeDefinition
import ch.mathorck.easystock.data.model.attributes.PastDate
import ch.mathorck.easystock.data.model.categories.Category
import ch.mathorck.easystock.data.model.location.Location

data class StockUiState(
    val loading: Boolean = true,
    /** Articles matching the search query and the filters. */
    val items: List<ArticleStock> = emptyList(),
    /** Number of articles before filtering, to tell "no articles" from "no results". */
    val totalCount: Int = 0,
    /** Locations in hierarchy order (depth-first, siblings by name). */
    val locations: List<StockLocationNode> = emptyList(),
    val categories: List<Category> = emptyList(),
    /** Active attribute definitions, by label: the choices of the attribute filter. */
    val attributeDefinitions: List<AttributeDefinition> = emptyList(),
    /** Active filters. Ids that no longer exist are already dropped. */
    val filters: StockFilters = StockFilters(),
    /** Attribute summary line per article id ("static · 10.5 mm"). Articles without values are absent. */
    val attributeLines: Map<String, String> = emptyMap(),
    /** Ids of the articles with an open flag (manual or from a review): they get a flag badge. */
    val flaggedIds: Set<String> = emptySet(),
    /** Past dates per article id (date attributes that flag when past): they get a date badge. */
    val pastDatesByArticle: Map<String, List<PastDate>> = emptyMap(),
) {
    val selectedLocation: Location? get() = locations.firstOrNull { it.location.id == filters.locationId }?.location
    val selectedCategory: Category? get() = categories.firstOrNull { it.id == filters.categoryId }
    val selectedAttribute: AttributeDefinition? get() = attributeDefinitions.firstOrNull { it.id == filters.attributeId }
}
