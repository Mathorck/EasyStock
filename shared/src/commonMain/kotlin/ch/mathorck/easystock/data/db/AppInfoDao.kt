package ch.mathorck.easystock.data.db

import androidx.room.Dao
import androidx.room.Query

/** Cheap COUNT queries for the app info screen. Soft-deleted rows are excluded. */
@Dao
interface AppInfoDao {
    @Query("SELECT COUNT(*) FROM article WHERE deleted_at IS NULL")
    suspend fun articles(): Int

    @Query("SELECT COUNT(*) FROM location WHERE deleted_at IS NULL")
    suspend fun locations(): Int

    @Query("SELECT COUNT(*) FROM item WHERE deleted_at IS NULL")
    suspend fun items(): Int

    /** Append-only table, no soft delete: all rows. */
    @Query("SELECT COUNT(*) FROM stock_movement")
    suspend fun stockMovements(): Int

    @Query(
        "SELECT (SELECT COUNT(*) FROM article WHERE deleted_at IS NULL AND barcode IS NOT NULL) + " +
            "(SELECT COUNT(*) FROM location WHERE deleted_at IS NULL AND barcode IS NOT NULL)",
    )
    suspend fun nfcTags(): Int

    @Query("SELECT COUNT(*) FROM stock_review WHERE deleted_at IS NULL AND status = 'open'")
    suspend fun openReviews(): Int
}
