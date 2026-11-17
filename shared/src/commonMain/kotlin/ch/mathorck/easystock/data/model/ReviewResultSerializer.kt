package ch.mathorck.easystock.data.model

import ch.mathorck.easystock.enums.review.ReviewResult

object ReviewResultSerializer : DbEnumSerializer<ReviewResult>("ReviewResult", ReviewResult.entries.toTypedArray())
