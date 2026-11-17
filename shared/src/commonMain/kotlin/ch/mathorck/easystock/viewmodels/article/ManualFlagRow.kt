package ch.mathorck.easystock.viewmodels.article

/** An open manual flag ("Report a problem"). [author] is null in standalone mode or for an unknown user. */
data class ManualFlagRow(val id: String, val note: String, val createdAt: String, val author: String?)
