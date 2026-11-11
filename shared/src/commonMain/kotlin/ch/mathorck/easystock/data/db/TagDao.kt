package ch.mathorck.easystock.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Location
import kotlinx.coroutines.flow.Flow

/** What an NFC tag can be linked to. */
sealed interface TagTarget {
    val id: String

    data class OfArticle(override val id: String) : TagTarget

    data class OfLocation(override val id: String) : TagTarget
}

/** The active row holding a tag. */
sealed interface TagOwner {
    val target: TagTarget
    val name: String

    data class OfArticle(val article: Article) : TagOwner {
        override val target: TagTarget get() = TagTarget.OfArticle(article.id)
        override val name: String get() = article.name
    }

    data class OfLocation(val location: Location) : TagOwner {
        override val target: TagTarget get() = TagTarget.OfLocation(location.id)
        override val name: String get() = location.name
    }
}

/** Result of [TagDao.linkTag]. */
sealed interface TagLinkResult {
    /** The tag is now on the target (also when it already was). */
    data object Linked : TagLinkResult

    /** Another active article or location holds the tag: nothing changed. Ask the user, then call again with it. */
    data class LinkedElsewhere(val owner: TagOwner) : TagLinkResult

    /** The target is deleted (or never existed): nothing changed. */
    data object TargetGone : TagLinkResult
}

/** The tag is already linked to an active article or location. */
class TagAlreadyLinkedException(val tagId: String) : IllegalStateException("Tag already linked: $tagId")

/**
 * NFC tags of articles and locations. One tag = one thing: a tag is on one article or one location,
 * never both. Each table has its own unique index; this DAO keeps the rule across both tables.
 * Soft-deleted rows can still hold a tag (e.g. after a backup merge): they are freed without asking.
 */
@Dao
abstract class TagDao {
    @Query("SELECT * FROM article WHERE barcode = :tagId AND deleted_at IS NULL LIMIT 1")
    protected abstract suspend fun activeArticleWithTag(tagId: String): Article?

    @Query("SELECT * FROM location WHERE barcode = :tagId AND deleted_at IS NULL LIMIT 1")
    protected abstract suspend fun activeLocationWithTag(tagId: String): Location?

    @Query("SELECT * FROM article WHERE id = :id AND deleted_at IS NULL")
    protected abstract suspend fun activeArticle(id: String): Article?

    @Query("SELECT * FROM location WHERE id = :id AND deleted_at IS NULL")
    protected abstract suspend fun activeLocation(id: String): Location?

    @Query("UPDATE article SET barcode = :tagId, updated_at = :now WHERE id = :id")
    protected abstract suspend fun setArticleTag(id: String, tagId: String?, now: Long)

    @Query("UPDATE location SET barcode = :tagId, updated_at = :now WHERE id = :id")
    protected abstract suspend fun setLocationTag(id: String, tagId: String?, now: Long)

    /** Clears the tag from every row holding it (soft-deleted ones included), except [keepId]. */
    @Query("UPDATE article SET barcode = NULL, updated_at = :now WHERE barcode = :tagId AND id <> :keepId")
    protected abstract suspend fun clearArticleTag(tagId: String, keepId: String, now: Long)

    @Query("UPDATE location SET barcode = NULL, updated_at = :now WHERE barcode = :tagId AND id <> :keepId")
    protected abstract suspend fun clearLocationTag(tagId: String, keepId: String, now: Long)

    @Query("UPDATE article SET barcode = NULL, updated_at = :now WHERE id = :id AND deleted_at IS NULL AND barcode IS NOT NULL")
    protected abstract suspend fun unlinkArticleRow(id: String, now: Long): Int

    @Query("UPDATE location SET barcode = NULL, updated_at = :now WHERE id = :id AND deleted_at IS NULL AND barcode IS NOT NULL")
    protected abstract suspend fun unlinkLocationRow(id: String, now: Long): Int

    /** Active, non-archived articles without an NFC tag, the choice when linking a new tag. */
    @Query("SELECT * FROM article WHERE deleted_at IS NULL AND archived = 0 AND barcode IS NULL ORDER BY name COLLATE NOCASE")
    abstract fun observeUntaggedArticles(): Flow<List<Article>>

    /** Active articles holding an NFC tag, archived ones included (a scan still opens them), by name. */
    @Query("SELECT * FROM article WHERE deleted_at IS NULL AND barcode IS NOT NULL ORDER BY name COLLATE NOCASE, id")
    abstract fun observeTaggedArticles(): Flow<List<Article>>

    /** All active locations, tagged or not (the paths need the parents), by name. */
    @Query("SELECT * FROM location WHERE deleted_at IS NULL ORDER BY name COLLATE NOCASE, id")
    abstract fun observeLocations(): Flow<List<Location>>

    /** The active article or location holding [tagId], null when none. */
    @Transaction
    open suspend fun findOwner(tagId: String): TagOwner? =
        activeArticleWithTag(tagId)?.let { TagOwner.OfArticle(it) }
            ?: activeLocationWithTag(tagId)?.let { TagOwner.OfLocation(it) }

    /**
     * Links [tagId] to the active [target], in one transaction.
     * If another active article or location holds the tag, nothing changes and [TagLinkResult.LinkedElsewhere]
     * is returned, unless it is [confirmedOwner] (the user agreed to move the tag): its tag is cleared first.
     * Soft-deleted rows holding the tag are freed without asking. Every changed row gets `updated_at` = [now].
     */
    @Transaction
    open suspend fun linkTag(target: TagTarget, tagId: String, confirmedOwner: TagTarget?, now: Long): TagLinkResult {
        val current: String? = when (target) {
            is TagTarget.OfArticle -> (activeArticle(target.id) ?: return TagLinkResult.TargetGone).barcode
            is TagTarget.OfLocation -> (activeLocation(target.id) ?: return TagLinkResult.TargetGone).barcode
        }
        val owner = findOwner(tagId)
        if (owner != null && owner.target != target && owner.target != confirmedOwner) return TagLinkResult.LinkedElsewhere(owner)
        // Cleared first: the unique index allows one row per tag (and the rule allows one owner)
        val keepArticle = (target as? TagTarget.OfArticle)?.id ?: ""
        val keepLocation = (target as? TagTarget.OfLocation)?.id ?: ""
        clearArticleTag(tagId, keepArticle, now)
        clearLocationTag(tagId, keepLocation, now)
        if (current != tagId) {
            when (target) {
                is TagTarget.OfArticle -> setArticleTag(target.id, tagId, now)
                is TagTarget.OfLocation -> setLocationTag(target.id, tagId, now)
            }
        }
        return TagLinkResult.Linked
    }

    /** Removes the NFC tag of the active [target]. Returns false when there was none (or no such row). */
    @Transaction
    open suspend fun unlinkTag(target: TagTarget, now: Long): Boolean = when (target) {
        is TagTarget.OfArticle -> unlinkArticleRow(target.id, now)
        is TagTarget.OfLocation -> unlinkLocationRow(target.id, now)
    } > 0
}
