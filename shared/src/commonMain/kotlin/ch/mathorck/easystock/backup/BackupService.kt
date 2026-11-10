package ch.mathorck.easystock.backup

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

enum class ImportMode {
    /** Wipes this phone and restores the backup. */
    REPLACE,

    /** Adds the backup to this phone, newer rows win. */
    MERGE,
}

/** What an import changed. Counts per table; photos copied from the backup. */
data class ImportSummary(
    val mode: ImportMode,
    val added: Map<BackupTable, Int>,
    val updated: Map<BackupTable, Int>,
    val photos: Int,
    val conflicts: List<MergeConflict>,
) {
    val totalAdded: Int get() = added.values.sum()
    val totalUpdated: Int get() = updated.values.sum()
}

fun BackupData.rowCount(table: BackupTable): Int = when (table) {
    BackupTable.CATEGORY -> categories.size
    BackupTable.ATTRIBUTE_DEFINITION -> attributeDefinitions.size
    BackupTable.CATEGORY_ATTRIBUTE -> categoryAttributes.size
    BackupTable.LOCATION -> locations.size
    BackupTable.ARTICLE -> articles.size
    BackupTable.ITEM -> items.size
    BackupTable.STOCK_MOVEMENT -> movements.size
    BackupTable.STOCK_REVIEW -> reviews.size
    BackupTable.STOCK_REVIEW_LINE -> reviewLines.size
}

private fun BackupData.perTable(): Map<BackupTable, Int> =
    BackupTable.entries.associateWith { rowCount(it) }.filterValues { it > 0 }

fun replaceSummary(data: BackupData, photos: Int): ImportSummary =
    ImportSummary(ImportMode.REPLACE, data.perTable(), emptyMap(), photos, emptyList())

/** The item location cache fixes are not counted: they are not edits. */
fun MergePlan.summary(photos: Int): ImportSummary =
    ImportSummary(ImportMode.MERGE, inserts.perTable(), updates.perTable(), photos, conflicts)

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

/** A backup to import, before it is opened. */
sealed interface ImportInput {
    /** A file copied to the app cache (opened file or Wi-Fi download). */
    class File(val path: String) : ImportInput

    /** A scanned data QR code. */
    class Qr(val payload: ByteArray) : ImportInput
}

/** A decrypted and checked backup, ready to import. Release with [BackupService.discard]. */
class OpenedBackup(
    val manifest: BackupManifest,
    val data: BackupData,
    /** Photos of the backup, available to copy. */
    val photos: Int,
    /** Platform handle of the decrypted archive, null for a QR code. */
    internal val archivePath: String?,
)

sealed interface WifiState {
    /** Waiting for the other phone to scan the QR code. */
    data object Waiting : WifiState

    data class Sending(val sentBytes: Long, val totalBytes: Long) : WifiState

    data object Done : WifiState

    /** Nobody connected in time: the QR code is no longer valid. */
    data object Expired : WifiState

    /** Stopped after too many wrong attempts. */
    data object Blocked : WifiState

    data class Failed(val error: BackupError) : WifiState
}

/** A running Wi-Fi transfer. Single download, closed on [stop], once done or after the timeout. */
interface WifiSender {
    val ticket: WifiTicket
    val state: StateFlow<WifiState>

    fun stop()
}

/**
 * Builds and reads backups, platform part. Every method throws [BackupException] on a known
 * failure. Nothing leaves the phone, except through [startWifi] (local network only).
 */
interface BackupService {
    /** Writes a backup of all data (all rows, photos) to a temporary file. */
    suspend fun export(password: CharArray?): ExportedBackup

    /** Shares [export] over the local network: the receiver scans the QR code of [WifiSender.ticket]. */
    suspend fun startWifi(export: ExportedBackup): WifiSender

    /** Downloads a backup from another phone on the same network. [onProgress]: bytes received. */
    suspend fun download(ticket: WifiTicket, onProgress: (Long) -> Unit): ImportInput.File

    /** True when [input] is protected by a password. */
    suspend fun needsPassword(input: ImportInput): Boolean

    /** Decrypts (if needed), reads and checks [input]. */
    suspend fun open(input: ImportInput, password: CharArray?): OpenedBackup

    /** Writes [backup] to the database and copies its photos, in one transaction. */
    suspend fun import(backup: OpenedBackup, mode: ImportMode): ImportSummary

    /** Deletes the temporary files. Safe to call more than once. */
    fun discard(export: ExportedBackup)

    fun discard(input: ImportInput)

    fun discard(backup: OpenedBackup)
}

@Composable
expect fun rememberBackupService(): BackupService
