package ch.mathorck.easystock.viewmodels.article

internal data class ActionStatus(val saving: Boolean = false, val failed: Boolean = false, val rejectedSerial: String? = null)
