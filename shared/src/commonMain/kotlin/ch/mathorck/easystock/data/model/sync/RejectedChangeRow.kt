package ch.mathorck.easystock.data.model.sync

import androidx.room.Embedded

/** A [RejectedChange] with the name of its local row, null when the row is gone. Not a table. */
data class RejectedChangeRow(
    @Embedded val change: RejectedChange,
    val label: String?,
)
