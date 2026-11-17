package ch.mathorck.easystock.services.prefs

/** In-memory store: previews and tests. */
class MemoryKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, Boolean>()
    override fun getBoolean(key: String, default: Boolean) = values[key] ?: default
    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }
}
