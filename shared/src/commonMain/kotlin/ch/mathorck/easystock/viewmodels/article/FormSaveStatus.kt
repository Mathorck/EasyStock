package ch.mathorck.easystock.viewmodels.article

/** Save progress of the article form, and the values the database refused. */
internal data class FormSaveStatus(
    val saving: Boolean = false,
    val failed: Boolean = false,
    val rejectedSku: String? = null,
    val rejectedCategoryName: String? = null,
)
