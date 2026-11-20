package ch.mathorck.easystock.services.profiles

/** Where the registry is kept: outside every profile, written at once (a restart follows a switch). */
interface ProfileRegistryStore {
    fun load(): String?

    fun save(json: String)
}
