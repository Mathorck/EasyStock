package ch.mathorck.easystock.viewmodels.backup

import ch.mathorck.easystock.services.backup.ExportedBackup
import ch.mathorck.easystock.services.backup.ImportSummary
import ch.mathorck.easystock.services.backup.OpenedBackup
import ch.mathorck.easystock.services.backup.WifiState
import kotlin.time.ComparableTimeMark

/** Where the backup screen is. */
sealed interface BackupStep {
    data object Home : BackupStep

    data object Exporting : BackupStep

    /** The backup is built: choose how to send it. */
    data class Exported(val export: ExportedBackup) : BackupStep

    data class ShowingQr(val export: ExportedBackup, val text: String) : BackupStep

    data class SendingWifi(
        val export: ExportedBackup,
        val qrText: String,
        val state: WifiState,
        /** When the code stops being valid. */
        val deadline: ComparableTimeMark,
    ) : BackupStep

    data object Scanning : BackupStep

    data class Downloading(val receivedBytes: Long) : BackupStep

    /** [wrong]: the last password did not open it. */
    data class PasswordNeeded(val wrong: Boolean = false) : BackupStep

    /** Opening or importing. */
    data object Working : BackupStep

    /** Read and checked: choose merge or replace. */
    data class Preview(val backup: OpenedBackup) : BackupStep

    data class Done(val summary: ImportSummary) : BackupStep
}
