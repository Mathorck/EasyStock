package ch.mathorck.easystock.data.model.attributes

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ch.mathorck.easystock.data.model.categories.Category
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(
    tableName = "category_attribute",
    foreignKeys = [
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["category_id"]),
        ForeignKey(entity = AttributeDefinition::class, parentColumns = ["id"], childColumns = ["attribute_id"]),
    ],
    indices = [
        Index(value = ["category_id", "attribute_id"], unique = true),
        Index(value = ["attribute_id"]),
        Index(value = ["updated_at"]),
    ],
)
@Serializable
data class CategoryAttribute(
    @PrimaryKey val id: String,
    @SerialName("category_id") @ColumnInfo(name = "category_id") val categoryId: String,
    @SerialName("attribute_id") @ColumnInfo(name = "attribute_id") val attributeId: String,
    @ColumnInfo(defaultValue = "0") val required: Boolean = false,
    @SerialName("sort_order") @ColumnInfo(name = "sort_order", defaultValue = "0") val sortOrder: Int = 0,
    @SerialName("created_at") @ColumnInfo(name = "created_at") val createdAt: Long,
    @SerialName("updated_at") @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @SerialName("deleted_at") @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)
