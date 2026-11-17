package ch.mathorck.easystock.enums.article

/**
 * Create: minimal form (type, name, category, unit). Edit: every field, the type is read-only.
 * Both use the same [ArticleForm] and [validate], hidden fields stay blank and are valid.
 */
enum class ArticleFormMode { CREATE, EDIT }
