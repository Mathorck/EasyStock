package ch.mathorck.easystock.viewmodels.location

import ch.mathorck.easystock.data.model.location.Location

data class LocationContentsUiState(
    val loading: Boolean = true,
    /** The location shown, null when it is deleted (or never existed). */
    val location: Location? = null,
    /** Full path of [location], "Room › Shelf". */
    val path: String = "",
    val includeSubLocations: Boolean = true,
    /** The location first, then its descendants in tree order. Only groups with articles. */
    val groups: List<ContentsGroup> = emptyList(),
    val subLocations: List<ContentsSubLocation> = emptyList(),
    /** Can go back to the parent location opened before. */
    val canGoBack: Boolean = false,
) {
    val isEmpty: Boolean get() = groups.isEmpty()
    val articleCount: Int get() = groups.sumOf { group -> group.rows.size }
}
