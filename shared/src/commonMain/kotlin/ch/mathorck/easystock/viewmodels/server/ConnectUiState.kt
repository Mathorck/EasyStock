package ch.mathorck.easystock.viewmodels.server

import ch.mathorck.easystock.enums.server.ConnectError

data class ConnectUiState(
    val step: ConnectStep = ConnectStep.Choose,
    val address: String = "",
    /** The typed address can't be read. */
    val addressInvalid: Boolean = false,
    val username: String = "",
    val displayName: String = "",
    val password: String = "",
    /** Fields that break the account rules, shown after a submit. */
    val invalidFields: Set<FormField> = emptySet(),
    /** Shown in a dialog. */
    val error: ConnectError? = null,
) {
    // The password must never end up in logs
    override fun toString(): String = "ConnectUiState(step=$step, address=$address, error=$error)"
}
