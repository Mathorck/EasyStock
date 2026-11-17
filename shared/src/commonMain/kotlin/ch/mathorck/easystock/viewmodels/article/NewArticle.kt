package ch.mathorck.easystock.viewmodels.article

import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.data.model.categories.Category

/** What to save on create: the article, and the category to create first (if any). */
data class NewArticle(val article: Article, val newCategory: Category?)
