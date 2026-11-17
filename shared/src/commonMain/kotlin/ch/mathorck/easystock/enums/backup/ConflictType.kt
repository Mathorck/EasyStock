package ch.mathorck.easystock.enums.backup

enum class ConflictType {
    /** Another category already has this name: the imported one was renamed. */
    CATEGORY_RENAMED,

    /** Another article already has this SKU: the imported one got a suffix. */
    SKU_RENAMED,

    /** The NFC tag is already on another article or location here: removed from the imported row (one tag = one thing). */
    BARCODE_CLEARED,

    /** Another unit already has this serial number: the imported one got a suffix. */
    SERIAL_RENAMED,

    /** An attribute with the same key exists: both are the same attribute, the newer label/options are kept. */
    ATTRIBUTE_MERGED,

    /** The imported attribute has another key for the same id: the key can't change, the local one is kept. */
    ATTRIBUTE_KEY_KEPT,

    /** The category already has this attribute: the imported link was skipped. */
    CATEGORY_ATTRIBUTE_SKIPPED,

    /** The imported parent would make a location its own ancestor: the local parent is kept. */
    LOCATION_PARENT_KEPT,

    /** A movement with the same id differs: movements are never changed, the local one is kept. */
    MOVEMENT_KEPT,
}
