package ch.mathorck.easystock.data.model

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

/** Palette of `location.color`. Mid tones that stay readable on light and dark themes. */
object LocationColors {
    val keys: List<String> = listOf(
        "red", "pink", "purple", "indigo", "blue", "cyan",
        "teal", "green", "amber", "orange", "brown", "blue_grey",
    )

    /** The key if it is in the palette, otherwise null (default color). */
    fun normalize(key: String?): String? = key?.takeIf { it in keys }
}
