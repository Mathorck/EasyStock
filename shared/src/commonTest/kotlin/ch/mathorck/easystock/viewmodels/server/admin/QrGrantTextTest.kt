package ch.mathorck.easystock.viewmodels.server.admin

import ch.mathorck.easystock.data.dto.server.QrGrant
import ch.mathorck.easystock.enums.server.JoinKind
import ch.mathorck.easystock.services.server.JoinCode
import ch.mathorck.easystock.services.server.ServerConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class QrGrantTextTest {
    private val connection = ServerConnection("https://192.168.1.10:8443", "ab".repeat(32))

    @Test
    fun builtFromTheTokenWithTheConnection() {
        val text = assertNotNull(QrGrant(token = "tok").qrText(connection, JoinKind.RESET))
        val code = JoinCode.parse(text)
        assertEquals(JoinKind.RESET, code.kind)
        assertEquals("tok", code.token)
        assertEquals(connection.serverUrl, code.serverUrl)
        assertEquals(connection.fingerprint, code.fingerprint)
    }

    @Test
    fun serverCodeOnlyWhenValidAndOfTheKind() {
        val invite = JoinCode("https://192.168.1.10:8443", JoinKind.INVITE, "t").toQrText()
        assertEquals(invite, QrGrant(qr = invite).qrText(connection, JoinKind.INVITE))
        assertNull(QrGrant(qr = invite).qrText(connection, JoinKind.RESET))
        assertNull(QrGrant(qr = "easystock://invite?server=x&token=y").qrText(connection, JoinKind.INVITE))
        assertNull(QrGrant().qrText(connection, JoinKind.INVITE))
    }
}
