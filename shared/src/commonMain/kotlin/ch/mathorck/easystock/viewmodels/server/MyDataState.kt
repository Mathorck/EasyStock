package ch.mathorck.easystock.viewmodels.server

import ch.mathorck.easystock.services.server.LocalFile

data class MyDataState(
    /** The summary of what the export holds, shown before the download. */
    val summary: Boolean = false,
    val downloading: Boolean = false,
    /** The downloaded export, to save or share. */
    val downloaded: LocalFile? = null,
    val error: MyDataError? = null,
    /** The file was saved where the user chose. */
    val saved: Boolean = false,
)
