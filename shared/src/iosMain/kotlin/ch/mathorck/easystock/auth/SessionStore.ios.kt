package ch.mathorck.easystock.auth

import androidx.compose.runtime.Composable

private val store = InMemorySessionStore()

// TODO(server mode on iOS): keep the refresh token in the Keychain. Until then the session is lost
// when the app stops.
@Composable
actual fun rememberSessionStore(): SessionStore = store
