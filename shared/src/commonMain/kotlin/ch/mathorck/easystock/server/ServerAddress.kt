package ch.mathorck.easystock.server

/** A typed server address, once read. */
sealed interface TypedAddress {
    /**
     * [url] is the address to try first. [schemeTyped]: the user wrote `http://` or `https://`. Without a
     * scheme the app tries HTTPS, and offers HTTP only after a failure and a warning ([httpFallback]).
     */
    data class Url(val url: String, val schemeTyped: Boolean) : TypedAddress {
        val isHttps: Boolean get() = url.startsWith("https://")

        /** The plain HTTP address of a schemeless HTTPS try, null when the user chose the scheme. */
        val httpFallback: String? get() = if (!schemeTyped && isHttps) "http://" + url.removePrefix("https://") else null
    }

    /** A pasted join link (`easystock://join?...` or the server plan format). */
    data class Code(val code: JoinCode) : TypedAddress

    data object Invalid : TypedAddress
}

/**
 * Reads what the user typed or pasted in the address field: a join link, `https://host[:port][/path]`,
 * `http://...`, or `host[:port][/path]` without a scheme (HTTPS first). Spaces around are ignored, the
 * scheme is case-insensitive, a trailing slash is dropped.
 */
fun parseTypedAddress(input: String): TypedAddress {
    val text = input.trim()
    if (text.isEmpty()) return TypedAddress.Invalid
    if (text.startsWith("easystock://", ignoreCase = true)) {
        return runCatching { TypedAddress.Code(JoinCode.parse(text)) }.getOrDefault(TypedAddress.Invalid)
    }
    val lower = text.lowercase()
    val (url, typed) = when {
        lower.startsWith("https://") -> "https://" + text.substring(8) to true
        lower.startsWith("http://") -> "http://" + text.substring(7) to true
        "://" in text -> return TypedAddress.Invalid
        else -> "https://$text" to false
    }
    val clean = url.trimEnd('/')
    return if (isValidServerUrl(clean)) TypedAddress.Url(clean, typed) else TypedAddress.Invalid
}
