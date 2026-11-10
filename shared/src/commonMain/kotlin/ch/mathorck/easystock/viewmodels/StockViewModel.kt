package ch.mathorck.easystock.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.data.db.ArticleDao
import ch.mathorck.easystock.data.model.ArticleStock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StockUiState(
    val loading: Boolean = true,
    /** Articles matching the search query. */
    val items: List<ArticleStock> = emptyList(),
    /** Number of articles before filtering, to tell "no articles" from "no results". */
    val totalCount: Int = 0,
)

/** Keeps the articles whose name or SKU contains [query], ignoring case. A blank query keeps all. */
fun List<ArticleStock>.filterByQuery(query: String): List<ArticleStock> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { it.article.name.contains(q, ignoreCase = true) || it.article.sku.contains(q, ignoreCase = true) }
}

class StockViewModel(dao: ArticleDao) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val state: StateFlow<StockUiState> = combine(dao.observeStock(), _query) { all, q ->
        StockUiState(loading = false, items = all.filterByQuery(q), totalCount = all.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StockUiState())

    fun onQueryChange(query: String) {
        _query.value = query
    }
}
