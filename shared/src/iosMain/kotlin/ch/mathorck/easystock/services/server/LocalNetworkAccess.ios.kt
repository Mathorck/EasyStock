package ch.mathorck.easystock.services.server

import androidx.compose.runtime.Composable

// TODO(server mode on iOS): only Android is delivered
@Composable
actual fun rememberLocalNetworkAccess(onResult: (granted: Boolean) -> Unit): LocalNetworkAccess = NoLocalNetworkAccessNeeded
