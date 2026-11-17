package ch.mathorck.easystock.services.server

/** Rules of the account fields (03-server-spec.md, "Accounts"), checked before sending. */
object AccountRules {
    const val PASSWORD_MIN = 10
    const val PASSWORD_MAX = 128
    const val DISPLAY_NAME_MAX = 50
    private val USERNAME = Regex("^[A-Za-z0-9._-]{3,32}$")

    fun isValidUsername(username: String): Boolean = USERNAME.matches(username)

    fun isValidPassword(password: String): Boolean = password.length in PASSWORD_MIN..PASSWORD_MAX

    fun isValidDisplayName(name: String): Boolean = name.trim().length in 1..DISPLAY_NAME_MAX
}
