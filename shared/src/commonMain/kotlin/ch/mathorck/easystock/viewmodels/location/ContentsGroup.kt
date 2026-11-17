package ch.mathorck.easystock.viewmodels.location

import ch.mathorck.easystock.data.model.location.Location

/** The articles directly at [location]. [path] is relative to the location shown ("" for itself). */
data class ContentsGroup(
    val location: Location,
    val path: String,
    val rows: List<ContentsArticleRow>,
)
