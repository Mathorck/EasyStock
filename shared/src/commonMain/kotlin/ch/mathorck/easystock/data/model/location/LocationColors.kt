package ch.mathorck.easystock.data.model.location

/** Palette of `location.color`. Mid tones that stay readable on light and dark themes. */
object LocationColors {
    val keys: List<String> = listOf(
        "red", "pink", "purple", "indigo", "blue", "cyan",
        "teal", "green", "amber", "orange", "brown", "blue_grey",
    )

    /** The key if it is in the palette, otherwise null (default color). */
    fun normalize(key: String?): String? = key?.takeIf { it in keys }
}
