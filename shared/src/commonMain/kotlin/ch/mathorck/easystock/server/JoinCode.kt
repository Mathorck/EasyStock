package ch.mathorck.easystock.server

/** Prefix of every server QR code, next to the backup ones (backup/QrTransfer). */
const val JOIN_QR_PREFIX = "easystock://join?"

/** What a server QR code is for. */
enum class JoinKind(val code: String) {
    /** First start of a server: create the first Admin. */
    SETUP("setup"),

    /** Invite for a new user with a role chosen by an Admin. */
    INVITE("invite"),

    /** Printed code: log in, or continue as a Guest. */
    GUEST("guest"),

    /** Password reset of an existing user. */
    RESET("reset"),
    ;

    companion object {
        fun fromCode(code: String): JoinKind? = entries.firstOrNull { it.code == code }
    }
}

/**
 * A scanned server QR code: `easystock://join?u=<server URL>&k=<kind>&t=<token>&fp=<certificate SHA-256>`.
 * [fingerprint] (64 lowercase hex characters) is only given for HTTPS with a self-signed certificate.
 * Built by [build] for the admin screens (invite, reset and printed codes), read by [parse] when scanned.
 */
data class JoinCode(
    val serverUrl: String,
    val kind: JoinKind,
    val token: String,
    val fingerprint: String? = null,
) {
    val isHttps: Boolean get() = serverUrl.startsWith("https://")

    // The token must not end up in logs
    override fun toString(): String = "JoinCode(serverUrl=$serverUrl, kind=$kind, fingerprint=$fingerprint)"

    /**
     * The QR code text: `easystock://join?u=...&k=...&t=...` plus `&fp=...` when there is a fingerprint.
     * Values are percent-encoded, so [parse] gives back the same code.
     */
    fun toQrText(): String = buildString {
        append(JOIN_QR_PREFIX)
        append("u=").append(percentEncode(serverUrl))
        append("&k=").append(kind.code)
        append("&t=").append(percentEncode(token))
        if (fingerprint != null) append("&fp=").append(fingerprint)
    }

    companion object {
        const val MAX_TEXT_LENGTH = 2048
        const val MAX_TOKEN_LENGTH = 512

        /**
         * Reads a join code. Throws [IllegalArgumentException] when the text is not a valid join code:
         * missing, empty or repeated parameter, bad percent-encoding, unknown kind, invalid URL or
         * fingerprint, or a fingerprint on an HTTP address. Unknown parameters are ignored (newer server).
         */
        fun parse(text: String): JoinCode {
            require(text.startsWith(JOIN_QR_PREFIX)) { "Not a join code" }
            require(text.length <= MAX_TEXT_LENGTH) { "Join code too long" }
            val query = text.substring(JOIN_QR_PREFIX.length)
            require('#' !in query) { "Fragment in join code" }

            val params = HashMap<String, String>()
            for (part in query.split('&')) {
                val eq = part.indexOf('=')
                require(eq > 0) { "Invalid parameter" }
                val key = percentDecode(part.substring(0, eq))
                val value = percentDecode(part.substring(eq + 1))
                require(params.put(key, value) == null) { "Repeated parameter" }
            }

            val url = params["u"]
            require(!url.isNullOrEmpty()) { "Missing server URL" }
            require(isValidServerUrl(url)) { "Invalid server URL" }

            val kind = JoinKind.fromCode(params["k"] ?: "") ?: throw IllegalArgumentException("Unknown kind")

            val token = params["t"]
            require(!token.isNullOrEmpty()) { "Missing token" }
            require(token.length <= MAX_TOKEN_LENGTH && token.all { it.code in 0x21..0x7e }) { "Invalid token" }

            val fingerprint = params["fp"]?.let { raw ->
                normalizeFingerprint(raw) ?: throw IllegalArgumentException("Invalid fingerprint")
            }
            val https = url.startsWith("https://")
            require(fingerprint == null || https) { "Fingerprint on an HTTP address" }

            return JoinCode(serverUrl = url.trimEnd('/'), kind = kind, token = token, fingerprint = fingerprint)
        }

        /**
         * A join code for [serverUrl] (the `sync_state` address) and [token] given by the server.
         * Checked like a scanned one: throws [IllegalArgumentException] for an invalid URL, token or
         * fingerprint, or a fingerprint on an HTTP address.
         */
        fun build(serverUrl: String, kind: JoinKind, token: String, fingerprint: String? = null): JoinCode {
            val code = JoinCode(serverUrl.trimEnd('/'), kind, token, fingerprint?.let(::normalizeFingerprint) ?: fingerprint)
            // Same rules as a scanned code: what the admin shows can always be scanned
            return parse(code.toQrText())
        }
    }
}

/**
 * SHA-256 fingerprint as 64 lowercase hex characters. Accepts upper case and `:` separators
 * (as shown by openssl: `AB:CD:...`). Null when it is not a SHA-256.
 */
fun normalizeFingerprint(raw: String): String? {
    val hex = if (':' in raw) {
        val bytes = raw.split(':')
        if (bytes.any { it.length != 2 }) return null
        bytes.joinToString("")
    } else {
        raw
    }
    if (hex.length != 64 || hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    return hex.lowercase()
}

/**
 * `http://` or `https://`, a host (name, IPv4 or `[IPv6]`), an optional port 1-65535 and an optional
 * path. No user info, query or fragment, no space or control character.
 */
internal fun isValidServerUrl(url: String): Boolean {
    val rest = when {
        url.startsWith("https://") -> url.removePrefix("https://")
        url.startsWith("http://") -> url.removePrefix("http://")
        else -> return false
    }
    if (rest.any { it.code <= 0x20 || it.code == 0x7f || it in "@?#\\" }) return false
    val authority = rest.substringBefore('/')
    val host: String
    val port: String?
    if (authority.startsWith('[')) {
        val end = authority.indexOf(']')
        if (end < 0) return false
        host = authority.substring(1, end)
        val after = authority.substring(end + 1)
        port = when {
            after.isEmpty() -> null
            after.startsWith(':') -> after.substring(1)
            else -> return false
        }
        if (host.isEmpty() || host.any { it != ':' && it != '.' && it !in '0'..'9' && it.lowercaseChar() !in 'a'..'f' }) return false
    } else {
        host = authority.substringBefore(':')
        port = if (':' in authority) authority.substringAfter(':') else null
        if (host.isEmpty() || host.length > 253) return false
        if (host.any { it !in 'a'..'z' && it !in 'A'..'Z' && it !in '0'..'9' && it != '-' && it != '.' }) return false
        if (host.startsWith('.') || host.startsWith('-') || ".." in host) return false
    }
    if (port != null) {
        val number = port.toIntOrNull() ?: return false
        if (number !in 1..65535 || port != number.toString()) return false
    }
    return true
}

/** RFC 3986 percent-encoding of UTF-8: everything but the unreserved characters `A-Z a-z 0-9 - . _ ~`. */
internal fun percentEncode(text: String): String = buildString {
    for (byte in text.encodeToByteArray()) {
        val b = byte.toInt() and 0xff
        val c = b.toChar()
        if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "-._~") {
            append(c)
        } else {
            append('%').append(HEX[b shr 4]).append(HEX[b and 0x0f])
        }
    }
}

private const val HEX = "0123456789ABCDEF"

/** RFC 3986 percent-decoding to UTF-8. `+` stays a plus. Throws on a bad escape or invalid UTF-8. */
internal fun percentDecode(text: String): String {
    if ('%' !in text) return text
    val bytes = ArrayList<Byte>(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '%') {
            require(i + 2 < text.length) { "Bad escape" }
            val value = text.substring(i + 1, i + 3).toIntOrNull(16)
            require(value != null && text[i + 1] != '+' && text[i + 1] != '-') { "Bad escape" }
            bytes += value.toByte()
            i += 3
        } else {
            c.toString().encodeToByteArray().forEach { bytes += it }
            i++
        }
    }
    return try {
        bytes.toByteArray().decodeToString(throwOnInvalidSequence = true)
    } catch (e: CharacterCodingException) {
        throw IllegalArgumentException("Invalid UTF-8", e)
    }
}
