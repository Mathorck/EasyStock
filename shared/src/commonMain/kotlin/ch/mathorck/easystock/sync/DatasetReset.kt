package ch.mathorck.easystock.sync

import kotlin.concurrent.Volatile

/**
 * The `dataset_epoch` of the server data this phone holds (server plan §1.2 #21): a UUID the server
 * changes when it replaces its whole data set (replace import of a backup). The phone sends it back
 * with the sync calls (`X-EasyStock-Dataset-Epoch`); a server with another epoch answers 409
 * `dataset-reset`, and the phone wipes its server data and pulls everything again.
 *
 * Kept outside the database (no schema change for it yet): written before the first page of a full
 * pull is saved, and only read when `sync_state.since > 0`, so a stale value (crash, an older
 * connection) is never sent with a full pull.
 */
interface DatasetEpochStore {
    suspend fun load(): String?

    /** Null forgets it. */
    suspend fun save(epoch: String?)
}

class InMemoryDatasetEpochStore : DatasetEpochStore {
    @Volatile
    private var epoch: String? = null

    override suspend fun load(): String? = epoch

    override suspend fun save(epoch: String?) {
        this.epoch = epoch
    }
}

/**
 * The server replaced its data set: the phone erased its copy and pulled the new one.
 * [lostChanges]: local changes that were not sent yet, lost with the wipe.
 * [backupFile]: name of the automatic backup of the phone written before the wipe ([AutoBackups]), null
 * when none was written. [backupFailed]: the backup was tried and failed (the reset went on anyway).
 */
data class DatasetResetNotice(
    val lostChanges: Int,
    val backupFile: String? = null,
    val backupFailed: Boolean = false,
) {
    /** Text kept by a [ResetNoticeStore] until the user dismisses the notice. */
    fun encode(): String = listOf(lostChanges.toString(), if (backupFailed) "1" else "0", backupFile.orEmpty()).joinToString("\n")

    companion object {
        /** Opposite of [encode]. Null for nothing stored or text that is not a notice. */
        fun decode(text: String?): DatasetResetNotice? {
            val parts = text?.split("\n") ?: return null
            if (parts.size != 3) return null
            val lost = parts[0].toIntOrNull()?.takeIf { it >= 0 } ?: return null
            val failed = when (parts[1]) {
                "1" -> true
                "0" -> false
                else -> return null
            }
            return DatasetResetNotice(lost, parts[2].ifEmpty { null }, failed)
        }
    }
}

/**
 * Keeps the [DatasetResetNotice] across an app restart (a reset in a background cycle is seen later),
 * until the user dismisses it. Outside the database (no schema change).
 */
interface ResetNoticeStore {
    suspend fun load(): String?

    /** Null forgets it. */
    suspend fun save(text: String?)
}

class InMemoryResetNoticeStore : ResetNoticeStore {
    @Volatile
    private var text: String? = null

    override suspend fun load(): String? = text

    override suspend fun save(text: String?) {
        this.text = text
    }
}

/** The automatic backups written before a reset wipe: only the newest [KEEP] stay. */
object AutoBackups {
    const val KEEP = 3
    const val PREFIX = "auto-"

    /** Of [names] (file names starting with [PREFIX], then a sortable UTC time stamp), those to delete. */
    fun toDelete(names: List<String>, keep: Int = KEEP): List<String> =
        names.filter { it.startsWith(PREFIX) }.sortedDescending().drop(keep)
}

/** Header of the sync calls with the [DatasetEpochStore] value. */
const val DATASET_EPOCH_HEADER = "X-EasyStock-Dataset-Epoch"

/** A pull answer with another epoch than the one this phone holds: the server's data set was replaced. */
internal class DatasetEpochChanged : Exception()
