package ch.mathorck.easystock.enums.review

// Pure rules of a stock review, shared by ReviewDao and the UI. No database access here.

/** What the checklist shows for a line. The info flag is separate ([StockReviewLine.infoFlagged]). */
enum class LineStatus { UNCHECKED, OK, DISCREPANCY }
