package ch.mathorck.easystock.services.sync

import kotlin.concurrent.Volatile

class InMemoryDatasetEpochStore : DatasetEpochStore {
    @Volatile
    private var epoch: String? = null

    override suspend fun load(): String? = epoch

    override suspend fun save(epoch: String?) {
        this.epoch = epoch
    }
}
