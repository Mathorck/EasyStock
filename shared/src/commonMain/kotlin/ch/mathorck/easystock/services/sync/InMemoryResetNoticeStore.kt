package ch.mathorck.easystock.services.sync

import kotlin.concurrent.Volatile

class InMemoryResetNoticeStore : ResetNoticeStore {
    @Volatile
    private var text: String? = null

    override suspend fun load(): String? = text

    override suspend fun save(text: String?) {
        this.text = text
    }
}
