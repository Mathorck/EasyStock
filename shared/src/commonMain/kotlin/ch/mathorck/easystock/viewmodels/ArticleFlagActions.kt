package ch.mathorck.easystock.viewmodels

import ch.mathorck.easystock.data.model.article.ArticleFlag
import ch.mathorck.easystock.services.auth.AppPermissions
import ch.mathorck.easystock.services.auth.ArticleDetailGates
import ch.mathorck.easystock.viewmodels.article.flagNoteError
import kotlin.time.Clock

/**
 * Writes of the manual flags with their role guard: the UI hides the buttons, this refuses the write anyway,
 * so a Viewer can never queue a resolve. [permissions] is read at each call (the role may change).
 */
class ArticleFlagActions(
    private val store: ArticleFlagStore,
    private val permissions: () -> AppPermissions,
    private val clock: Clock,
    private val newId: () -> String,
) {
    /** Raises a flag with the trimmed [rawNote]. False (nothing written) when not allowed, or the note is invalid. */
    suspend fun report(articleId: String, rawNote: String): Boolean {
        val gates = ArticleDetailGates.of(permissions())
        if (!gates.reportProblem || flagNoteError(rawNote) != null) return false
        val now = clock.now().toEpochMilliseconds()
        store.insert(ArticleFlag(id = newId(), articleId = articleId, note = rawNote.trim(), createdAt = now, updatedAt = now))
        return true
    }

    /** Resolves an open flag. False (nothing written) when not allowed, or when nothing changed. */
    suspend fun resolve(flagId: String): Boolean {
        if (!ArticleDetailGates.of(permissions()).markProblemFixed) return false
        return store.resolve(flagId, clock.now().toEpochMilliseconds()) > 0
    }
}
