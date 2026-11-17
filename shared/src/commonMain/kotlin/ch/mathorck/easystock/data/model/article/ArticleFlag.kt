package ch.mathorck.easystock.data.model.article

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A note raised on an article ("something is wrong"). Open while [resolvedAt] and [deletedAt] are null.
 * [createdBy] is set by the server (never sent by the phone, never changed by a local edit): it is stored
 * from the pulled row. Null in standalone mode.
 */
@Entity(
    tableName = "article_flag",
    foreignKeys = [
        ForeignKey(entity = Article::class, parentColumns = ["id"], childColumns = ["article_id"]),
    ],
    indices = [
        Index(value = ["article_id"]),
        Index(value = ["updated_at"]),
    ],
)
@Serializable
data class ArticleFlag(
    @PrimaryKey val id: String,
    @SerialName("article_id") @ColumnInfo(name = "article_id") val articleId: String,
    val note: String,
    @SerialName("created_at") @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Server mode: the user who raised it, set by the server. Null in standalone mode. */
    @SerialName("created_by") @ColumnInfo(name = "created_by") val createdBy: String? = null,
    /** Null = open. */
    @SerialName("resolved_at") @ColumnInfo(name = "resolved_at") val resolvedAt: Long? = null,
    @SerialName("updated_at") @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @SerialName("deleted_at") @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)
