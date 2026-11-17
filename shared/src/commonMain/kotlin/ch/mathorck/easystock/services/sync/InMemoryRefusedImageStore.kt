package ch.mathorck.easystock.services.sync

import kotlin.concurrent.Volatile

class InMemoryRefusedImageStore : RefusedImageStore {
    @Volatile
    private var value: String? = null

    override suspend fun load(): String? = value

    override suspend fun save(value: String?) {
        this.value = value
    }
}
