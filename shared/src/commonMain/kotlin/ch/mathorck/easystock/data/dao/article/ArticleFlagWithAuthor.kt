package ch.mathorck.easystock.data.dao.article

import androidx.room.ColumnInfo
import androidx.room.Embedded
import ch.mathorck.easystock.data.model.article.ArticleFlag

/** A flag with the display name of its author (null in standalone mode, or when the user is not in `server_user`). */
data class ArticleFlagWithAuthor(
    @Embedded val flag: ArticleFlag,
    @ColumnInfo(name = "author_name") val authorName: String? = null,
)
