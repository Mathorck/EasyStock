package ch.mathorck.easystock.viewmodels

import ch.mathorck.easystock.auth.AppPermissions
import ch.mathorck.easystock.auth.ArticleDetailGates
import ch.mathorck.easystock.auth.Role
import ch.mathorck.easystock.data.db.ArticleFlagWithAuthor
import ch.mathorck.easystock.data.db.InfoFlag
import ch.mathorck.easystock.data.db.MAX_FLAG_NOTE_LENGTH
import ch.mathorck.easystock.data.db.MovementWithLocation
import ch.mathorck.easystock.data.model.ArticleFlag
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.StockMovement
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ArticleFlagTest {
    private val utc = TimeZone.UTC

    private fun gates(role: Role?) = ArticleDetailGates.of(AppPermissions.connectedAs(role))

    // ---------- Gates ----------

    @Test
    fun test_gates_standalone_everythingAllowed() {
        val g = ArticleDetailGates.of(AppPermissions.Standalone)
        assertTrue(g.reportProblem)
        assertTrue(g.problems)
        assertTrue(g.markProblemFixed)
    }

    @Test
    fun test_gates_admin_reportSeeAndFix() {
        val g = gates(Role.ADMIN)
        assertTrue(g.reportProblem && g.problems && g.markProblemFixed)
    }

    @Test
    fun test_gates_editor_reportSeeAndFix() {
        val g = gates(Role.EDITOR)
        assertTrue(g.reportProblem && g.problems && g.markProblemFixed)
    }

    @Test
    fun test_gates_viewer_reportAndSeeButNeverFix() {
        val g = gates(Role.VIEWER)
        assertTrue(g.reportProblem)
        assertTrue(g.problems)
        assertFalse(g.markProblemFixed)
    }

    @Test
    fun test_gates_guest_nothing() {
        val g = gates(Role.GUEST)
        assertFalse(g.reportProblem)
        assertFalse(g.problems)
        assertFalse(g.markProblemFixed)
    }

    @Test
    fun test_gates_unknownRole_behavesLikeGuest() {
        val g = gates(null)
        assertFalse(g.reportProblem || g.problems || g.markProblemFixed)
    }

    @Test
    fun test_gates_viewer_seesManualFlagsAndKeepsReviewGate() {
        val g = gates(Role.VIEWER)
        assertTrue(g.infoFlags)
        assertTrue(g.problems)
    }

    // ---------- Note validation ----------

    @Test
    fun test_noteError_blank_empty() {
        assertEquals(FlagNoteError.EMPTY, flagNoteError(""))
        assertEquals(FlagNoteError.EMPTY, flagNoteError("   \n\t "))
    }

    @Test
    fun test_noteError_boundaryLengths() {
        assertNull(flagNoteError("a"))
        assertNull(flagNoteError("a".repeat(MAX_FLAG_NOTE_LENGTH)))
        assertEquals(FlagNoteError.TOO_LONG, flagNoteError("a".repeat(MAX_FLAG_NOTE_LENGTH + 1)))
    }

    @Test
    fun test_noteError_lengthIsMeasuredAfterTrim() {
        assertNull(flagNoteError("  " + "a".repeat(MAX_FLAG_NOTE_LENGTH) + "  "))
    }

    // ---------- Actions ----------

    private class FakeStore : ArticleFlagStore {
        val inserted = mutableListOf<ArticleFlag>()
        val resolved = mutableListOf<Pair<String, Long>>()
        var resolveResult = 1

        override suspend fun insert(flag: ArticleFlag) {
            inserted += flag
        }

        override suspend fun resolve(id: String, now: Long): Int {
            resolved += id to now
            return resolveResult
        }
    }

    private class FixedClock(private val millis: Long) : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(millis)
    }

    private fun actions(store: FakeStore, permissions: AppPermissions) =
        ArticleFlagActions(store, { permissions }, FixedClock(5_000), { "new-id" })

    @Test
    fun test_report_allowed_insertsTrimmedFlagWithoutAuthor() = runTest {
        val store = FakeStore()

        val ok = actions(store, AppPermissions.connectedAs(Role.VIEWER)).report("a1", "  Cracked lid \n")

        assertTrue(ok)
        val flag = store.inserted.single()
        assertEquals("new-id", flag.id)
        assertEquals("a1", flag.articleId)
        assertEquals("Cracked lid", flag.note)
        assertEquals(5_000, flag.createdAt)
        assertEquals(5_000, flag.updatedAt)
        assertNull(flag.createdBy)
        assertNull(flag.resolvedAt)
        assertNull(flag.deletedAt)
    }

    @Test
    fun test_report_standalone_allowed() = runTest {
        val store = FakeStore()
        assertTrue(actions(store, AppPermissions.Standalone).report("a1", "x"))
        assertEquals(1, store.inserted.size)
    }

    @Test
    fun test_report_guest_refusedAndNothingWritten() = runTest {
        val store = FakeStore()
        assertFalse(actions(store, AppPermissions.connectedAs(Role.GUEST)).report("a1", "x"))
        assertTrue(store.inserted.isEmpty())
    }

    @Test
    fun test_report_invalidNote_refusedAndNothingWritten() = runTest {
        val store = FakeStore()
        val a = actions(store, AppPermissions.Standalone)
        assertFalse(a.report("a1", "   "))
        assertFalse(a.report("a1", "a".repeat(MAX_FLAG_NOTE_LENGTH + 1)))
        assertTrue(store.inserted.isEmpty())
    }

    @Test
    fun test_resolve_editor_callsStoreWithNow() = runTest {
        val store = FakeStore()
        assertTrue(actions(store, AppPermissions.connectedAs(Role.EDITOR)).resolve("f1"))
        assertEquals(listOf("f1" to 5_000L), store.resolved)
    }

    @Test
    fun test_resolve_standalone_allowed() = runTest {
        val store = FakeStore()
        assertTrue(actions(store, AppPermissions.Standalone).resolve("f1"))
    }

    @Test
    fun test_resolve_viewer_neverQueued() = runTest {
        val store = FakeStore()
        assertFalse(actions(store, AppPermissions.connectedAs(Role.VIEWER)).resolve("f1"))
        assertFalse(actions(store, AppPermissions.connectedAs(Role.GUEST)).resolve("f1"))
        assertTrue(store.resolved.isEmpty())
    }

    @Test
    fun test_resolve_nothingChanged_false() = runTest {
        val store = FakeStore().apply { resolveResult = 0 }
        assertFalse(actions(store, AppPermissions.Standalone).resolve("f1"))
    }

    @Test
    fun test_actions_permissionsReadAtCallTime() = runTest {
        val store = FakeStore()
        var permissions = AppPermissions.connectedAs(Role.EDITOR)
        val a = ArticleFlagActions(store, { permissions }, FixedClock(1), { "id" })
        permissions = AppPermissions.connectedAs(Role.VIEWER)
        assertFalse(a.resolve("f1"))
    }

    // ---------- Rows, merge, history ----------

    private fun flag(
        id: String,
        note: String = "Broken $id",
        createdAt: Long = 1_000,
        resolvedAt: Long? = null,
        author: String? = null,
        createdBy: String? = null,
    ) = ArticleFlagWithAuthor(
        ArticleFlag(
            id = id, articleId = "a1", note = note, createdAt = createdAt, createdBy = createdBy,
            resolvedAt = resolvedAt, updatedAt = createdAt,
        ),
        author,
    )

    @Test
    fun test_manualRows_openOnly_withNoteDateAndAuthor() {
        val rows = manualFlagRows(
            listOf(flag("f1", createdAt = 0, author = "Alice"), flag("f2", resolvedAt = 5), flag("f3", author = " ")),
            utc,
        )

        assertEquals(listOf("f1", "f3"), rows.map { it.id })
        assertEquals(ManualFlagRow("f1", "Broken f1", "1970-01-01 00:00", "Alice"), rows[0])
        assertNull(rows[1].author)
    }

    @Test
    fun test_manualRows_standalone_noAuthor() {
        assertNull(manualFlagRows(listOf(flag("f1")), utc).single().author)
    }

    private val review = InfoFlag("line1", "Wrong unit", "van", "Van", null, 2_000, null)
    private val reviewRow = InfoFlagRow("line1", "Wrong unit", "Van", null, "1970-01-01 00:00")
    private val manual = ManualFlagRow("f1", "Broken f1", "1970-01-01 00:00", null)

    @Test
    fun test_flagCards_reviewFlagsThenManual_whenBothGatesOpen() {
        val cards = flagCards(listOf(reviewRow), listOf(manual), ArticleDetailGates.of(AppPermissions.Standalone))

        assertEquals(listOf("info-flag-line1", "problem-f1"), cards.map { it.key })
        assertTrue(cards[0] is FlagCard.Review)
        assertTrue(cards[1] is FlagCard.Manual)
    }

    @Test
    fun test_flagCards_viewer_seesBoth() {
        val cards = flagCards(listOf(reviewRow), listOf(manual), gates(Role.VIEWER))
        assertEquals(2, cards.size)
    }

    @Test
    fun test_flagCards_guest_none() {
        assertTrue(flagCards(listOf(reviewRow), listOf(manual), gates(Role.GUEST)).isEmpty())
    }

    @Test
    fun test_flagCards_reviewGateClosedButManualOpen_onlyManual() {
        val g = ArticleDetailGates.of(AppPermissions.Standalone).copy(infoFlags = false)
        assertEquals(listOf("problem-f1"), flagCards(listOf(reviewRow), listOf(manual), g).map { it.key })
    }

    private val locations = listOf(Location(id = "van", name = "Van", parentId = null, createdAt = 0, updatedAt = 0))

    private fun movement(occurredAt: Long) = MovementWithLocation(
        StockMovement(
            id = "m$occurredAt", articleId = "a1", locationId = "van", quantityMilli = 1_000,
            reason = MovementReason.CONSUMPTION, occurredAt = occurredAt, createdAt = occurredAt,
        ),
        "Van",
    )

    @Test
    fun test_history_manualFlagGivesReportedThenFixedEntries() {
        val rows = historyRows(
            movements = listOf(movement(1_500)),
            flags = emptyList(),
            locations = locations,
            timeZone = utc,
            manualFlags = listOf(flag("f1", createdAt = 1_000, resolvedAt = 2_000, author = "Alice"), flag("f2", createdAt = 3_000)),
        )

        assertEquals(
            listOf("problem-reported-f2", "problem-fixed-f1", "movement-m1500", "problem-reported-f1"),
            rows.map { it.key },
        )
        val fixed = rows[1] as HistoryRow.ProblemFixed
        assertEquals("Broken f1", fixed.flag.note)
        assertEquals("Alice", fixed.flag.author)
        assertEquals("1970-01-01 00:00", fixed.at)
    }

    @Test
    fun test_history_mixesReviewAndManualFlagsNewestFirst() {
        val rows = historyRows(
            movements = emptyList(),
            flags = listOf(review),
            locations = locations,
            timeZone = utc,
            manualFlags = listOf(flag("f1", createdAt = 1_000), flag("f9", createdAt = 4_000)),
        )

        assertEquals(listOf("problem-reported-f9", "flagged-line1", "problem-reported-f1"), rows.map { it.key })
    }

    @Test
    fun test_history_partial_dropsManualEventsOlderThanOldestMovement() {
        val rows = historyRows(
            movements = listOf(movement(2_000)),
            flags = emptyList(),
            locations = locations,
            timeZone = utc,
            moreMovements = true,
            manualFlags = listOf(flag("old", createdAt = 1_000), flag("new", createdAt = 3_000)),
        )

        assertEquals(listOf("problem-reported-new", "movement-m2000"), rows.map { it.key })
    }

    @Test
    fun test_history_withoutManualFlags_unchanged() {
        val rows = historyRows(listOf(movement(1_000)), emptyList(), locations, utc)
        assertEquals(listOf("movement-m1000"), rows.map { it.key })
    }
}
