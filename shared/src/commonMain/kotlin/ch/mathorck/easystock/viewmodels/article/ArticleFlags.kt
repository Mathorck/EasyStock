package ch.mathorck.easystock.viewmodels.article

import ch.mathorck.easystock.data.dao.article.ArticleFlagDao
import ch.mathorck.easystock.data.dao.article.ArticleFlagWithAuthor
import ch.mathorck.easystock.data.dao.article.MAX_FLAG_NOTE_LENGTH
import ch.mathorck.easystock.data.model.article.ArticleFlag
import ch.mathorck.easystock.enums.article.FlagNoteError
import ch.mathorck.easystock.services.auth.ArticleDetailGates
import ch.mathorck.easystock.viewmodels.ArticleFlagStore
import kotlinx.datetime.TimeZone

/** Null when [raw] is a valid note: 1 to [MAX_FLAG_NOTE_LENGTH] characters once trimmed. */
fun flagNoteError(raw: String): FlagNoteError? = when {
    raw.trim().isEmpty() -> FlagNoteError.EMPTY
    raw.trim().length > MAX_FLAG_NOTE_LENGTH -> FlagNoteError.TOO_LONG
    else -> null
}

internal fun ArticleFlagWithAuthor.toRow(timeZone: TimeZone) = ManualFlagRow(
    id = flag.id,
    note = flag.note,
    createdAt = formatDateTime(flag.createdAt, timeZone),
    author = authorName?.takeIf { it.isNotBlank() },
)

/** Open manual flags (not resolved), in the given order (the query sorts the oldest first). */
fun manualFlagRows(flags: List<ArticleFlagWithAuthor>, timeZone: TimeZone): List<ManualFlagRow> =
    flags.filter { it.flag.resolvedAt == null }.map { it.toRow(timeZone) }

/**
 * The cards of both flag sources, in one list: review flags first, then manual flags. Each source is shown only
 * when its gate is open ([ArticleDetailGates.infoFlags], [ArticleDetailGates.problems]).
 */
fun flagCards(infoFlags: List<InfoFlagRow>, manual: List<ManualFlagRow>, gates: ArticleDetailGates): List<FlagCard> =
    (if (gates.infoFlags) infoFlags.map(FlagCard::Review) else emptyList()) +
        (if (gates.problems) manual.map(FlagCard::Manual) else emptyList())

fun ArticleFlagDao.asStore(): ArticleFlagStore = object : ArticleFlagStore {
    override suspend fun insert(flag: ArticleFlag) = this@asStore.insert(flag)

    override suspend fun resolve(id: String, now: Long): Int = this@asStore.resolve(id, now)
}
