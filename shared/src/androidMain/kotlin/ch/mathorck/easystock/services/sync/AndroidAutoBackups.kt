package ch.mathorck.easystock.services.sync

import ch.mathorck.easystock.services.backup.BackupService
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes the automatic backup before a reset wipe: a normal backup (same format as a manual one, no
 * password) moved to [dir]. Only the newest [AutoBackups.KEEP] stay.
 */
class AndroidAutoBackups(private val dir: File, private val service: BackupService) {
    /** Returns the name of the new file. Throws when the backup fails. */
    suspend fun create(): String {
        val export = service.export(null)
        try {
            return withContext(Dispatchers.IO) {
                dir.mkdirs()
                val target = File(dir, AutoBackups.PREFIX + export.fileName)
                // Written under a name no listing sees, then renamed: never a half file with a backup name
                val part = File(dir, "${target.name}.part")
                File(export.path).copyTo(part, overwrite = true)
                if (!part.renameTo(target)) {
                    part.delete()
                    error("Could not store the backup")
                }
                prune()
                target.name
            }
        } finally {
            service.discard(export)
        }
    }

    fun prune() {
        val names = dir.list()?.toList().orEmpty()
        AutoBackups.toDelete(names).forEach { File(dir, it).delete() }
    }
}
