package ch.mathorck.easystock.viewmodels

import ch.mathorck.easystock.data.model.article.ArticleFlag

/** What the ViewModel needs to write manual flags. */
interface ArticleFlagStore {
    suspend fun insert(flag: ArticleFlag)

    /** Rows changed: 0 when unknown, already resolved or deleted. */
    suspend fun resolve(id: String, now: Long): Int
}
