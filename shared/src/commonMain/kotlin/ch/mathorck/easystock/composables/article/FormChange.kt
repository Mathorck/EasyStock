package ch.mathorck.easystock.composables.article

import ch.mathorck.easystock.viewmodels.article.ArticleForm

internal typealias FormChange = ((ArticleForm) -> ArticleForm) -> Unit
