package ch.mathorck.easystock.services.review

import ch.mathorck.easystock.data.dao.stock.UNIT_MILLI
import ch.mathorck.easystock.data.dao.stock.canMoveUnit
import ch.mathorck.easystock.data.model.article.PendingUnit
import ch.mathorck.easystock.data.model.review.StockReviewLine
import ch.mathorck.easystock.data.model.review.pendingUnit
import ch.mathorck.easystock.enums.article.ItemStatus
import ch.mathorck.easystock.enums.review.LineFilter
import ch.mathorck.easystock.enums.review.LineStatus
import ch.mathorck.easystock.enums.review.MissingResolution
import ch.mathorck.easystock.enums.review.ReviewResult
import ch.mathorck.easystock.enums.review.SkipReason
import kotlinx.serialization.json.JsonObject

/** A serial line is one unit (existing or [isPendingUnit]), a quantity line an article at a location. */
val StockReviewLine.isSerial: Boolean get() = itemId != null || pendingItem != null

/** A new unit found during the review: it is created when the review is applied. */
val StockReviewLine.isPendingUnit: Boolean get() = pendingItem != null

/**
 * A unit added by "found here" (it was not expected at this location). Resetting it removes the line.
 * Expected units always have [StockReviewLine.expectedMilli] = 1000.
 */
val StockReviewLine.isAddedUnit: Boolean get() = isSerial && expectedMilli == 0L

fun StockReviewLine.status(): LineStatus = when (result) {
    ReviewResult.UNCHECKED -> LineStatus.UNCHECKED
    ReviewResult.COUNTED -> if (countedMilli == expectedMilli) LineStatus.OK else LineStatus.DISCREPANCY
    ReviewResult.PRESENT -> if (expectedMilli > 0) LineStatus.OK else LineStatus.DISCREPANCY
    ReviewResult.MISSING, ReviewResult.ELSEWHERE -> LineStatus.DISCREPANCY
}

/** Results a user can set on each kind of line. */
fun allowedResults(serial: Boolean): Set<ReviewResult> =
    if (serial) {
        setOf(ReviewResult.UNCHECKED, ReviewResult.PRESENT, ReviewResult.MISSING, ReviewResult.ELSEWHERE)
    } else {
        setOf(ReviewResult.UNCHECKED, ReviewResult.COUNTED)
    }

fun List<StockReviewLine>.progress(): ReviewProgress {
    val statuses = map { it.status() }
    return ReviewProgress(
        total = size,
        checked = statuses.count { it != LineStatus.UNCHECKED },
        discrepancies = statuses.count { it == LineStatus.DISCREPANCY },
        flagged = count { it.infoFlagged },
    )
}

fun StockReviewLine.matches(filter: LineFilter): Boolean = when (filter) {
    LineFilter.ALL -> true
    LineFilter.UNCHECKED -> status() == LineStatus.UNCHECKED
    LineFilter.DISCREPANCIES -> status() == LineStatus.DISCREPANCY
    LineFilter.FLAGGED -> infoFlagged
}

/**
 * Adds a line for every position not in the review yet, and refreshes the expected quantity of the
 * unchecked quantity lines (stock moved since the line was created). Checked lines keep their snapshot:
 * the correction is relative to what was expected when counting.
 * A unit already in the review (at any location) gets no second line.
 */
fun syncReviewLines(
    reviewId: String,
    lines: List<StockReviewLine>,
    positions: List<ReviewPosition>,
    now: Long,
    newId: () -> String,
): LineSync {
    val quantityLines = lines.filter { !it.isSerial }.associateBy { it.articleId to it.locationId }
    val unitIds = lines.mapNotNullTo(HashSet()) { it.itemId }
    val stockByPosition = positions.filter { it.itemId == null }.associate { (it.articleId to it.locationId) to it.quantityMilli }
    val inserts = mutableListOf<StockReviewLine>()
    for (position in positions) {
        val itemId = position.itemId
        if (itemId != null) {
            if (!unitIds.add(itemId)) continue
            inserts += newLine(newId(), reviewId, position.articleId, itemId, position.locationId, UNIT_MILLI, now)
        } else if ((position.articleId to position.locationId) !in quantityLines) {
            inserts += newLine(newId(), reviewId, position.articleId, null, position.locationId, position.quantityMilli, now)
        }
    }
    val updates = quantityLines.values.mapNotNull { line ->
        val live = stockByPosition[line.articleId to line.locationId] ?: 0
        if (line.result == ReviewResult.UNCHECKED && line.expectedMilli != live) {
            line.copy(expectedMilli = live, updatedAt = now)
        } else {
            null
        }
    }
    return LineSync(inserts, updates)
}

fun newLine(
    id: String,
    reviewId: String,
    articleId: String,
    itemId: String?,
    locationId: String,
    expectedMilli: Long,
    now: Long,
    result: ReviewResult = ReviewResult.UNCHECKED,
    pendingItem: JsonObject? = null,
) = StockReviewLine(
    id = id,
    reviewId = reviewId,
    articleId = articleId,
    itemId = itemId,
    locationId = locationId,
    expectedMilli = expectedMilli,
    result = result,
    pendingItem = pendingItem,
    checkedAt = if (result == ReviewResult.UNCHECKED) null else now,
    createdAt = now,
    updatedAt = now,
)

/**
 * Plans the corrections of a review. Pure: nothing is written.
 *
 * Quantity line counted: delta = counted - expected (expected is the stock when it was counted), added to
 * the current stock so movements recorded after the count are kept. Never below 0 (then in [ReviewFinishPlan.clamped]).
 *
 * Serial line present where expected: nothing to do, unless the unit moved since (skipped, [SkipReason.CHANGED]).
 * Found elsewhere, or found here unexpectedly: the unit ends at that location (move, return, found).
 * Missing: [MissingResolution.LOSE] by default from [resolutions], or nothing with KEEP.
 * Each unit is handled once. Deleted articles, units or locations are skipped.
 */
fun planReviewFinish(inputs: FinishInputs, resolutions: Map<String, MissingResolution>): ReviewFinishPlan {
    val changes = mutableListOf<ReviewChange>()
    val skipped = mutableListOf<SkippedLine>()
    val drifted = mutableSetOf<String>()
    val clamped = mutableSetOf<String>()
    val handledUnits = mutableSetOf<String>()
    val usedSerials = inputs.takenSerials.toMutableSet()
    var unchecked = 0
    for (line in inputs.lines.sortedWith(compareBy({ it.createdAt }, { it.id }))) {
        if (line.result == ReviewResult.UNCHECKED) {
            unchecked++
            continue
        }
        if (line.status() == LineStatus.OK && !line.isSerial) {
            val current = inputs.stock[line.articleId to line.locationId] ?: 0
            if (current != line.expectedMilli) drifted += line.id
            continue
        }
        if (line.articleId !in inputs.activeArticleIds) {
            skipped += SkippedLine(line.id, SkipReason.GONE)
            continue
        }
        val pending = line.pendingUnit
        if (pending != null && line.itemId == null) {
            planNewUnit(line, pending, inputs, usedSerials, changes, skipped)
            continue
        }
        val itemId = line.itemId
        if (itemId == null) {
            planCorrection(line, inputs, changes, skipped, drifted, clamped)
        } else {
            if (!handledUnits.add(itemId)) continue
            val unit = inputs.units[itemId]
            if (unit == null || unit.deleted) {
                skipped += SkippedLine(line.id, SkipReason.GONE)
                continue
            }
            planUnit(line, itemId, unit, inputs, resolutions[line.id] ?: MissingResolution.LOSE, changes, skipped)
        }
    }
    return ReviewFinishPlan(changes, skipped, drifted, clamped, unchecked)
}

private fun planNewUnit(
    line: StockReviewLine,
    pending: PendingUnit,
    inputs: FinishInputs,
    usedSerials: MutableSet<String>,
    changes: MutableList<ReviewChange>,
    skipped: MutableList<SkippedLine>,
) {
    if (line.locationId !in inputs.activeLocationIds) {
        skipped += SkippedLine(line.id, SkipReason.GONE)
        return
    }
    val serial = pending.serialNumber?.lowercase()
    if (serial != null && !usedSerials.add(serial)) {
        skipped += SkippedLine(line.id, SkipReason.DUPLICATE_SERIAL)
        return
    }
    changes += ReviewChange.CreateUnit(line.id, line.articleId, line.locationId, pending)
}

private fun planCorrection(
    line: StockReviewLine,
    inputs: FinishInputs,
    changes: MutableList<ReviewChange>,
    skipped: MutableList<SkippedLine>,
    drifted: MutableSet<String>,
    clamped: MutableSet<String>,
) {
    val counted = line.countedMilli ?: return
    val current = inputs.stock[line.articleId to line.locationId] ?: 0
    if (current != line.expectedMilli) drifted += line.id
    val wanted = current + counted - line.expectedMilli
    val target = wanted.coerceAtLeast(0)
    if (target != wanted) clamped += line.id
    val delta = target - current
    if (delta == 0L) return
    if (delta > 0 && line.locationId !in inputs.activeLocationIds) {
        skipped += SkippedLine(line.id, SkipReason.GONE)
        return
    }
    changes += ReviewChange.Correction(line.id, line.articleId, line.locationId, current, delta)
}

private fun planUnit(
    line: StockReviewLine,
    itemId: String,
    unit: UnitState,
    inputs: FinishInputs,
    resolution: MissingResolution,
    changes: MutableList<ReviewChange>,
    skipped: MutableList<SkippedLine>,
) {
    when (line.result) {
        ReviewResult.PRESENT, ReviewResult.ELSEWHERE -> {
            if (line.result == ReviewResult.PRESENT && !line.isAddedUnit) {
                // Expected and seen here: it is in stock at this location unless it changed since.
                if (!(canMoveUnit(unit.status) && unit.locationId == line.locationId)) {
                    skipped += SkippedLine(line.id, SkipReason.CHANGED)
                }
                return
            }
            val target = if (line.result == ReviewResult.ELSEWHERE) line.foundLocationId else line.locationId
            if (target == null || target !in inputs.activeLocationIds) {
                skipped += SkippedLine(line.id, SkipReason.GONE)
                return
            }
            val from = unit.locationId
            when {
                canMoveUnit(unit.status) && from == target -> Unit
                canMoveUnit(unit.status) && from != null ->
                    changes += ReviewChange.MoveUnit(line.id, line.articleId, itemId, from, target)
                else -> changes += ReviewChange.BringBack(line.id, line.articleId, itemId, target, unit.status)
            }
        }
        ReviewResult.MISSING -> {
            if (resolution == MissingResolution.KEEP || unit.status == ItemStatus.LOST) return
            // Moved since the check: it is not missing from here any more.
            if (unit.locationId != null && unit.locationId != line.locationId) {
                skipped += SkippedLine(line.id, SkipReason.CHANGED)
                return
            }
            changes += ReviewChange.LoseUnit(line.id, line.articleId, itemId, unit.status, unit.locationId)
        }
        ReviewResult.UNCHECKED, ReviewResult.COUNTED -> Unit
    }
}
