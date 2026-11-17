package ch.mathorck.easystock.exceptions.article

/** The article was deleted (or never existed) when saving. */
class ArticleNotFoundException(val id: String) : IllegalStateException("No active article: $id")
