package ch.mathorck.easystock.services.profiles

import android.content.Context

/**
 * The registry in its own preferences file, outside every profile (excluded from Android backups like
 * every preference, see `data_extraction_rules.xml`). Written with `commit`: a restart follows a switch.
 */
internal class AndroidProfileRegistryStore(context: Context) : ProfileRegistryStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): String? = prefs.getString(KEY_REGISTRY, null)

    override fun save(json: String) {
        check(prefs.edit().putString(KEY_REGISTRY, json).commit()) { "Profile registry not saved" }
    }

    private companion object {
        const val PREFS_NAME = "profiles"
        const val KEY_REGISTRY = "registry"
    }
}
