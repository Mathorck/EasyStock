package ch.mathorck.easystock.viewmodels.server

/** The promotion form: a Guest the Admin promoted chooses a username and a password. */
data class PromotionForm(
    val username: String = "",
    val password: String = "",
    val invalidFields: Set<FormField> = emptySet(),
    val busy: Boolean = false,
) {
    override fun toString(): String = "PromotionForm(username=$username, busy=$busy)"
}
