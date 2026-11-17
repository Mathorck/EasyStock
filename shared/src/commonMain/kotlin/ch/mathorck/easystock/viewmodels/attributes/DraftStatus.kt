package ch.mathorck.easystock.viewmodels.attributes

internal data class DraftStatus(val saving: Boolean = false, val failed: Boolean = false, val renamed: Int? = null)
