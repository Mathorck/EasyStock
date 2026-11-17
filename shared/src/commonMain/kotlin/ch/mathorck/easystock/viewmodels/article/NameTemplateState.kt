package ch.mathorck.easystock.viewmodels.article

import ch.mathorck.easystock.services.attributes.NameTemplateError

// ---------- Name template (pure) ----------

/** Name template input of the category screen, checked against the attributes of the draft. */
data class NameTemplateState(
    /** As typed, or the stored template while not edited (empty = no template). */
    val text: String = "",
    val error: NameTemplateError? = null,
    /** Name built from the template for the [previewSource]; null without (valid) template. */
    val preview: String? = null,
    val previewSource: PreviewSource = PreviewSource.SAMPLE,
    /** Active articles of the category with a typed name, they don't follow the template. */
    val manualNameCount: Int = 0,
    /** Save also makes those articles follow the template. */
    val applyToAll: Boolean = false,
) {
    enum class PreviewSource { ARTICLE, SAMPLE }
}
