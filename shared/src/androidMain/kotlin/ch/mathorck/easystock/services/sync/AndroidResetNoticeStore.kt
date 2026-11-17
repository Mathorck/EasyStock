package ch.mathorck.easystock.services.sync

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [ResetNoticeStore] in a small private preferences file (counts and a file name, no personal data). */
internal class AndroidResetNoticeStore(context: Context) : ResetNoticeStore {
    private val prefs = context.getSharedPreferences(RESET_NOTICE_FILE, Context.MODE_PRIVATE)

    override suspend fun load(): String? = withContext(Dispatchers.IO) { prefs.getString(KEY, null) }

    override suspend fun save(text: String?) = withContext(Dispatchers.IO) {
        prefs.edit().apply { if (text == null) remove(KEY) else putString(KEY, text) }.commit()
        Unit
    }

    private companion object {
        const val KEY = "notice"
    }
}
