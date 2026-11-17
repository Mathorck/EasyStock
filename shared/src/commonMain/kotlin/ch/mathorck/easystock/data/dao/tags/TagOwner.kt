package ch.mathorck.easystock.data.dao.tags

import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.data.model.location.Location

/** The active row holding a tag. */
sealed interface TagOwner {
    val target: TagTarget
    val name: String

    data class OfArticle(val article: Article) : TagOwner {
        override val target: TagTarget get() = TagTarget.OfArticle(article.id)
        override val name: String get() = article.name
    }

    data class OfLocation(val location: Location) : TagOwner {
        override val target: TagTarget get() = TagTarget.OfLocation(location.id)
        override val name: String get() = location.name
    }
}
