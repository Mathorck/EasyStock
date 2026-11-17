package ch.mathorck.easystock.enums.stock

/** Stock level filter of the Stock page. One at a time: an article out of stock with a threshold is also low. */
enum class StockLevelFilter {
    /** At or below its threshold, see [ArticleStock.isLowStock]. Never a serial article. */
    LOW,

    /**
     * Stock (at the filtered location, or in total) at or below 0. With a location, only articles that had
     * a movement there are listed, so this means "used up here", not "never stored here".
     */
    OUT,
}
