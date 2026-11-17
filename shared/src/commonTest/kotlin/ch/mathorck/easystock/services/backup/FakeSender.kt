package ch.mathorck.easystock.services.backup

import ch.mathorck.easystock.data.dto.backup.WifiTicket
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class FakeSender : WifiSender {
    override val ticket = WifiTicket(host = "192.168.1.2", port = 8080, token = "t", key = "k")
    override val timeout = 5.minutes
    val flow = MutableStateFlow<WifiState>(WifiState.Waiting)
    override val state: StateFlow<WifiState> = flow
    var stopped = false

    override fun stop() {
        stopped = true
    }
}
