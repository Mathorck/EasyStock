package ch.mathorck.easystock.services.server

import ch.mathorck.easystock.data.model.sync.SyncState

/** The server of the `sync_state` row: what the HTTP client needs. */
data class ServerConnection(
    /** Without a trailing slash, e.g. `https://stock.example.org` or `http://192.168.1.20:8080`. */
    val serverUrl: String,
    /** SHA-256 of the self-signed certificate (64 lowercase hex), null for HTTP or a trusted certificate. */
    val fingerprint: String? = null,
) {
    val isHttps: Boolean get() = serverUrl.startsWith("https://")

    /** Base of every API call. */
    val apiBase: String get() = serverUrl.trimEnd('/') + "/api/v1/"

    companion object {
        fun of(state: SyncState) = ServerConnection(state.serverUrl.trimEnd('/'), state.certFingerprint)
    }
}
