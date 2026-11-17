package ch.mathorck.easystock.services.server

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TlsFailureTest {
    @Test
    fun aRefusedCertificateIsATlsFailure() {
        val handshake = SSLHandshakeException("untrusted").apply { initCause(CertificateException("pin")) }
        assertTrue(isTlsFailure(handshake))
        assertTrue(isTlsFailure(IOException("wrapped", handshake)))
        assertTrue(isTlsFailure(SSLPeerUnverifiedException("wrong host name")))
    }

    @Test
    fun noAnswerIsNotATlsFailure() {
        assertFalse(isTlsFailure(ConnectException("refused")))
        assertFalse(isTlsFailure(SocketTimeoutException("timeout")))
        assertFalse(isTlsFailure(UnknownHostException("nas.example")))
    }

    @Test
    fun aPlainHttpServerOnThePortIsNotATlsFailure() {
        // What OkHttp throws when the port answers plain HTTP: the HTTP fallback is for this case
        assertFalse(isTlsFailure(SSLException("Unrecognized SSL message, plaintext connection?")))
    }
}
