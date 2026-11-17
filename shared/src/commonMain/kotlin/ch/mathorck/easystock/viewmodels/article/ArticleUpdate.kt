package ch.mathorck.easystock.viewmodels.article

import ch.mathorck.easystock.data.dao.article.ArticleChanges
import ch.mathorck.easystock.data.model.categories.Category

/** What to save on edit: the changed fields, and the category to create first (if any). */
data class ArticleUpdate(val changes: ArticleChanges, val newCategory: Category?)
