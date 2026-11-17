package ch.mathorck.easystock.services.backup

import androidx.compose.runtime.Composable
import ch.mathorck.easystock.data.dto.backup.BackupData
import ch.mathorck.easystock.data.dto.backup.WifiTicket
import ch.mathorck.easystock.enums.backup.BackupTable
import ch.mathorck.easystock.enums.backup.ImportMode

fun BackupData.rowCount(table: BackupTable): Int = when (table) {
    BackupTable.CATEGORY -> categories.size
    BackupTable.ATTRIBUTE_DEFINITION -> attributeDefinitions.size
    BackupTable.CATEGORY_ATTRIBUTE -> categoryAttributes.size
    BackupTable.LOCATION -> locations.size
    BackupTable.ARTICLE -> articles.size
    BackupTable.ARTICLE_FLAG -> articleFlags.size
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
