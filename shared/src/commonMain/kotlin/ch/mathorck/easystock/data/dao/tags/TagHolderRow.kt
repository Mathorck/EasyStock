package ch.mathorck.easystock.data.dao.tags

import androidx.room.ColumnInfo

/** A row holding an NFC tag. */
data class TagHolderRow(
    val id: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
)
