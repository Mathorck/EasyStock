package ch.mathorck.easystock.services.review

import ch.mathorck.easystock.enums.review.SkipReason

data class SkippedLine(val lineId: String, val reason: SkipReason)
