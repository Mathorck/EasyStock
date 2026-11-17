package ch.mathorck.easystock.data.dao.tags

import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.data.model.location.Location
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [TagStore] following the [ch.mathorck.easystock.data.dao.tags.TagDao] rules for active rows. */
class FakeTagStore(vararg articles: Article) : TagStore {
    val articles = MutableStateFlow(articles.toList())
    val locations = MutableStateFlow(emptyList<Location>())
    var failing = false

    /** When set, lookups wait for it. */
    var lookupGate: CompletableDeferred<Unit>? = null
    val links = mutableListOf<Triple<TagTarget, String, TagTarget?>>()

    fun get(id: String) = articles.value.single { it.id == id }
    fun location(id: String) = locations.value.single { it.id == id }

    private fun ownerOf(tagId: String): TagOwner? =
        articles.value.firstOrNull { it.barcode == tagId }?.let { TagOwner.OfArticle(it) }
            ?: locations.value.firstOrNull { it.barcode == tagId }?.let { TagOwner.OfLocation(it) }

    override suspend fun findOwner(tagId: String): TagOwner? {
        lookupGate?.await()
        if (failing) error("db")
        return ownerOf(tagId)
    }

    override fun observeUntaggedArticles(): Flow<List<Article>> =
        articles.map { list -> list.filter { it.barcode == null && !it.archived }.sortedBy { it.name.lowercase() } }

    override fun observeTaggedArticles(): Flow<List<Article>> =
        articles.map { list -> list.filter { it.barcode != null }.sortedBy { it.name.lowercase() } }

    override fun observeLocations(): Flow<List<Location>> = locations.map { list -> list.sortedBy { it.name.lowercase() } }

    override suspend fun link(target: TagTarget, tagId: String, confirmedOwner: TagTarget?, now: Long): TagLinkResult {
        if (failing) error("db")
        links += Triple(target, tagId, confirmedOwner)
        val exists = when (target) {
            is TagTarget.OfArticle -> articles.value.any { it.id == target.id }
            is TagTarget.OfLocation -> locations.value.any { it.id == target.id }
        }
        if (!exists) return TagLinkResult.TargetGone
        val owner = ownerOf(tagId)
        if (owner != null && owner.target != target && owner.target != confirmedOwner) return TagLinkResult.LinkedElsewhere(owner)
        articles.value = articles.value.map {
            when {
                target == TagTarget.OfArticle(it.id) -> it.copy(barcode = tagId, updatedAt = now)
                it.barcode == tagId -> it.copy(barcode = null, updatedAt = now)
                else -> it
            }
        }
        locations.value = locations.value.map {
            when {
                target == TagTarget.OfLocation(it.id) -> it.copy(barcode = tagId, updatedAt = now)
                it.barcode == tagId -> it.copy(barcode = null, updatedAt = now)
                else -> it
            }
        }
        return TagLinkResult.Linked
    }

    override suspend fun unlink(target: TagTarget, now: Long): Boolean {
        if (failing) error("db")
        when (target) {
            is TagTarget.OfArticle -> {
                if (articles.value.none { it.id == target.id && it.barcode != null }) return false
                articles.value = articles.value.map { if (it.id == target.id) it.copy(barcode = null, updatedAt = now) else it }
            }
            is TagTarget.OfLocation -> {
                if (locations.value.none { it.id == target.id && it.barcode != null }) return false
                locations.value = locations.value.map { if (it.id == target.id) it.copy(barcode = null, updatedAt = now) else it }
            }
        }
        return true
    }
}
