package ch.mathorck.easystock.composables.location

import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.viewmodels.article.StockActionForm

/** A stock action started from the contents: the sheet of [article], opened with [form]. */
internal data class ContentsAction(val article: Article, val form: StockActionForm)
