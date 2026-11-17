package ch.mathorck.easystock.viewmodels.stock

import ch.mathorck.easystock.data.dao.article.FoundCandidate

data class FoundSearchState(val query: String = "", val results: List<FoundCandidate> = emptyList(), val searching: Boolean = false)
