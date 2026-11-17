package ch.mathorck.easystock.viewmodels.attributes

import ch.mathorck.easystock.data.dao.attributes.CategoryAttributeWithDefinition
import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.data.model.categories.Category

/** Stored state of the category: its links, template and articles. */
internal data class StoredCategory(
    val category: Category?,
    val links: List<CategoryAttributeWithDefinition>,
    val sampleArticle: Article?,
    val manualNameCount: Int,
)
