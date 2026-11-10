package ch.mathorck.easystock.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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

    @Insert
    protected abstract suspend fun insertRow(location: Location)

    @Update
    protected abstract suspend fun updateRow(location: Location)

    @Query("UPDATE location SET deleted_at = :now, updated_at = :now WHERE id IN (:ids) AND deleted_at IS NULL")
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

    /** Creates a location at the top level or under [parentId]. Throws [LocationException] if refused. */
    @Transaction
    open suspend fun createLocation(
        id: String,
        name: String,
        parentId: String?,
        now: Long,
        icon: String? = null,
        color: String? = null,
    ): Location {
        val all = activeLocations()
        if (parentId != null && all.none { it.id == parentId }) throw LocationException(LocationError.PARENT_NOT_FOUND)
        validateLocationName(name, all.childrenOf(parentId))?.let { throw LocationException(it) }
        val location = Location(
            id = id, name = name.trim(), parentId = parentId,
            icon = LocationIcons.normalize(icon), color = LocationColors.normalize(color),
            createdAt = now, updatedAt = now,
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
