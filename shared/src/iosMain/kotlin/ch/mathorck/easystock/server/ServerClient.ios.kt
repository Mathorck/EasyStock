package ch.mathorck.easystock.server

import androidx.compose.runtime.Composable
import ch.mathorck.easystock.data.model.SyncState
import io.ktor.client.engine.HttpClientEngine

// TODO(server mode on iOS): Darwin engine with the fingerprint pin. Only Android is delivered.
actual fun serverHttpEngine(fingerprint: String?): HttpClientEngine =
    throw UnsupportedOperationException("Server mode is not available on iOS yet")

@Composable
actual fun rememberServerClient(state: SyncState?): ServerClient? = null
