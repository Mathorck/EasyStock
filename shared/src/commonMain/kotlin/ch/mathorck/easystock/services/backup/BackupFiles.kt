package ch.mathorck.easystock.services.backup

/** Save, share and open backup files with the system pickers. */
interface BackupFiles {
    /** Lets the user choose where to save [export]. */
    fun save(export: ExportedBackup)

    /** Sends [export] to another app (mail, messenger, cloud) chosen by the user. */
    fun share(export: ExportedBackup)

    /** Lets the user pick a backup file; it is copied to the app cache before [onOpened]. */
    fun open()
}
