package ch.mathorck.easystock.viewmodels.review

data class ReviewGroup(val locationId: String, val name: String, val path: String, val lines: List<ReviewLineUi>)
