package ch.mathorck.easystock.prefs

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private class SharedPreferencesStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("easystock_prefs", Context.MODE_PRIVATE)
    override fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
}

@Composable
actual fun rememberAppPreferences(): AppPreferences {
    val context = LocalContext.current.applicationContext
    return remember(context) { AppPreferences(SharedPreferencesStore(context)) }
}
