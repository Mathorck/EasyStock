package ch.mathorck.easystock.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Category
import kotlinx.coroutines.flow.Flow

/** The SKU is already used by an article, deleted ones included (the unique index covers them). */
class DuplicateSkuException(val sku: String) : IllegalStateException("SKU already used: $sku")

/** A deleted category still holds this name (the unique index covers it). */
class DeletedCategoryNameException(val name: String) : IllegalStateException("Name of a deleted category: $name")

/** Creates articles from the article form. Editing is in [ArticleDetailDao.updateArticle]. */
@Dao
abstract class ArticleFormDao {
    /** SKUs of all articles, soft-deleted ones included (the unique index covers them). */
    @Query("SELECT id, sku FROM article WHERE sku IS NOT NULL")
    abstract fun observeSkus(): Flow<List<SkuOwner>>

    @Query("SELECT COUNT(*) FROM category")
    abstract suspend fun countCategories(): Int

    @Query("SELECT COUNT(*) FROM article")
    abstract suspend fun countArticles(): Int

    /** Ignores case. Includes soft-deleted articles: they keep their SKU and the unique index. */
    @Query("SELECT COUNT(*) FROM article WHERE sku = :sku COLLATE NOCASE")
    protected abstract suspend fun countArticlesWithSku(sku: String): Int

    /** Ignores case. Includes soft-deleted categories: they keep their name and the unique index. */
    @Query("SELECT * FROM category WHERE name = :name COLLATE NOCASE LIMIT 1")
    protected abstract suspend fun findCategoryByName(name: String): Category?

    /** Any row holding the tag, soft-deleted ones included (the unique index covers them). */
    @Query("SELECT * FROM article WHERE barcode = :tagId LIMIT 1")
    protected abstract suspend fun findTagHolder(tagId: String): Article?

    @Query("UPDATE article SET barcode = NULL, updated_at = :now WHERE id = :id")
    protected abstract suspend fun clearBarcode(id: String, now: Long)

    @Insert
    protected abstract suspend fun insertCategory(category: Category)

    @Insert
    protected abstract suspend fun insertArticle(article: Article)

    /**
     * Inserts [article] in one transaction and returns the stored row.
     * [newCategory] is created first and replaces [Article.categoryId], unless an active category
     * already has its name (ignoring case): that one is reused.
     * An NFC tag ([Article.barcode]) still held by a soft-deleted row is freed first.
     * Throws [DuplicateSkuException], [DeletedCategoryNameException] or [TagAlreadyLinkedException]
     * (an active article holds the tag); nothing is saved then.
     */
    @Transaction
    open suspend fun createArticle(article: Article, newCategory: Category?): Article {
        article.sku?.let { if (countArticlesWithSku(it) > 0) throw DuplicateSkuException(it) }
        article.barcode?.let { tagId ->
            val holder = findTagHolder(tagId)
            if (holder != null) {
                if (holder.deletedAt == null) throw TagAlreadyLinkedException(tagId)
                clearBarcode(holder.id, article.createdAt)
            }
        }
        var categoryId = article.categoryId
        newCategory?.let { category ->
            val existing = findCategoryByName(category.name)
            categoryId = when {
                existing == null -> category.id.also { insertCategory(category) }
                existing.deletedAt != null -> throw DeletedCategoryNameException(category.name)
                else -> existing.id
            }
        }
        val stored = article.copy(categoryId = categoryId)
        insertArticle(stored)
        return stored
    }
}
