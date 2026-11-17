package ch.mathorck.easystock.viewmodels.stock

import ch.mathorck.easystock.data.dao.article.SkuOwner
import ch.mathorck.easystock.viewmodels.article.foldCase

/**
 * SKUs of all articles (soft-deleted ones included) indexed ignoring case, built once per database change
 * so the check on every keystroke is a lookup.
 */
class SkuIndex(owners: List<SkuOwner>) {
    private val ids: Map<String, List<String>> = owners
        .mapNotNull { owner -> owner.sku?.let { it.foldCase() to owner.id } }
        .groupBy({ it.first }, { it.second })

    /** True when an article other than [selfId] has [sku], ignoring case. */
    fun isTaken(sku: String, selfId: String?): Boolean = ids[sku.foldCase()]?.any { it != selfId } == true
}
