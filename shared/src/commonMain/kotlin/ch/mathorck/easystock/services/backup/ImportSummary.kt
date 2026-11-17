package ch.mathorck.easystock.services.backup

import ch.mathorck.easystock.enums.backup.BackupTable
import ch.mathorck.easystock.enums.backup.ImportMode

/** What an import changed. Counts per table; photos copied from the backup. */
data class ImportSummary(
    val mode: ImportMode,
    val added: Map<BackupTable, Int>,
    val updated: Map<BackupTable, Int>,
    val photos: Int,
    val conflicts: List<MergeConflict>,
) {
    val totalAdded: Int get() = added.values.sum()
    val totalUpdated: Int get() = updated.values.sum()
}
