package ch.mathorck.easystock.services.images

/**
 * Photo files written while a form is open, not saved yet.
 * [stored]: path saved on the article when the form opened (null for a new article).
 */
data class StagedImages(val stored: String? = null, val staged: Set<String> = emptySet()) {
    operator fun plus(path: String): StagedImages = copy(staged = staged + path)

    /**
     * Files to delete once [saved] is stored: the other staged files, and the [previous] photo
     * when it was replaced or removed.
     */
    fun toDeleteOnSave(saved: String?, previous: String? = stored): Set<String> =
        (staged + listOfNotNull(previous)) - setOfNotNull(saved)

    /** Files to delete when the form is closed without saving: the stored photo stays. */
    fun toDeleteOnCancel(): Set<String> = staged - setOfNotNull(stored)
}
