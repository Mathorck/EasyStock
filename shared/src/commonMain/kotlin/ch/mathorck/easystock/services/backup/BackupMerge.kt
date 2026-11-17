package ch.mathorck.easystock.services.backup

import ch.mathorck.easystock.data.dto.backup.BackupData
import ch.mathorck.easystock.data.model.stock.StockMovement

/** Max lengths of the columns that may get a " (2)" suffix (docs/schema.dbml). */
const val MAX_CATEGORY_NAME_LENGTH = 100

const val MAX_SKU_LENGTH = 50

/**
 * Plans the merge of [incoming] into [local] (every row of both, soft-deleted ones included), by id:
 * - a row on one side only is added;
 * - a row on both sides: the newer `updated_at` wins (a soft delete is an update), the local one on a tie;
 * - `stock_movement` is append-only: missing ids are added, existing ones never changed;
 * - unique columns are resolved deterministically (values of replaced local rows released first, then rows
 *   sorted by id) and reported in [MergePlan.conflicts];
 * - `item.location_id` is recomputed from the merged movements;
 * - stock reviews and their lines merge like the other rows (newer wins). A finished review never adds
 *   movements here: its corrections are movements already, merged with the others.
 * [incoming] must be valid ([validateBackup]). Pure: nothing is written.
 */
fun planMerge(local: BackupData, incoming: BackupData): MergePlan = Merger(local, incoming).plan()

/**
 * The movement without `created_by` and `device_id`: the server sets them after the push, and an import
 * clears them ([withoutServerAuthors]). The same movement with or without its author is not a conflict.
 */
internal fun StockMovement.withoutAuthor(): StockMovement =
    if (createdBy == null && deviceId == null) this else copy(createdBy = null, deviceId = null)

internal fun <T> Sequence<T>.takeWhileDistinct(): Sequence<T> {
    val seen = HashSet<T>()
    return takeWhile { seen.add(it) }
}
