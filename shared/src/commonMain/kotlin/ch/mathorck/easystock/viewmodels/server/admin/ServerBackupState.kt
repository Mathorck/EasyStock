package ch.mathorck.easystock.viewmodels.server.admin

import ch.mathorck.easystock.services.server.LocalFile

data class ServerBackupState(
    val downloading: Boolean = false,
    /** The last downloaded backup, to save or share. */
    val downloaded: LocalFile? = null,
    val import: ServerImportForm? = null,
    val importing: Boolean = false,
    val failure: ActionFailure? = null,
    /** A file could not be written, read or picked. */
    val fileFailed: Boolean = false,
    val message: ServerBackupMessage? = null,
) {
    val busy: Boolean get() = downloading || importing
}
