package ch.mathorck.easystock.services.backup

import ch.mathorck.easystock.data.dto.backup.BackupData
import ch.mathorck.easystock.data.dto.backup.BackupManifest

/** A decrypted and checked backup, ready to import. Release with [BackupService.discard]. */
class OpenedBackup(
    val manifest: BackupManifest,
    val data: BackupData,
    /** Photos of the backup, available to copy. */
    val photos: Int,
    /** Platform handle of the decrypted archive, null for a QR code. */
    internal val archivePath: String?,
)
