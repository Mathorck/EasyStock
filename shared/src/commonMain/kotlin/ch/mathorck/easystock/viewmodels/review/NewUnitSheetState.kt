package ch.mathorck.easystock.viewmodels.review

import ch.mathorck.easystock.viewmodels.article.StockActionUiState

/** The "new unit found" form: the unit properties of the article, at a fixed location of the review. */
data class NewUnitSheetState(
    val articleName: String,
    val unit: String,
    val locationPath: String,
    val ui: StockActionUiState,
)
