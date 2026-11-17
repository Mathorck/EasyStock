package ch.mathorck.easystock.services.sync

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [DatasetEpochStore] in a small private preferences file (not personal data: a server UUID). */
internal class AndroidDatasetEpochStore(context: Context) : DatasetEpochStore {
    private val prefs = context.getSharedPreferences(DATASET_EPOCH_FILE, Context.MODE_PRIVATE)

    override suspend fun load(): String? = withContext(Dispatchers.IO) { prefs.getString(KEY, null) }

    override suspend fun save(epoch: String?) = withContext(Dispatchers.IO) {
        // commit: on disk before the pulled rows are written
        prefs.edit().apply { if (epoch == null) remove(KEY) else putString(KEY, epoch) }.commit()
        Unit
    }

    private companion object {
        const val KEY = "dataset_epoch"
    }
}
