package ch.mathorck.easystock.services.push

import ch.mathorck.easystock.data.dto.push.PushRecord
import kotlinx.coroutines.flow.StateFlow

/** Where [PushRecord] is kept. */
interface PushStore {
    val record: StateFlow<PushRecord>

    suspend fun save(record: PushRecord)
}
