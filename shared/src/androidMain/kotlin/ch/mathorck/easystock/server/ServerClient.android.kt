package ch.mathorck.easystock.server

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ch.mathorck.easystock.auth.ServerSessionEvents
import ch.mathorck.easystock.auth.sessionStore
import ch.mathorck.easystock.data.model.SyncState
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext

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

private var holder: ServerClientHolder? = null

/** Process-wide holder: all screens share the client, so token refreshes never race. Main thread. */
private fun holder(context: Context): ServerClientHolder = holder ?: run {
    val app = context.applicationContext
    val versions = ClientVersions(appVersion = appVersionName(app))
    ServerClientHolder { connection ->
        createServerClient(
            connection = connection,
            engine = serverHttpEngine(connection.fingerprint),
            session = sessionStore(app),
            versions = versions,
            // The wipe runs outside this client (MainComposable): it closes the client
            onSessionRevoked = { ServerSessionEvents.sessionRevoked() },
        )
    }.also { holder = it }
}

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
