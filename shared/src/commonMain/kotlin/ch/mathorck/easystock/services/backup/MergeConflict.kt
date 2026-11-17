package ch.mathorck.easystock.services.backup

import ch.mathorck.easystock.enums.backup.BackupTable
import ch.mathorck.easystock.enums.backup.ConflictType

/** One conflict resolved during a merge. [value]: what was imported, [resolved]: what was saved instead. */
data class MergeConflict(
    val type: ConflictType,
    val table: BackupTable,
    val rowId: String,
    val value: String? = null,
    val resolved: String? = null,
)
