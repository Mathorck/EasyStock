package ch.mathorck.easystock.composables.server

import ch.mathorck.easystock.enums.server.ConnectError

/** The user's actions on the connect screen, so that the content can be previewed without a view model. */
internal class ConnectActions(
    val startScan: () -> Unit = {},
    val scanned: (String) -> Unit = {},
    val scanCancelled: () -> Unit = {},
    val addressChanged: (String) -> Unit = {},
    val submitAddress: () -> Unit = {},
    val cancel: () -> Unit = {},
    val tryHttp: () -> Unit = {},
    val acceptHttp: () -> Unit = {},
    val confirmCode: () -> Unit = {},
    val askErase: () -> Unit = {},
    val cancelErase: () -> Unit = {},
    val confirmErase: () -> Unit = {},
    val chooseLogin: () -> Unit = {},
    val chooseGuest: () -> Unit = {},
    val usernameChanged: (String) -> Unit = {},
    val displayNameChanged: (String) -> Unit = {},
    val passwordChanged: (String) -> Unit = {},
    val submitForm: () -> Unit = {},
    val dismissError: () -> Unit = {},
    /** After [ConnectError.LOCAL_NETWORK_DENIED]: asks again, or opens the app settings when Android won't ask any more. */
    val allowLocalNetwork: () -> Unit = {},
)
