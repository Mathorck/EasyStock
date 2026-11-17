package ch.mathorck.easystock.data.model.location

/**
 * Icons a location can have, stored in `location.icon`. The keys are stable: never rename or reuse one.
 * The drawables are in the UI layer (`composables/LocationAppearance.kt`).
 */
object LocationIcons {
    val keys: List<String> = listOf(
        "warehouse", "shelf", "box", "crate", "cabinet", "workbench",
        "garage", "home", "door", "store", "fridge", "folder",
        "tools", "toolbox", "first_aid", "backpack", "car", "van",
    )

    /** The key if it is in the list, otherwise null (default icon). */
    fun normalize(key: String?): String? = key?.takeIf { it in keys }
}
