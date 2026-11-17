package ch.mathorck.easystock.viewmodels.server

import ch.mathorck.easystock.data.dto.server.MeAnswer
import ch.mathorck.easystock.enums.server.ConnectError

data class AccountUiState(
    /** From `GET /me`, null until it answered. */
    val me: MeAnswer? = null,
    /** `GET /me` failed: the screen shows what is stored on the phone. */
    val meError: ConnectError? = null,
    /** An alerts switch change is being sent. */
    val alertsBusy: Boolean = false,
    val logout: LogoutStep? = null,
    val promotion: PromotionForm? = null,
    val nameForm: NameForm? = null,
    val passwordForm: PasswordForm? = null,
    /** The password was changed: a dialog tells that the other devices must log in again. */
    val passwordChanged: Boolean = false,
    val deleteForm: DeleteForm? = null,
    /** Shown once in a dialog. */
    val error: ConnectError? = null,
)
