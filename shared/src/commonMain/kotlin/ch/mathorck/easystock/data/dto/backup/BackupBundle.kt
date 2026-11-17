package ch.mathorck.easystock.data.dto.backup

import kotlinx.serialization.Serializable

/** Manifest and tables in one document, for a QR code (no photos). */
@Serializable
data class BackupBundle(val manifest: BackupManifest, val data: BackupData)
