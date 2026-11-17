package ch.mathorck.easystock.services.alerts

/** Where a tapped notification leads. */
sealed interface AlertTarget {
    data class Article(val articleId: String) : AlertTarget

    /** The Stock page filtered on "low stock" (grouped notification). */
    data object LowStock : AlertTarget
}
