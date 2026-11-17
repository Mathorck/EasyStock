package ch.mathorck.easystock.services.backup

import ch.mathorck.easystock.data.dto.backup.BackupData
import ch.mathorck.easystock.data.dto.backup.BackupManifest
import ch.mathorck.easystock.data.dto.backup.WifiTicket
import ch.mathorck.easystock.enums.backup.BackupError
import ch.mathorck.easystock.enums.backup.ImportMode
import ch.mathorck.easystock.exceptions.backup.BackupException
import kotlinx.coroutines.CompletableDeferred

internal class FakeBackupService : BackupService {
    val sender = FakeSender()
    var failImport = false

    /** When set, [import] waits for it. */
    var importGate: CompletableDeferred<Unit>? = null
    var discards = 0

    private val manifest = BackupManifest(appVersion = "test", createdAt = "2026-11-10T00:00:00Z")
    private val summary = ImportSummary(ImportMode.MERGE, emptyMap(), emptyMap(), 0, emptyList())

    override suspend fun export(password: CharArray?) =
        ExportedBackup("/tmp/b", "b.zip", 1, manifest, 0, false, QrDecision.TooLarge)

    override suspend fun startWifi(export: ExportedBackup): WifiSender = sender

    override suspend fun download(ticket: WifiTicket, onProgress: (Long) -> Unit): ImportInput.File =
        ImportInput.File("/tmp/d")

    override suspend fun needsPassword(input: ImportInput) = false

    override suspend fun open(input: ImportInput, password: CharArray?) =
        OpenedBackup(manifest, BackupData(), 0, null)

    override suspend fun import(backup: OpenedBackup, mode: ImportMode): ImportSummary {
        importGate?.await()
        if (failImport) throw BackupException(BackupError.FAILED)
        return summary
    }

    override fun discard(export: ExportedBackup) {
        discards++
    }

    override fun discard(input: ImportInput) {
        discards++
    }

    override fun discard(backup: OpenedBackup) {
        discards++
    }
}
