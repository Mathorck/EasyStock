package ch.mathorck.easystock.enums.review

import ch.mathorck.easystock.data.model.ReviewStatusSerializer
import kotlinx.serialization.Serializable

@Serializable(with = ReviewStatusSerializer::class)
enum class ReviewStatus { OPEN, FINISHED, CANCELLED }
