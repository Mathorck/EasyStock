package ch.mathorck.easystock.data.model.article

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * SHA-256 of an article photo file, as the server knows it (v10). Written when a photo is pushed or
 * pulled: the server stores photos by hash (`article.image_sha256`), the phone by path. A row whose
 * file is missing is a photo still to download.
 */
@Entity(tableName = "image_hash", indices = [Index(value = ["sha256"])])
data class ImageHash(
    /** Same value as `article.image_path` (`images/<uuid>.jpg`). */
    @PrimaryKey @ColumnInfo(name = "image_path") val imagePath: String,
    /** Lowercase hex. */
    val sha256: String,
)
