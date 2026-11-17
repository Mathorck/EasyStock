package ch.mathorck.easystock.util

/** Max length of `article.order_url`, same as the server column (docs/schema.dbml). */
const val MAX_ORDER_URL_LENGTH = 2048

private const val MAX_PORT_DIGITS = 5

/** Result of [checkOrderUrl]. */
sealed interface OrderUrlCheck {
    /** Blank input: no link. */
    data object Empty : OrderUrlCheck

    /** An absolute http(s) URL with a host. [url] is the trimmed value to store. */
    data class Valid(val url: String) : OrderUrlCheck

    data object TooLong : OrderUrlCheck

    /** Not an absolute http(s) URL with a host (other scheme, no host, spaces, user info, ...). */
    data object Invalid : OrderUrlCheck
}

/**
 * Checks the order link as typed: trims it, blank is [OrderUrlCheck.Empty]. Only absolute `http://` and
 * `https://` URLs with a host are valid, no other scheme (javascript:, intent:, file:, ...), at most
 * [MAX_ORDER_URL_LENGTH] characters. White space, control characters and user info (`user@host`, used to
 * disguise the real host) are refused.
 */
fun checkOrderUrl(raw: String): OrderUrlCheck {
    val value = raw.trim()
    return when {
        value.isEmpty() -> OrderUrlCheck.Empty
        value.length > MAX_ORDER_URL_LENGTH -> OrderUrlCheck.TooLong
        hostOf(value) == null -> OrderUrlCheck.Invalid
        else -> OrderUrlCheck.Valid(value)
    }
}

/** The link to store: trimmed, null when blank. Call only on a value that passed [checkOrderUrl]. */
fun orderUrlOrNull(raw: String): String? = raw.trim().ifEmpty { null }

/**
 * True when [url] is stored as is and is safe to open: re-checks the scheme, as the value may come from a
 * backup or a sync and not from the form.
 */
fun isOpenableOrderUrl(url: String): Boolean = (checkOrderUrl(url) as? OrderUrlCheck.Valid)?.url == url

/** Host of a valid order link, without port, user info or path. Null when [url] is not valid. */
fun orderUrlHost(url: String): String? = if (checkOrderUrl(url) is OrderUrlCheck.Valid) hostOf(url.trim()) else null

private fun hostOf(value: String): String? {
    val rest = when {
        value.startsWith("https://", ignoreCase = true) -> value.substring("https://".length)
        value.startsWith("http://", ignoreCase = true) -> value.substring("http://".length)
        else -> return null
    }
    if (value.any { it.isWhitespace() || it.isISOControl() }) return null
    val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
    if (authority.contains('@')) return null
    val colon = authority.indexOf(':')
    val host = if (colon < 0) authority else authority.substring(0, colon)
    if (colon >= 0) {
        val port = authority.substring(colon + 1)
        if (port.isEmpty() || port.length > MAX_PORT_DIGITS || !port.all { it in '0'..'9' }) return null
    }
    val validHost = host.isNotEmpty() && host.first().isLetterOrDigit() &&
        host.all { it.isLetterOrDigit() || it == '-' || it == '.' || it == '_' }
    return host.takeIf { validHost }
}
