package ch.mathorck.easystock.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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

    /**
     * Renames the active category. Changing only the case of its own name is allowed.
     * Throws [CategoryNotFoundException], [DuplicateCategoryNameException] or [DeletedCategoryNameException].
     */
    @Transaction
    open suspend fun rename(id: String, name: String, now: Long) {
        checkNameFree(name, id)
        if (renameRow(id, name, now) == 0) throw CategoryNotFoundException(id)
    }
}
