package ch.mathorck.easystock.viewmodels.stock


/** One entry of a picker sheet. A null [id] is the "All" entry. */
internal data class PickerOption(
    val id: String?,
    val label: String,
    val depth: Int = 0,
    val icon: String? = null,
    val color: String? = null,
    /** True for location entries: show the location icon (the default one when [icon] is null). */
    val showIcon: Boolean = false,
)
