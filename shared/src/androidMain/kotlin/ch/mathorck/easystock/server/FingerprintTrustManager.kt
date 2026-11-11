package ch.mathorck.easystock.server

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * Accepts only a server whose leaf certificate has the SHA-256 [fingerprint] from the join QR code
 * (64 lowercase hex characters, see [normalizeFingerprint]). Used for self-signed certificates, which the
 * normal check rejects before an OkHttp `CertificatePinner` is looked at. Without a fingerprint the app
 * uses the normal Android check instead.
 *
 * The pin replaces the chain and expiry checks: the person scanned the code from the server itself.
 * The host name is not in a self-signed certificate on an IP address, so the client that uses this also
 * needs a host name verifier tied to the same pin (next phase, with the API client).
 * Not used yet.
 */
class FingerprintTrustManager(fingerprint: String) : X509TrustManager {
    private val expected: ByteArray = run {
        val hex = requireNotNull(normalizeFingerprint(fingerprint)) { "Invalid fingerprint" }
        ByteArray(32) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("No server certificate")
        if (!matches(leaf)) throw CertificateException("Server certificate does not match the fingerprint")
    }

    /** The app never authenticates with a client certificate. */
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Client certificates are not supported")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    /** Constant-time comparison of the SHA-256 of the DER encoding. */
    fun matches(certificate: X509Certificate): Boolean =
        MessageDigest.isEqual(sha256(certificate.encoded), expected)

    companion object {
        /** SHA-256 of a certificate, as 64 lowercase hex characters (the format of the QR code). */
        fun fingerprintOf(certificate: X509Certificate): String =
            sha256(certificate.encoded).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

        private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    }
}
