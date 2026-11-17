package ch.mathorck.easystock.viewmodels

import ch.mathorck.easystock.auth.AppPermissions
import ch.mathorck.easystock.auth.ArticleDetailGates
import ch.mathorck.easystock.data.db.ArticleFlagDao
import ch.mathorck.easystock.data.db.ArticleFlagWithAuthor
import ch.mathorck.easystock.data.db.MAX_FLAG_NOTE_LENGTH
import ch.mathorck.easystock.data.model.ArticleFlag
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/** Why a flag note cannot be saved. */
enum class FlagNoteError { EMPTY, TOO_LONG }

/** Null when [raw] is a valid note: 1 to [MAX_FLAG_NOTE_LENGTH] characters once trimmed. */
fun flagNoteError(raw: String): FlagNoteError? = when {
    raw.trim().isEmpty() -> FlagNoteError.EMPTY
    raw.trim().length > MAX_FLAG_NOTE_LENGTH -> FlagNoteError.TOO_LONG
    else -> null
}

/** An open manual flag ("Report a problem"). [author] is null in standalone mode or for an unknown user. */
data class ManualFlagRow(val id: String, val note: String, val createdAt: String, val author: String?)

internal fun ArticleFlagWithAuthor.toRow(timeZone: TimeZone) = ManualFlagRow(
    id = flag.id,
    note = flag.note,
    createdAt = formatDateTime(flag.createdAt, timeZone),
    author = authorName?.takeIf { it.isNotBlank() },
)

/** Open manual flags (not resolved), in the given order (the query sorts the oldest first). */
fun manualFlagRows(flags: List<ArticleFlagWithAuthor>, timeZone: TimeZone): List<ManualFlagRow> =
    flags.filter { it.flag.resolvedAt == null }.map { it.toRow(timeZone) }

/** One card of the open flags: a manual flag or an "info to fix" flag from a review. */
sealed interface FlagCard {
    /** Unique among the cards, for the list keys. */
    val key: String

    data class Review(val row: InfoFlagRow) : FlagCard {
        override val key: String get() = "info-flag-${row.lineId}"
    }

    data class Manual(val row: ManualFlagRow) : FlagCard {
        override val key: String get() = "problem-${row.id}"
    }
}

/**
 * The cards of both flag sources, in one list: review flags first, then manual flags. Each source is shown only
 * when its gate is open ([ArticleDetailGates.infoFlags], [ArticleDetailGates.problems]).
 */
fun flagCards(infoFlags: List<InfoFlagRow>, manual: List<ManualFlagRow>, gates: ArticleDetailGates): List<FlagCard> =
    (if (gates.infoFlags) infoFlags.map(FlagCard::Review) else emptyList()) +
        (if (gates.problems) manual.map(FlagCard::Manual) else emptyList())

/** What the ViewModel needs to write manual flags. */
interface ArticleFlagStore {
    suspend fun insert(flag: ArticleFlag)

    /** Rows changed: 0 when unknown, already resolved or deleted. */
    suspend fun resolve(id: String, now: Long): Int
}

fun ArticleFlagDao.asStore(): ArticleFlagStore = object : ArticleFlagStore {
    override suspend fun insert(flag: ArticleFlag) = this@asStore.insert(flag)

    override suspend fun resolve(id: String, now: Long): Int = this@asStore.resolve(id, now)
}

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
