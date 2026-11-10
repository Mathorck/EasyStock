package ch.mathorck.easystock.data.db

import ch.mathorck.easystock.data.model.Article
import kotlinx.coroutines.flow.Flow

/** NFC tag lookups and links of articles. See [ArticleDao] for the rules; fakes replace it in tests. */
interface ArticleTags {
    /** The active article holding the tag, null when none. */
    suspend fun findByTag(tagId: String): Article?

    /** Active, non-archived articles without a tag, sorted by name. */
    fun observeUntagged(): Flow<List<Article>>

    /** See [ArticleDao.linkTag]. */
    suspend fun link(articleId: String, tagId: String, confirmedOwnerId: String?, now: Long): TagLinkResult

    /** See [ArticleDao.unlinkTag]. */
    suspend fun unlink(articleId: String, now: Long): Boolean
}

fun ArticleDao.asArticleTags(): ArticleTags = object : ArticleTags {
    override suspend fun findByTag(tagId: String): Article? = findByBarcode(tagId)
    override fun observeUntagged(): Flow<List<Article>> = this@asArticleTags.observeUntagged()
    override suspend fun link(articleId: String, tagId: String, confirmedOwnerId: String?, now: Long): TagLinkResult =
        linkTag(articleId, tagId, confirmedOwnerId, now)
    override suspend fun unlink(articleId: String, now: Long): Boolean = unlinkTag(articleId, now)
}
