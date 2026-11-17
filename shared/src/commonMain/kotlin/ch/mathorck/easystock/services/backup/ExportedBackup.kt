package ch.mathorck.easystock.services.backup

import ch.mathorck.easystock.data.dto.backup.BackupManifest

/** A backup built for export, in a temporary file until [BackupService.discard]. */
class ExportedBackup(
    /** Platform path of the temporary file. */
    val path: String,
    /** Suggested name when saving or sharing. */
    val fileName: String,
    val sizeBytes: Long,
    val manifest: BackupManifest,
    val photos: Int,
    val encrypted: Boolean,
    /** Text of a data QR code, or [QrDecision.TooLarge]. */
    val qr: QrDecision,
)
