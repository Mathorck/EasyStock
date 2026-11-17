package ch.mathorck.easystock.services.backup

import ch.mathorck.easystock.data.dto.backup.WifiTicket
import ch.mathorck.easystock.services.server.JoinCode

sealed interface ScannedQr {
    /** A backup payload (maybe encrypted). */
    class Data(val payload: ByteArray) : ScannedQr

    /** Where to download a backup on the local network. */
    data class Wifi(val ticket: WifiTicket) : ScannedQr

    /** A server QR code (server mode). */
    data class Join(val code: JoinCode) : ScannedQr
}
