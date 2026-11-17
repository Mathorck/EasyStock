package ch.mathorck.easystock.services.sync

/**
 * Conflict codes of a "changed" answer (03-server-spec.md, "Conflict rules"; server SyncPushConflictHelper).
 * The server fixes the row (a new name, a cleared tag...) and the phone takes its version without a
 * message, except [MOVEMENT_KEPT] (the user chooses) and [CATEGORY_ATTRIBUTE_SKIPPED] (the link is dropped).
 */
object ConflictCodes {
    const val CATEGORY_RENAMED = "CATEGORY_RENAMED"
    const val SKU_RENAMED = "SKU_RENAMED"
    const val BARCODE_CLEARED = "BARCODE_CLEARED"
    const val SERIAL_RENAMED = "SERIAL_RENAMED"
    const val ATTRIBUTE_MERGED = "ATTRIBUTE_MERGED"
    const val ATTRIBUTE_KEY_KEPT = "ATTRIBUTE_KEY_KEPT"
    const val CATEGORY_ATTRIBUTE_SKIPPED = "CATEGORY_ATTRIBUTE_SKIPPED"
    const val LOCATION_PARENT_KEPT = "LOCATION_PARENT_KEPT"
    const val MOVEMENT_KEPT = "MOVEMENT_KEPT"

    val ALL = listOf(
        CATEGORY_RENAMED, SKU_RENAMED, BARCODE_CLEARED, SERIAL_RENAMED, ATTRIBUTE_MERGED, ATTRIBUTE_KEY_KEPT,
        CATEGORY_ATTRIBUTE_SKIPPED, LOCATION_PARENT_KEPT, MOVEMENT_KEPT,
    )
}
