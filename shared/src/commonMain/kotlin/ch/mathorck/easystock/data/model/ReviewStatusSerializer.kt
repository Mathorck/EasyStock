package ch.mathorck.easystock.data.model

import ch.mathorck.easystock.enums.review.ReviewStatus

object ReviewStatusSerializer : DbEnumSerializer<ReviewStatus>("ReviewStatus", ReviewStatus.entries.toTypedArray())
