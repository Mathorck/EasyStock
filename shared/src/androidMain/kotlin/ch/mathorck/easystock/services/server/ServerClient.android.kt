package ch.mathorck.easystock.services.server

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ch.mathorck.easystock.data.model.sync.SyncState
import ch.mathorck.easystock.services.auth.ServerSessionEvents
import ch.mathorck.easystock.services.auth.sessionStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * OkHttp. With a [fingerprint] (self-signed certificate), the server is trusted only when its leaf
 * certificate has that SHA-256, and the host name check is tied to the same pin: a self-signed
 * certificate on an IP address has no matching name, but the pin already proves it is the scanned server.
 * Without a fingerprint, the normal Android checks apply.
 */
actual fun serverHttpEngine(fingerprint: String?): HttpClientEngine = OkHttp.create {
    if (fingerprint != null) {
        val trust = FingerprintTrustManager(fingerprint)
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }
        config {
            sslSocketFactory(ssl.socketFactory, trust)
            hostnameVerifier(pinnedHostnameVerifier(trust))
        }
    }
}

/** Accepts the host only when the certificate of the TLS session matches the pin. */
internal fun pinnedHostnameVerifier(trust: FingerprintTrustManager) = HostnameVerifier { _, session ->
    val leaf = runCatching { session.peerCertificates.firstOrNull() as? X509Certificate }.getOrNull()
    leaf != null && trust.matches(leaf)
}

/**
 * A refused certificate shows as an `SSLHandshakeException` caused by a `CertificateException` (untrusted,
 * expired, wrong pin), a wrong host name as an `SSLPeerUnverifiedException`. Other SSL errors (a plain
 * HTTP server on that port...) are not certificate refusals.
 */
internal actual fun isTlsFailure(e: Throwable): Boolean =
    generateSequence(e) { it.cause.takeIf { cause -> cause !== it } }.take(10)
        .any { it is CertificateException || it is SSLPeerUnverifiedException }

private var holder: ServerClientHolder? = null

/** Process-wide holder: all screens share the client, so token refreshes never race. Main thread. */
private fun holder(context: Context): ServerClientHolder = holder ?: run {
    val app = context.applicationContext
    val versions = ClientVersions(appVersion = appVersionName(app))
    ServerClientHolder { connection ->
        val session = sessionStore(app)
        createServerClient(
            connection = connection,
            engine = serverHttpEngine(connection.fingerprint),
            session = session,
            versions = versions,
            // The wipe runs outside this client (MainComposable): it closes the client. A token missing
            // or unreadable on this phone is a local loss (log in again), not a revoke by the server
            onSessionRevoked = { ServerSessionEvents.sessionRevoked(lostLocally = session.refreshTokenMissing) },
        )
    }.also { holder = it }
}

/** The shared client of [connection], for the sync engine. Main thread. */
internal fun serverClientFor(context: Context, connection: ServerConnection): ServerClient = holder(context).clientFor(connection)

actual fun releaseServerClient() {
    holder?.release()
}

internal fun appVersionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0.0.0"

@Composable
actual fun rememberServerClient(state: SyncState?): ServerClient? {
    val context = LocalContext.current
    // Standalone: no sync_state row, so no client and no network
    val connection = state?.let { ServerConnection.of(it) } ?: return null
    return remember(connection) { holder(context).clientFor(connection) }
}
