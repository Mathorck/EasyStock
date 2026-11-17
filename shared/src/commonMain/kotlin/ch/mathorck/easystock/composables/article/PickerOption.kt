package ch.mathorck.easystock.composables.article

internal data class PickerOption(
    val key: String,
    val text: String,
    val depth: Int = 0,
    val icon: String? = null,
    val color: String? = null,
    val showIcon: Boolean = false,
)
