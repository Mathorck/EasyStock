package ch.mathorck.easystock.enums.sync

/** Why "Keep mine" is not offered. */
enum class KeepMineBlock {
    /** The role can't add stock movements (Viewer, Guest). */
    ROLE,

    /** A serial unit's movement: sent again, the unit could be in two places at once. */
    SERIAL_UNIT,

    /** One half of a transfer: sent again alone, the stock would not add up. */
    TRANSFER,

    /** The article is deleted. */
    ARTICLE_GONE,

    /** An entry to a deleted location. */
    LOCATION_GONE,

    /** A removal larger than the stock at the location now. */
    NOT_ENOUGH_STOCK,

    /** A reason that doesn't match the direction (never made by this app). */
    INVALID,
}
