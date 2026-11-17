package ch.mathorck.easystock.data.dao.tags

/** What an NFC tag can be linked to. */
sealed interface TagTarget {
    val id: String

    data class OfArticle(override val id: String) : TagTarget

    data class OfLocation(override val id: String) : TagTarget
}
