package ch.mathorck.easystock.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.AttributeDefinition
import ch.mathorck.easystock.data.model.Category
import kotlinx.coroutines.flow.Flow

/** An active category already has this name (ignoring case). */
class DuplicateCategoryNameException(val name: String) : IllegalStateException("Category name already used: $name")

/** The category was deleted (or never existed). */
class CategoryNotFoundException(val id: String) : IllegalStateException("No active category: $id")

@Dao
abstract class CategoryDao {
    @Insert
    abstract suspend fun insert(category: Category)

    @Query("SELECT * FROM category WHERE deleted_at IS NULL ORDER BY name")
    abstract fun observeAll(): Flow<List<Category>>

    /** Ignores case. Includes soft-deleted categories: they keep their name and the unique index. */
    @Query("SELECT * FROM category WHERE name = :name COLLATE NOCASE AND id != :exceptId LIMIT 1")
    protected abstract suspend fun findOtherByName(name: String, exceptId: String): Category?

    @Query("UPDATE category SET name = :name, updated_at = :now WHERE id = :id AND deleted_at IS NULL")
    protected abstract suspend fun renameRow(id: String, name: String, now: Long): Int

    @Query("UPDATE category SET deleted_at = :now, updated_at = :now WHERE id = :id AND deleted_at IS NULL")
    abstract suspend fun softDelete(id: String, now: Long)

    private suspend fun checkNameFree(name: String, exceptId: String) {
        val other = findOtherByName(name, exceptId) ?: return
        if (other.deletedAt != null) throw DeletedCategoryNameException(name)
        throw DuplicateCategoryNameException(name)
    }

    /** Inserts [category]. Throws [DuplicateCategoryNameException] or [DeletedCategoryNameException]. */
    @Transaction
    open suspend fun create(category: Category) {
        checkNameFree(category.name, category.id)
        insert(category)
    }

    @Query("SELECT * FROM category WHERE id = :id AND deleted_at IS NULL")
    protected abstract suspend fun getActive(id: String): Category?

    /** Active attribute definitions linked to the category (same set as AttributeDao.getCategoryAttributes). */
    @Query(
        """
        SELECT attribute_definition.* FROM attribute_definition
        JOIN category_attribute ON category_attribute.attribute_id = attribute_definition.id
        WHERE category_attribute.category_id = :categoryId
          AND category_attribute.deleted_at IS NULL
          AND attribute_definition.deleted_at IS NULL
        """,
    )
    protected abstract suspend fun activeDefinitions(categoryId: String): List<AttributeDefinition>

    @Query("SELECT * FROM article WHERE category_id = :categoryId AND deleted_at IS NULL AND name_auto = 1")
    protected abstract suspend fun autoNamedArticles(categoryId: String): List<Article>

    @Query("UPDATE article SET name = :name, updated_at = :now WHERE id = :id")
    protected abstract suspend fun renameArticleRow(id: String, name: String, now: Long)

    /**
     * Renames the active category. Changing only the case of its own name is allowed.
     * Its articles that follow its name template (`name_auto`) are renamed in the same transaction, since
     * the template may use {category} ([autoNameChanges]); typed names are kept. Returns how many were renamed.
     * Throws [CategoryNotFoundException], [DuplicateCategoryNameException] or [DeletedCategoryNameException].
     */
    @Transaction
    open suspend fun rename(id: String, name: String, now: Long): Int {
        checkNameFree(name, id)
        if (renameRow(id, name, now) == 0) throw CategoryNotFoundException(id)
        val category = getActive(id) ?: return 0
        if (category.nameTemplate == null) return 0
        val changes = autoNameChanges(category, activeDefinitions(id), autoNamedArticles(id))
        changes.forEach { (articleId, articleName) -> renameArticleRow(articleId, articleName, now) }
        return changes.size
    }
}
