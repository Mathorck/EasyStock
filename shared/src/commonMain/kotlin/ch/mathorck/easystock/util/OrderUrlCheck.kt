package ch.mathorck.easystock.util

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
