package ch.mathorck.easystock.services.prefs

/** Small key-value storage of the platform (Android SharedPreferences, iOS NSUserDefaults). */
interface KeyValueStore {
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
}
