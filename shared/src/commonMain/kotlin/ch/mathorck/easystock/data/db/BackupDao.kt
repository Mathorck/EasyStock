package ch.mathorck.easystock.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import ch.mathorck.easystock.backup.BackupData
import ch.mathorck.easystock.backup.MergePlan
import ch.mathorck.easystock.backup.parentsFirst
import ch.mathorck.easystock.backup.planMerge
import ch.mathorck.easystock.backup.withItemLocations
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.AttributeDefinition
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.CategoryAttribute
import ch.mathorck.easystock.data.model.Item
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.StockMovement
import ch.mathorck.easystock.data.model.StockReview
import ch.mathorck.easystock.data.model.StockReviewLine

/**
 * Whole-database reads and writes for backups. Every row, soft-deleted ones included.
 * The incoming data must be valid (backup.validateBackup): the writes rely on it, each method is one
 * transaction and rolls back entirely on any error.
 */
@Dao
abstract class BackupDao {
    @Query("SELECT * FROM category ORDER BY id") protected abstract suspend fun categories(): List<Category>
    @Query("SELECT * FROM attribute_definition ORDER BY id") protected abstract suspend fun attributeDefinitions(): List<AttributeDefinition>
    @Query("SELECT * FROM category_attribute ORDER BY id") protected abstract suspend fun categoryAttributes(): List<CategoryAttribute>
    @Query("SELECT * FROM location ORDER BY id") protected abstract suspend fun locations(): List<Location>
    @Query("SELECT * FROM article ORDER BY id") protected abstract suspend fun articles(): List<Article>
    @Query("SELECT * FROM item ORDER BY id") protected abstract suspend fun items(): List<Item>
    @Query("SELECT * FROM stock_movement ORDER BY created_at, id") protected abstract suspend fun movements(): List<StockMovement>
    @Query("SELECT * FROM stock_review ORDER BY id") protected abstract suspend fun reviews(): List<StockReview>
    @Query("SELECT * FROM stock_review_line ORDER BY id") protected abstract suspend fun reviewLines(): List<StockReviewLine>

    @Insert protected abstract suspend fun insertCategories(rows: List<Category>)
    @Insert protected abstract suspend fun insertAttributeDefinitions(rows: List<AttributeDefinition>)
    @Insert protected abstract suspend fun insertCategoryAttributes(rows: List<CategoryAttribute>)
    @Insert protected abstract suspend fun insertLocations(rows: List<Location>)
    @Insert protected abstract suspend fun insertArticles(rows: List<Article>)
    @Insert protected abstract suspend fun insertItems(rows: List<Item>)
    @Insert protected abstract suspend fun insertMovements(rows: List<StockMovement>)
    @Insert protected abstract suspend fun insertReviews(rows: List<StockReview>)
    @Insert protected abstract suspend fun insertReviewLines(rows: List<StockReviewLine>)

    @Update protected abstract suspend fun updateCategories(rows: List<Category>)
    @Update protected abstract suspend fun updateAttributeDefinitions(rows: List<AttributeDefinition>)
    @Update protected abstract suspend fun updateCategoryAttributes(rows: List<CategoryAttribute>)
    @Update protected abstract suspend fun updateLocations(rows: List<Location>)
    @Update protected abstract suspend fun updateArticles(rows: List<Article>)
    @Update protected abstract suspend fun updateItems(rows: List<Item>)
    @Update protected abstract suspend fun updateReviews(rows: List<StockReview>)
    @Update protected abstract suspend fun updateReviewLines(rows: List<StockReviewLine>)

    // Children first (foreign keys). A replace is the only place rows are hard-deleted:
    // the user explicitly asked to wipe this phone and restore a backup.
    @Query("DELETE FROM stock_review_line") protected abstract suspend fun deleteReviewLines()
    @Query("DELETE FROM stock_review") protected abstract suspend fun deleteReviews()
    @Query("DELETE FROM stock_movement") protected abstract suspend fun deleteMovements()
    @Query("DELETE FROM item") protected abstract suspend fun deleteItems()
    @Query("DELETE FROM article") protected abstract suspend fun deleteArticles()
    @Query("DELETE FROM category_attribute") protected abstract suspend fun deleteCategoryAttributes()
    @Query("DELETE FROM attribute_definition") protected abstract suspend fun deleteAttributeDefinitions()
    @Query("DELETE FROM category") protected abstract suspend fun deleteCategories()
    @Query("DELETE FROM location") protected abstract suspend fun deleteLocations()

    // Server mode tables, app only (never in a backup)
    @Query("DELETE FROM sync_state") protected abstract suspend fun deleteSyncState()
    @Query("DELETE FROM outbox") protected abstract suspend fun deleteOutbox()
    @Query("DELETE FROM server_user") protected abstract suspend fun deleteServerUsers()
    @Query("DELETE FROM low_stock_alert") protected abstract suspend fun deleteLowStockAlerts()
    @Query("DELETE FROM rejected_change") protected abstract suspend fun deleteRejectedChanges()
    @Query("DELETE FROM image_hash") protected abstract suspend fun deleteImageHashes()
    @Query("DELETE FROM pending_push") protected abstract suspend fun deletePendingPush()
    @Query("UPDATE sync_state SET applying = :applying") protected abstract suspend fun setApplying(applying: Boolean)
    @Query("SELECT EXISTS(SELECT 1 FROM sync_state)") abstract suspend fun isConnected(): Boolean

    // Frees unique values before the updates, so two rows can swap a value (name, SKU, tag, serial)
    @Query("UPDATE category SET name = :name WHERE id = :id") protected abstract suspend fun renameCategory(id: String, name: String)
    @Query("UPDATE article SET sku = NULL, barcode = NULL WHERE id = :id") protected abstract suspend fun clearArticleUniques(id: String)
    @Query("UPDATE location SET barcode = NULL WHERE id = :id") protected abstract suspend fun clearLocationTag(id: String)
    @Query("UPDATE item SET serial_number = NULL WHERE id = :id") protected abstract suspend fun clearSerial(id: String)

    /** Cache only, not an edit: `updated_at` is unchanged. */
    @Query("UPDATE item SET location_id = :locationId WHERE id = :id") protected abstract suspend fun setItemLocation(id: String, locationId: String?)

    /** Every row of every table, in one consistent read. */
    @Transaction
    open suspend fun snapshot(): BackupData = BackupData(
        categories = categories(),
        attributeDefinitions = attributeDefinitions(),
        categoryAttributes = categoryAttributes(),
        locations = locations(),
        articles = articles(),
        items = items(),
        movements = movements(),
        reviews = reviews(),
        reviewLines = reviewLines(),
    )

    /**
     * Hard-deletes every row of every synced table, children first (foreign keys).
     * Only for a full wipe the user asked for (backup restore, reset of all data): the soft-delete
     * and append-only `stock_movement` rules don't apply, nothing is left to sync.
     * The server mode tables are kept (a backup replace doesn't touch them), see [wipeEverything].
     */
    @Transaction
    open suspend fun wipeAll() {
        deleteReviewLines()
        deleteReviews()
        deleteMovements()
        deleteItems()
        deleteArticles()
        deleteCategoryAttributes()
        deleteAttributeDefinitions()
        deleteCategories()
        deleteLocations()
    }

    /**
     * [wipeAll] plus the server mode tables (`sync_state`, `outbox`, `server_user`, `low_stock_alert`,
     * `rejected_change`, `image_hash`, `pending_push`): the phone is as after a fresh install. For the reset of all data.
     */
    @Transaction
    open suspend fun wipeEverything() {
        // sync_state first: with it gone the outbox triggers are off (they don't fire on DELETE anyway)
        deleteSyncState()
        deleteOutbox()
        deleteServerUsers()
        deleteLowStockAlerts()
        deleteRejectedChanges()
        deleteImageHashes()
        deletePendingPush()
        wipeAll()
    }

    /**
     * Wipes every synced table and writes [data]. `item.location_id` is recomputed from the movements.
     * Refused while connected to a server: it would hard-delete synced rows (the screen offers only Merge then).
     */
    @Transaction
    open suspend fun replaceAll(data: BackupData) {
        check(!isConnected()) { "A backup replace is not allowed while connected to a server" }
        wipeAll()
        val rows = data.withItemLocations()
        insertCategories(rows.categories)
        insertAttributeDefinitions(rows.attributeDefinitions)
        insertCategoryAttributes(rows.categoryAttributes)
        insertLocations(parentsFirst(rows.locations))
        insertArticles(rows.articles)
        insertItems(rows.items)
        insertMovements(rows.movements)
        insertReviews(rows.reviews)
        insertReviewLines(rows.reviewLines)
    }

    /** Reads the local rows, plans the merge of [incoming] and writes it, in one transaction. */
    @Transaction
    open suspend fun merge(incoming: BackupData): MergePlan {
        val plan = planMerge(snapshot(), incoming)
        apply(plan)
        return plan
    }

    private suspend fun apply(plan: MergePlan) {
        val updates = plan.updates
        updates.categories.forEach { renameCategory(it.id, "\u0000merge:${it.id}") }
        updates.articles.forEach { clearArticleUniques(it.id) }
        updates.locations.forEach { clearLocationTag(it.id) }
        updates.items.forEach { clearSerial(it.id) }

        // Parents first; inserts before updates (an update may point to a new row)
        insertCategories(plan.inserts.categories)
        updateCategories(updates.categories)
        insertAttributeDefinitions(plan.inserts.attributeDefinitions)
        updateAttributeDefinitions(updates.attributeDefinitions)
        insertCategoryAttributes(plan.inserts.categoryAttributes)
        updateCategoryAttributes(updates.categoryAttributes)
        insertLocations(plan.inserts.locations)
        updateLocations(updates.locations)
        insertArticles(plan.inserts.articles)
        updateArticles(updates.articles)
        insertItems(plan.inserts.items)
        updateItems(updates.items)
        // Append-only
        insertMovements(plan.inserts.movements)
        insertReviews(plan.inserts.reviews)
        updateReviews(updates.reviews)
        insertReviewLines(plan.inserts.reviewLines)
        updateReviewLines(updates.reviewLines)
        // A cache recomputed from the movements, never sent: every phone recomputes it. With
        // `applying` on the outbox triggers skip these updates (no-op in standalone: no sync_state row)
        setApplying(true)
        plan.itemLocationFixes.forEach { (id, location) -> setItemLocation(id, location) }
        setApplying(false)
    }
}
