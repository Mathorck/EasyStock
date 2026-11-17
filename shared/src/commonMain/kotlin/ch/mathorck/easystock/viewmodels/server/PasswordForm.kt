package ch.mathorck.easystock.viewmodels.server

/** The change password dialog. [wrongCurrent]: the server refused the current password. */
data class PasswordForm(
    val current: String = "",
    val new: String = "",
    /** Only [FormField.PASSWORD] (the new one). */
    val invalidFields: Set<FormField> = emptySet(),
    val currentEmpty: Boolean = false,
    val wrongCurrent: Boolean = false,
    val busy: Boolean = false,
) {
    override fun toString(): String = "PasswordForm(busy=$busy)"
}
