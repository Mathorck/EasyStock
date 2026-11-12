package ch.mathorck.easystock.prefs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults

private class UserDefaultsStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun getBoolean(key: String, default: Boolean) =
        if (defaults.objectForKey(key) == null) default else defaults.boolForKey(key)

    override fun putBoolean(key: String, value: Boolean) = defaults.setBool(value, forKey = key)
}

@Composable
actual fun rememberAppPreferences(): AppPreferences = remember { AppPreferences(UserDefaultsStore()) }
