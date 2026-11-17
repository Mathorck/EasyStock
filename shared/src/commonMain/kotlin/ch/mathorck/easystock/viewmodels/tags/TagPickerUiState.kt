package ch.mathorck.easystock.viewmodels.tags

import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.enums.tags.TagPickerType
import ch.mathorck.easystock.viewmodels.location.LocationOption

/** The picker of articles or locations without a tag, to link the unknown tag. */
data class TagPickerUiState(
    val type: TagPickerType = TagPickerType.ARTICLE,
    val query: String = "",
    /** Untagged articles matching [query], for [TagPickerType.ARTICLE]. Null while loading. */
    val articles: List<Article>? = null,
    /** The properties line of each article of [articles] that has properties, by article id ("Longueur 50 m · ..."). */
    val attributeLines: Map<String, String> = emptyMap(),
    /** Untagged locations matching [query], for [TagPickerType.LOCATION]. Null while loading. */
    val locations: List<LocationOption>? = null,
    /** True when nothing at all is untagged (as opposed to none matching the query). */
    val noneUntagged: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    /** Still loading the list of its [type]. */
    val loading: Boolean get() = if (type == TagPickerType.ARTICLE) articles == null else locations == null
}
