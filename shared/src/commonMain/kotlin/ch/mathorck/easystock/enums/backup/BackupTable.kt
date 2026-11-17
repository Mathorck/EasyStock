package ch.mathorck.easystock.enums.backup

import ch.mathorck.easystock.services.backup.TABLES_DIR

/** Table names, also the JSON file names in [TABLES_DIR]. Parent tables first (foreign keys). */
enum class BackupTable(val tableName: String) {
    CATEGORY("category"),
    ATTRIBUTE_DEFINITION("attribute_definition"),
    CATEGORY_ATTRIBUTE("category_attribute"),
    LOCATION("location"),
    ARTICLE("article"),
    ARTICLE_FLAG("article_flag"),
    ITEM("item"),
    STOCK_MOVEMENT("stock_movement"),
    STOCK_REVIEW("stock_review"),
    STOCK_REVIEW_LINE("stock_review_line"),
    ;

    val entryName: String get() = "$TABLES_DIR/$tableName.json"
}
