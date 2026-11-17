package ch.mathorck.easystock.services.push

import android.util.Log
import ch.mathorck.easystock.data.dto.push.PushRecord
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** [PushRecord] as JSON in `noBackupFilesDir`: an Android backup never brings an old address back. */
internal class FilePushStore(private val file: File) : PushStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val _record = MutableStateFlow(load())
    override val record: StateFlow<PushRecord> = _record.asStateFlow()

    private fun load(): PushRecord = runCatching {
        if (file.isFile) json.decodeFromString(PushRecord.serializer(), file.readText()) else PushRecord()
    }.getOrDefault(PushRecord())

    override suspend fun save(record: PushRecord) {
        _record.value = record
        withContext(Dispatchers.IO) {
            if (record == PushRecord()) {
                file.delete()
            } else {
                val temp = File(file.parentFile, "${file.name}.tmp")
                temp.writeText(json.encodeToString(PushRecord.serializer(), record))
                if (!temp.renameTo(file)) Log.w(TAG, "Could not save the push state")
            }
        }
    }
}
