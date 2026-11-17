package ch.mathorck.easystock.viewmodels.server.admin

import ch.mathorck.easystock.enums.server.ServerImportMode
import ch.mathorck.easystock.services.server.LocalFile

/** The import being prepared: a picked file and how to load it. */
data class ServerImportForm(
    val file: LocalFile,
    val mode: ServerImportMode = ServerImportMode.MERGE,
    /** What the Admin typed to confirm a replace. */
    val typed: String = "",
)
