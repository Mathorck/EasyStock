package ch.mathorck.easystock.services.server

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
