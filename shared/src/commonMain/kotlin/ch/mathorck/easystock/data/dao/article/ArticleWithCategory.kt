package ch.mathorck.easystock.data.dao.article

import androidx.room.ColumnInfo
import androidx.room.Embedded
import ch.mathorck.easystock.data.model.article.Article

/** An article with the name of its category (null without category). */
data class ArticleWithCategory(
    @Embedded val article: Article,
    @ColumnInfo(name = "category_name") val categoryName: String?,
)
