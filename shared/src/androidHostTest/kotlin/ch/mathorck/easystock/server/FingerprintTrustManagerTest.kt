package ch.mathorck.easystock.server

import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FingerprintTrustManagerTest {
    private val certA = certificate(CERT_A)
    private val certB = certificate(CERT_B)

    @Test
    fun theFingerprintIsTheSha256OfTheCertificate() {
        assertEquals(FP_A.replace(":", "").lowercase(), FingerprintTrustManager.fingerprintOf(certA))
        assertEquals(FP_B.replace(":", "").lowercase(), FingerprintTrustManager.fingerprintOf(certB))
    }

    @Test
    fun acceptsTheMatchingCertificate() {
        val manager = FingerprintTrustManager(FP_A)
        manager.checkServerTrusted(arrayOf(certA), "ECDHE_ECDSA")
        // Only the leaf counts, whatever follows it
        manager.checkServerTrusted(arrayOf(certA, certB), "ECDHE_ECDSA")
        assertTrue(manager.matches(certA))
    }

    @Test
    fun rejectsAnotherCertificate() {
        val manager = FingerprintTrustManager(FP_A)
        assertFalse(manager.matches(certB))
        assertFailsWith<CertificateException> { manager.checkServerTrusted(arrayOf(certB), "ECDHE_ECDSA") }
        // The pinned certificate further down the chain is not enough
        assertFailsWith<CertificateException> { manager.checkServerTrusted(arrayOf(certB, certA), "ECDHE_ECDSA") }
    }

    @Test
    fun rejectsAnEmptyChain() {
        val manager = FingerprintTrustManager(FP_A)
        assertFailsWith<CertificateException> { manager.checkServerTrusted(emptyArray(), "RSA") }
        assertFailsWith<CertificateException> { manager.checkServerTrusted(null, "RSA") }
    }

    @Test
    fun acceptsEveryFingerprintFormat() {
        listOf(FP_A, FP_A.lowercase(), FP_A.replace(":", ""), FP_A.replace(":", "").lowercase()).forEach {
            assertTrue(FingerprintTrustManager(it).matches(certA), it)
        }
    }

    @Test
    fun rejectsAnInvalidFingerprint() {
        listOf("", "abc", FP_A.dropLast(3), "g".repeat(64)).forEach {
            assertFailsWith<IllegalArgumentException>(it) { FingerprintTrustManager(it) }
        }
    }

    @Test
    fun neverTrustsClientCertificatesNorAdvertisesIssuers() {
        val manager = FingerprintTrustManager(FP_A)
        assertFailsWith<CertificateException> { manager.checkClientTrusted(arrayOf(certA), "RSA") }
        assertEquals(0, manager.acceptedIssuers.size)
    }

    private fun certificate(pem: String): X509Certificate =
        CertificateFactory.getInstance("X.509").generateCertificate(pem.trimIndent().byteInputStream()) as X509Certificate

    private companion object {
        // Self-signed test certificates (openssl req -x509 -newkey ec), with their openssl SHA-256 fingerprints
        const val FP_A = "29:63:6E:7D:E7:35:11:A3:7A:7E:26:6A:57:D3:B6:45:11:06:89:A0:14:2C:0E:6D:21:8F:CB:26:80:45:B9:70"
        const val FP_B = "B6:EB:E7:F7:3A:08:BD:17:DF:F7:C0:05:8C:73:80:DF:4C:5B:35:72:12:CF:48:EA:73:39:5C:3A:67:95:7B:8E"

        const val CERT_A = """
            -----BEGIN CERTIFICATE-----
            MIIBjDCCATOgAwIBAgIUZmIM+TM2bLfs1kKE42pdJHUoofMwCgYIKoZIzj0EAwIw
            GzEZMBcGA1UEAwwQZWFzeXN0b2NrLXRlc3QtYTAgFw0yNjExMTExMjQ2NDRaGA8y
            MTI2MTAxODEyNDY0NFowGzEZMBcGA1UEAwwQZWFzeXN0b2NrLXRlc3QtYTBZMBMG
            ByqGSM49AgEGCCqGSM49AwEHA0IABBQm2Z66T7msHG5HzySA9ZSP6hJq7FGVPT0F
            5jJM6aw+XgHwNhGTFmGreyWuY7V39axen2EtuIhhvCfMNctgnaajUzBRMB0GA1Ud
            DgQWBBRWLfI7zgbIVM3BHwakx0LtDvxy9zAfBgNVHSMEGDAWgBRWLfI7zgbIVM3B
            Hwakx0LtDvxy9zAPBgNVHRMBAf8EBTADAQH/MAoGCCqGSM49BAMCA0cAMEQCIA0R
            Y9BWNzDQQ7GwXBOFQIDS2lsx3SYUuxB/4TPKpUiJAiBwb+fSw1RdcZjd4mdCTzbR
            hecnbRaxpdkKlsgSdWdIwQ==
            -----END CERTIFICATE-----
        """

        const val CERT_B = """
            -----BEGIN CERTIFICATE-----
            MIIBjjCCATOgAwIBAgIUYLWJJTvi1dzeaYfisdny47GTiGcwCgYIKoZIzj0EAwIw
            GzEZMBcGA1UEAwwQZWFzeXN0b2NrLXRlc3QtYjAgFw0yNjExMTExMjQ2NDRaGA8y
            MTI2MTAxODEyNDY0NFowGzEZMBcGA1UEAwwQZWFzeXN0b2NrLXRlc3QtYjBZMBMG
            ByqGSM49AgEGCCqGSM49AwEHA0IABAtGYy050bU9BDeaT8P69Dqq3pxs8h+qJsuT
            aO70Rtdc9lyCy9VG5GEkoFe68ajxgQYEGM3sQiZ1vFHucirLwlmjUzBRMB0GA1Ud
            DgQWBBTDH5PlotWOWqqpfRSEJ3zi6rnCujAfBgNVHSMEGDAWgBTDH5PlotWOWqqp
            fRSEJ3zi6rnCujAPBgNVHRMBAf8EBTADAQH/MAoGCCqGSM49BAMCA0kAMEYCIQDa
            /9Op3DmDCUR9w5zkOnL6RV9h/x4XN1SWA6fJJqsM4AIhAO5eVhWcRwLQvEzvOtA1
            ttx5BHI8RQGd8ye71Es7WH9o
            -----END CERTIFICATE-----
        """
    }
}
