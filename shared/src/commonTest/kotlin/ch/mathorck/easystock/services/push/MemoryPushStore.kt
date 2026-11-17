package ch.mathorck.easystock.services.push

import ch.mathorck.easystock.data.dto.push.PushRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class MemoryPushStore(initial: PushRecord = PushRecord()) : PushStore {
    private val _record = MutableStateFlow(initial)
    override val record: StateFlow<PushRecord> = _record

    override suspend fun save(record: PushRecord) {
        _record.value = record
    }
}
