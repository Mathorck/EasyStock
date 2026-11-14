package ch.mathorck.easystock.server

import androidx.compose.runtime.Composable

/**
 * The permission to reach devices on the local network (Android 17+, `ACCESS_LOCAL_NETWORK`). Without
 * it the system silently drops every connection to a private address (192.168.x, 10.x...): a server on
 * the local network looks offline. Public addresses work without it.
 */
interface LocalNetworkAccess {
    /** True when the system blocks the local network for this app: Android 17+ and not granted. */
    fun isMissing(): Boolean

    /** True when the system dialog won't show any more (denied for good): only the app settings can allow it. */
    fun mustOpenSettings(): Boolean

    /** Shows the system dialog; the answer goes to the `onResult` of [rememberLocalNetworkAccess]. Answers true at once when nothing is missing. */
    fun request()

    /** Opens the system settings page of the app. */
    fun openSettings()
}

/**
 * The local network permission of the phone. [onResult] gets the answer of [LocalNetworkAccess.request]
 * (also after the activity was recreated). Where nothing must be asked (before Android 17, iOS) it is
 * never missing.
 */
@Composable
expect fun rememberLocalNetworkAccess(onResult: (granted: Boolean) -> Unit = {}): LocalNetworkAccess

/** Never missing: before Android 17, on iOS, in previews and tests. */
object NoLocalNetworkAccessNeeded : LocalNetworkAccess {
    override fun isMissing() = false

    override fun mustOpenSettings() = false

    override fun request() = Unit

    override fun openSettings() = Unit
}

/**
 * True when [serverUrl] names a literal private address: IPv4 10/8, 172.16/12, 192.168/16, 169.254/16,
 * IPv6 fc00::/7 and fe80::/10, or an mDNS `.local` name. Such a server can't be reached at all while
 * [LocalNetworkAccess.isMissing], so the app can say so without waiting for the connect timeout. A plain
 * host name may resolve to a private address too: false here, its call decides.
 */
fun isLocalNetworkAddress(serverUrl: String): Boolean {
    val authority = serverUrl.trim().substringAfter("://").substringBefore('/').substringBefore('?').substringAfterLast('@')
    val host = if (authority.startsWith('[')) {
        authority.substringAfter('[').substringBefore(']')
    } else {
        authority.substringBefore(':')
    }.lowercase().trimEnd('.')
    if (host.isEmpty()) return false
    if (host.endsWith(".local")) return true
    if (':' in host) {
        // IPv6: the first group decides (fc00::/7 unique local, fe80::/10 link-local)
        val first = host.substringBefore(':').toIntOrNull(16) ?: return false
        return first and 0xfe00 == 0xfc00 || first and 0xffc0 == 0xfe80
    }
    val parts = host.split('.')
    if (parts.size != 4) return false
    val octets = parts.map { part -> part.toIntOrNull()?.takeIf { it in 0..255 && part.length in 1..3 } ?: return false }
    val (a, b) = octets
    return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254)
}
