package ch.mathorck.easystock.services.reset

/**
 * What [DataReset.runAfterRevoke] saved before the wipe. [unsentChanges]: local changes not sent to the
 * server. [backupFile]: the automatic backup that holds them, null when none was written.
 * [backupFailed]: the backup was tried and failed (the wipe went on: the session is gone either way).
 */
data class RevokeBackup(val unsentChanges: Int, val backupFile: String? = null, val backupFailed: Boolean = false)
