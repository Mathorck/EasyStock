package ch.mathorck.easystock.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.StockMovement
import ch.mathorck.easystock.data.model.Tracking

/** Reasons allowed for an incoming stock entry. */
val STOCK_ENTRY_REASONS = listOf(MovementReason.PURCHASE, MovementReason.RETURN, MovementReason.INVENTORY)

/**
 * One incoming stock entry. [newCategory], [newArticle] and [newLocation] are created with the movement.
 * When [newArticle] is set, [movement] must point to it (same for [newLocation]).
 */
data class NewStockEntry(
    val newCategory: Category? = null,
    val newArticle: Article? = null,
    val newLocation: Location? = null,
    val movement: StockMovement,
)

/** The SKU is already used by an article, deleted ones included (the unique index covers them). */
class DuplicateSkuException(val sku: String) : IllegalStateException("SKU already used: $sku")

/** A deleted category still holds this name (the unique index covers it). */
class DeletedCategoryNameException(val name: String) : IllegalStateException("Name of a deleted category: $name")

/** Saves an incoming stock entry and the rows it creates, all or nothing. */
@Dao
abstract class StockEntryDao {
    @Insert
    protected abstract suspend fun insertCategory(category: Category)

    @Insert
    protected abstract suspend fun insertArticle(article: Article)

    @Insert
    protected abstract suspend fun insertLocation(location: Location)

    /** Append-only, never updated or deleted. */
    @Insert
    protected abstract suspend fun insertMovement(movement: StockMovement)

    /** Ignores case. Includes soft-deleted articles: they keep their SKU and the unique index. */
    @Query("SELECT COUNT(*) FROM article WHERE sku = :sku COLLATE NOCASE")
    abstract suspend fun countArticlesWithSku(sku: String): Int

    /** Ignores case. Includes soft-deleted categories: they keep their name and the unique index. */
    @Query("SELECT * FROM category WHERE name = :name COLLATE NOCASE LIMIT 1")
    protected abstract suspend fun findCategoryByName(name: String): Category?

    @Query("SELECT COUNT(*) FROM category")
    abstract suspend fun countCategories(): Int

    @Query("SELECT COUNT(*) FROM location")
    abstract suspend fun countLocations(): Int

    @Query("SELECT COUNT(*) FROM stock_movement")
    abstract suspend fun countMovements(): Int

    /**
     * Inserts the new category, location and article, then the movement, in one transaction.
     * A category with the same name that is still active is reused instead of created.
     * Throws [DuplicateSkuException] or [DeletedCategoryNameException]; nothing is saved then.
     */
    @Transaction
    open suspend fun save(entry: NewStockEntry) {
        val movement = entry.movement
        require(movement.quantityMilli > 0) { "A stock entry must be positive" }
        require(movement.reason in STOCK_ENTRY_REASONS) { "Not an entry reason: ${movement.reason}" }
        require(movement.itemId == null) { "Serial items are not supported" }
        entry.newArticle?.let {
            require(it.id == movement.articleId) { "Movement does not point to the new article" }
            require(it.tracking == Tracking.QUANTITY) { "Only quantity-tracked articles are supported" }
        }
        entry.newLocation?.let { require(it.id == movement.locationId) { "Movement does not point to the new location" } }

        var article = entry.newArticle
        entry.newCategory?.let { category ->
            val existing = findCategoryByName(category.name)
            when {
                existing == null -> insertCategory(category)
                existing.deletedAt != null -> throw DeletedCategoryNameException(category.name)
                else -> article = article?.copy(categoryId = existing.id)
            }
        }
        entry.newLocation?.let { insertLocation(it) }
        article?.let {
            it.sku?.let { sku -> if (countArticlesWithSku(sku) > 0) throw DuplicateSkuException(sku) }
            insertArticle(it)
        }
        insertMovement(movement)
    }
}
