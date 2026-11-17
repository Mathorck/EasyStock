package ch.mathorck.easystock.viewmodels.server

/**
 * The "Delete my account" dialog. [guest]: no password (a Guest has none). [wrongPassword]: the server refused
 * it. [pending]: in [DeletePhase.SYNC_FAILED], the changes still not sent (null when they could not be counted).
 */
data class DeleteForm(
    val guest: Boolean,
    val password: String = "",
    val passwordEmpty: Boolean = false,
    val wrongPassword: Boolean = false,
    val phase: DeletePhase = DeletePhase.CONFIRM,
    val pending: Int? = null,
) {
    val busy: Boolean get() = phase == DeletePhase.SYNCING || phase == DeletePhase.DELETING || phase == DeletePhase.DONE

    override fun toString(): String = "DeleteForm(guest=$guest, phase=$phase)"
}
