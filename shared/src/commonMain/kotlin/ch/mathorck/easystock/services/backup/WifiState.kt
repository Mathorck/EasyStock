package ch.mathorck.easystock.services.backup

import ch.mathorck.easystock.enums.backup.BackupError

sealed interface WifiState {
    /** Waiting for the other phone to scan the QR code. */
    data object Waiting : WifiState

    data class Sending(val sentBytes: Long, val totalBytes: Long) : WifiState

    data object Done : WifiState

    /** Nobody connected in time: the QR code is no longer valid. */
    data object Expired : WifiState

    data class Failed(val error: BackupError) : WifiState
}
