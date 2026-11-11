package ch.mathorck.easystock.data.db

import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Location
import kotlinx.coroutines.flow.Flow

/** NFC tag lookups and links of articles and locations. See [TagDao] for the rules; fakes replace it in tests. */
interface TagStore {
    /** The active article or location holding the tag, null when none. */
    suspend fun findOwner(tagId: String): TagOwner?

    /** Active, non-archived articles without a tag, sorted by name. */
    fun observeUntaggedArticles(): Flow<List<Article>>

    /** All active locations (tagged or not, for the paths), sorted by name. */
    fun observeLocations(): Flow<List<Location>>

    /** See [TagDao.linkTag]. */
    suspend fun link(target: TagTarget, tagId: String, confirmedOwner: TagTarget?, now: Long): TagLinkResult

    /** See [TagDao.unlinkTag]. */
    suspend fun unlink(target: TagTarget, now: Long): Boolean
}

fun TagDao.asTagStore(): TagStore = object : TagStore {
    override suspend fun findOwner(tagId: String): TagOwner? = this@asTagStore.findOwner(tagId)
    override fun observeUntaggedArticles(): Flow<List<Article>> = this@asTagStore.observeUntaggedArticles()
    override fun observeLocations(): Flow<List<Location>> = this@asTagStore.observeLocations()
    override suspend fun link(target: TagTarget, tagId: String, confirmedOwner: TagTarget?, now: Long): TagLinkResult =
        linkTag(target, tagId, confirmedOwner, now)
    override suspend fun unlink(target: TagTarget, now: Long): Boolean = unlinkTag(target, now)
}
