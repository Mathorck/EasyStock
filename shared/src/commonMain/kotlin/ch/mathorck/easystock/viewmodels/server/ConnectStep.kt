package ch.mathorck.easystock.viewmodels.server

import ch.mathorck.easystock.data.dto.server.ServerInfo

/** Where the connect screen is. Nothing is written on the phone before [Done]. */
sealed interface ConnectStep {
    /** Scan a code or type an address. */
    data object Choose : ConnectStep

    data object Scanning : ConnectStep

    /**
     * Before the first call: the system dialog asks for the local network permission (Android 17+), the
     * screen answers with [ConnectViewModel.localNetworkAnswered].
     */
    data class LocalNetwork(val target: ConnectTarget) : ConnectStep

    /** Asking `/server-info`. */
    data class Checking(val target: ConnectTarget) : ConnectStep

    /** HTTPS gave no answer: offer plain HTTP ([httpUrl]), after the warning. */
    data class HttpsFailed(val target: ConnectTarget, val httpUrl: String) : ConnectStep

    /** A code scanned outside this screen (backup): nothing is sent before the user confirms. */
    data class ConfirmCode(val target: ConnectTarget) : ConnectStep

    /** Before the first call over plain HTTP: the user must accept it. */
    data class HttpWarning(val target: ConnectTarget) : ConnectStep

    /** The server answers, but this phone has data: export it and erase it, or cancel. */
    data class NotEmpty(val target: ConnectTarget, val info: ServerInfo) : ConnectStep

    /** The typed confirmation of the erase (with the offer to export first). */
    data class EraseConfirm(val target: ConnectTarget, val info: ServerInfo, val failed: Boolean = false) : ConnectStep

    data class Erasing(val target: ConnectTarget, val info: ServerInfo) : ConnectStep

    /** A printed Guest code: log in to an own account, or continue as a Guest. */
    data class GuestChoice(val target: ConnectTarget, val info: ServerInfo) : ConnectStep

    data class Form(
        val target: ConnectTarget,
        val info: ServerInfo,
        val mode: FormMode,
        /** The join call runs. */
        val busy: Boolean = false,
    ) : ConnectStep

    /** Joined: sync_state and the tokens are written. */
    data object Done : ConnectStep
}
