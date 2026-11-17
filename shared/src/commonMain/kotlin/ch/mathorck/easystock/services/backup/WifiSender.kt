package ch.mathorck.easystock.services.backup

import ch.mathorck.easystock.data.dto.backup.WifiTicket
import kotlin.time.Duration
import kotlinx.coroutines.flow.StateFlow

/** A running Wi-Fi transfer. Single download, closed on [stop], once done or after the timeout. */
interface WifiSender {
    val ticket: WifiTicket
    val state: StateFlow<WifiState>

    /** How long the code stays valid, from the start of the share. */
    val timeout: Duration

    fun stop()
}
