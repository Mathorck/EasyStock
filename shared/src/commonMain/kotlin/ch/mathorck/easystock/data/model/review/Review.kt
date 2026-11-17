package ch.mathorck.easystock.data.model.review

import ch.mathorck.easystock.data.model.article.PendingUnit

val StockReviewLine.pendingUnit: PendingUnit? get() = pendingItem?.let(PendingUnit::fromJson)
