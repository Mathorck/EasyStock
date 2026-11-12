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
 */
data class DatasetResetNotice(val lostChanges: Int)

/** Header of the sync calls with the [DatasetEpochStore] value. */
const val DATASET_EPOCH_HEADER = "X-EasyStock-Dataset-Epoch"

/** A pull answer with another epoch than the one this phone holds: the server's data set was replaced. */
internal class DatasetEpochChanged : Exception()
