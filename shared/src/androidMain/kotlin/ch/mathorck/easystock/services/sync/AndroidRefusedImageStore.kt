package ch.mathorck.easystock.services.sync

import android.content.Context
import ch.mathorck.easystock.services.profiles.activeProfilePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [RefusedImageStore] in the preferences file of [AndroidDatasetEpochStore], under its own key. */
internal class AndroidRefusedImageStore(context: Context) : RefusedImageStore {
    private val prefs = context.getSharedPreferences(activeProfilePaths.datasetEpochPrefsName, Context.MODE_PRIVATE)

    override suspend fun load(): String? = withContext(Dispatchers.IO) { prefs.getString(KEY, null) }

    override suspend fun save(value: String?) = withContext(Dispatchers.IO) {
        prefs.edit().apply { if (value == null) remove(KEY) else putString(KEY, value) }.commit()
        Unit
    }

    private companion object {
        const val KEY = "refused_images"
    }
}
