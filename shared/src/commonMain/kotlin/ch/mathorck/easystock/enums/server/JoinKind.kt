package ch.mathorck.easystock.enums.server

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
