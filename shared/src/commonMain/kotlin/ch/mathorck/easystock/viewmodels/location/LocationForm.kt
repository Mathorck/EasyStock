package ch.mathorck.easystock.viewmodels.location

/** Raw input of the location dialog. [parentId] null = top level. */
data class LocationForm(
    val name: String = "",
    val parentId: String? = null,
    /** Key of LocationIcons, null = default. */
    val icon: String? = null,
    /** Key of LocationColors, null = default. */
    val color: String? = null,
)
