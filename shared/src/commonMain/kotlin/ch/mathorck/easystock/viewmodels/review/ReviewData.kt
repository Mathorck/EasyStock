package ch.mathorck.easystock.viewmodels.review

import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.data.model.article.Item
import ch.mathorck.easystock.data.model.location.Location
import ch.mathorck.easystock.data.model.review.StockReview
import ch.mathorck.easystock.data.model.review.StockReviewLine

internal data class ReviewData(
    val review: StockReview?,
    val lines: List<StockReviewLine>,
    val articles: List<Article>,
    val items: List<Item>,
    val locations: List<Location>,
)
