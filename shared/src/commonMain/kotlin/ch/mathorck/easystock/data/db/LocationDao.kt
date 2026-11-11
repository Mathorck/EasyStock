package ch.mathorck.easystock.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Item
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.LocationColors
import ch.mathorck.easystock.data.model.LocationIcons
import ch.mathorck.easystock.viewmodels.LocationError
import ch.mathorck.easystock.viewmodels.childrenOf
import ch.mathorck.easystock.viewmodels.subtreeIds
import ch.mathorck.easystock.viewmodels.validateLocationName
import ch.mathorck.easystock.viewmodels.wouldCreateCycle
import kotlinx.coroutines.flow.Flow

/** A location write was refused for [error]. The transaction is rolled back. */
class LocationException(val error: LocationError) : IllegalStateException("Location write refused: $error")

/** Number of articles with a non-zero stock directly at [locationId] (descendants not included). */
data class LocationArticleCount(
    @ColumnInfo(name = "location_id") val locationId: String,
    @ColumnInfo(name = "article_count") val articleCount: Int,
)

/** Stock (sum of the movements) of one article at one location. */
data class LocationArticleStock(
    @Embedded val article: Article,
    @ColumnInfo(name = "stock_location_id") val locationId: String,
    @ColumnInfo(name = "stock") val stockMilli: Long,
)

/** A row holding an NFC tag. */
data class TagHolderRow(
    val id: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
)

/** Settings > Locations: create, rename, move and soft delete. Never touches stock_movement rows. */
@Dao
abstract class LocationDao {
    /** Active locations, by name. */
    @Query("SELECT * FROM location WHERE deleted_at IS NULL ORDER BY name COLLATE NOCASE, id")
    abstract fun observeLocations(): Flow<List<Location>>

    @Query("SELECT * FROM location WHERE deleted_at IS NULL ORDER BY name COLLATE NOCASE, id")
    abstract suspend fun activeLocations(): List<Location>

    /** Articles with stock (sum of movements not 0) per location, all locations. */
    @Query(
        """
        SELECT location_id, COUNT(*) AS article_count FROM (
            SELECT location_id, article_id FROM stock_movement
            GROUP BY location_id, article_id HAVING SUM(quantity) <> 0
        ) GROUP BY location_id
        """,
    )
    abstract fun observeArticleCounts(): Flow<List<LocationArticleCount>>

    /**
     * Active, non-archived articles with a non-zero stock at each of [ids] (one row per article and location).
     * The contents of a location: see viewmodels/LocationContents.
     */
    @Query(
        """
        SELECT article.*, stock_movement.location_id AS stock_location_id, SUM(stock_movement.quantity) AS stock
        FROM article
        JOIN stock_movement ON stock_movement.article_id = article.id
        WHERE stock_movement.location_id IN (:ids) AND article.deleted_at IS NULL AND article.archived = 0
        GROUP BY stock_movement.location_id, article.id
        HAVING SUM(stock_movement.quantity) <> 0
        ORDER BY article.name COLLATE NOCASE, article.id
        """,
    )
    abstract fun observeArticleStockIn(ids: List<String>): Flow<List<LocationArticleStock>>

    /** Active, non-archived serial units whose cached location is one of [ids], by serial number. */
    @Query("SELECT * FROM item WHERE location_id IN (:ids) AND deleted_at IS NULL AND archived = 0 ORDER BY serial_number COLLATE NOCASE, id")
    abstract fun observeItemsIn(ids: List<String>): Flow<List<Item>>

    @Insert
    protected abstract suspend fun insertRow(location: Location)

    @Update
    protected abstract suspend fun updateRow(location: Location)

    /** Also frees the NFC tag, like [Location.softDeleted]. */
    @Query("UPDATE location SET barcode = NULL, deleted_at = :now, updated_at = :now WHERE id IN (:ids) AND deleted_at IS NULL")
    protected abstract suspend fun softDeleteRows(ids: List<String>, now: Long): Int

    /** Positions (article, unit, location) with a non-zero movement sum inside [ids]. */
    @Query(
        """
        SELECT COUNT(*) FROM (
            SELECT 1 FROM stock_movement WHERE location_id IN (:ids)
            GROUP BY location_id, article_id, item_id HAVING SUM(quantity) <> 0
        )
        """,
    )
    protected abstract suspend fun nonZeroPositions(ids: List<String>): Int

    /** Active serial units whose cached location is in [ids]. */
    @Query("SELECT COUNT(*) FROM item WHERE location_id IN (:ids) AND deleted_at IS NULL")
    protected abstract suspend fun itemsAt(ids: List<String>): Int

    /** True if the location or one of its descendants still holds stock. */
    @Transaction
    open suspend fun subtreeHoldsStock(id: String): Boolean {
        val ids = activeLocations().subtreeIds(id)
        return ids.isNotEmpty() && (nonZeroPositions(ids) > 0 || itemsAt(ids) > 0)
    }

    /** Any row holding the tag, soft-deleted ones included (the unique indexes cover them). */
    @Query("SELECT id, deleted_at FROM location WHERE barcode = :tagId")
    protected abstract suspend fun locationTagHolders(tagId: String): List<TagHolderRow>

    @Query("SELECT id, deleted_at FROM article WHERE barcode = :tagId")
    protected abstract suspend fun articleTagHolders(tagId: String): List<TagHolderRow>

    @Query("UPDATE location SET barcode = NULL, updated_at = :now WHERE id = :id")
    protected abstract suspend fun clearLocationTag(id: String, now: Long)

    @Query("UPDATE article SET barcode = NULL, updated_at = :now WHERE id = :id")
    protected abstract suspend fun clearArticleTag(id: String, now: Long)

    /**
     * Creates a location at the top level or under [parentId], with the NFC tag [barcode] if given.
     * Throws [LocationException] if refused, [TagAlreadyLinkedException] if an active article or location
     * holds the tag (a soft-deleted holder is freed first).
     */
    @Transaction
    open suspend fun createLocation(
        id: String,
        name: String,
        parentId: String?,
        now: Long,
        icon: String? = null,
        color: String? = null,
        barcode: String? = null,
    ): Location {
        val all = activeLocations()
        if (parentId != null && all.none { it.id == parentId }) throw LocationException(LocationError.PARENT_NOT_FOUND)
        validateLocationName(name, all.childrenOf(parentId))?.let { throw LocationException(it) }
        barcode?.let { tagId ->
            val locations = locationTagHolders(tagId)
            val articles = articleTagHolders(tagId)
            if ((locations + articles).any { it.deletedAt == null }) throw TagAlreadyLinkedException(tagId)
            locations.forEach { clearLocationTag(it.id, now) }
            articles.forEach { clearArticleTag(it.id, now) }
        }
        val location = Location(
            id = id, name = name.trim(), parentId = parentId,
            icon = LocationIcons.normalize(icon), color = LocationColors.normalize(color),
            createdAt = now, updatedAt = now, barcode = barcode,
        )
        insertRow(location)
        return location
    }

    /** Renames, moves and/or restyles a location ([parentId] null = top level, null [icon]/[color] = default). Throws [LocationException] if refused. */
    @Transaction
    open suspend fun updateLocation(
        id: String,
        name: String,
        parentId: String?,
        now: Long,
        icon: String? = null,
        color: String? = null,
    ): Location {
        val all = activeLocations()
        val stored = all.firstOrNull { it.id == id } ?: throw LocationException(LocationError.NOT_FOUND)
        if (parentId != null && all.none { it.id == parentId }) throw LocationException(LocationError.PARENT_NOT_FOUND)
        if (all.wouldCreateCycle(id, parentId)) throw LocationException(LocationError.CYCLE)
        validateLocationName(name, all.childrenOf(parentId), editingId = id)?.let { throw LocationException(it) }
        val updated = stored.copy(
            name = name.trim(), parentId = parentId,
            icon = LocationIcons.normalize(icon), color = LocationColors.normalize(color),
            updatedAt = now,
        )
        if (updated != stored) updateRow(updated)
        return updated
    }

    /**
     * Soft-deletes the location and all its descendants. Throws [LocationException] with HAS_STOCK if
     * any of them still holds stock (movement sum not 0, or a unit located there). Returns the number deleted.
     */
    @Transaction
    open suspend fun softDeleteLocation(id: String, now: Long): Int {
        val ids = activeLocations().subtreeIds(id)
        if (ids.isEmpty()) throw LocationException(LocationError.NOT_FOUND)
        if (nonZeroPositions(ids) > 0 || itemsAt(ids) > 0) throw LocationException(LocationError.HAS_STOCK)
        return softDeleteRows(ids, now)
    }
}
