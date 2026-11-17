package ch.mathorck.easystock.viewmodels.server

/** The logout, step by step. */
sealed interface LogoutStep {
    /**
     * [pending]: local changes not sent yet (they are lost). [guest]: the account is deleted.
     * [syncFailed]: "Sync, then log out" could not send them all.
     */
    data class Confirm(val pending: Int, val guest: Boolean, val syncFailed: Boolean = false) : LogoutStep

    /** "Sync, then log out": the changes are being sent. */
    data class Syncing(val guest: Boolean) : LogoutStep

    data object Working : LogoutStep

    /** The server can't be reached: log out on this phone only (the session stays valid on the server until it expires). */
    data object Offline : LogoutStep

    /** The phone is standalone and empty again. */
    data object Done : LogoutStep
}
