package ch.mathorck.easystock.viewmodels.server

/** The display name dialog. */
data class NameForm(val text: String, val invalid: Boolean = false, val busy: Boolean = false)
