package ch.mathorck.easystock.services.backup

import ch.mathorck.easystock.data.dto.backup.BackupData

/**
 * What a merge writes. [inserts] and [updates] hold whole rows (updates never contain movements).
 * [itemLocationFixes]: other local items whose cached location changes (movements are the source of truth).
 * [result]: the database content after the merge.
 */
data class MergePlan(
    val inserts: BackupData,
    val updates: BackupData,
    val itemLocationFixes: Map<String, String?>,
    val conflicts: List<MergeConflict>,
    val result: BackupData,
    /** Photos referenced by inserted or updated articles: copy them from the archive if missing here. */
    val photosNeeded: Set<String>,
    /** Local photos no longer referenced by any article: delete them once the merge is saved. */
    val photosReleased: Set<String>,
) {
    val isEmpty: Boolean get() = inserts.isEmpty && updates.isEmpty && itemLocationFixes.isEmpty()
}
