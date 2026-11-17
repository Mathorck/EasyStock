package ch.mathorck.easystock.viewmodels.server

/** Where "Delete my account" is. */
enum class DeletePhase {
    /** The confirmation, with the password field of a full account. */
    CONFIRM,

    /** The local changes are sent first. */
    SYNCING,

    /** Not all changes could be sent: delete anyway (they are saved in an automatic backup) or cancel. */
    SYNC_FAILED,

    /** `DELETE /me`, then the wipe of the phone. */
    DELETING,

    /** Deleted and wiped: the screen leaves. */
    DONE,
}
