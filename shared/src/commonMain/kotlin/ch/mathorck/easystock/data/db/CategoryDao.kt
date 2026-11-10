package ch.mathorck.easystock.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ch.mathorck.easystock.data.model.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: Category)

    @Query("SELECT * FROM category WHERE deleted_at IS NULL ORDER BY name")
    fun observeAll(): Flow<List<Category>>
}
