package ch.mathorck.easystock.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Item
import ch.mathorck.easystock.data.model.ItemStatus
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.ReviewResult
import ch.mathorck.easystock.data.model.ReviewStatus
import ch.mathorck.easystock.data.model.StockMovement
import ch.mathorck.easystock.data.model.StockReview
import ch.mathorck.easystock.data.model.StockReviewLine
import ch.mathorck.easystock.data.model.Tracking
import ch.mathorck.easystock.review.FinishInputs
import ch.mathorck.easystock.review.MissingResolution
import ch.mathorck.easystock.review.ReviewChange
import ch.mathorck.easystock.review.ReviewFinishPlan
import ch.mathorck.easystock.review.ReviewPosition
import ch.mathorck.easystock.review.UnitState
import ch.mathorck.easystock.review.allowedResults
import ch.mathorck.easystock.review.isAddedUnit
import ch.mathorck.easystock.review.isSerial
import ch.mathorck.easystock.review.newLine
import ch.mathorck.easystock.review.planReviewFinish
import ch.mathorck.easystock.review.syncReviewLines
import ch.mathorck.easystock.viewmodels.subtreeIds
import kotlinx.coroutines.flow.Flow

/** The review is not open any more (finished or cancelled), or the line or review is gone. Nothing is saved. */
class ReviewClosedException(what: String) : IllegalStateException(what)

/** A review with its counters, for the list of reviews. */
data class ReviewSummaryRow(
    val id: String,
    @ColumnInfo(name = "location_id") val locationId: String,
    val status: ReviewStatus,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "finished_at") val finishedAt: Long?,
    val total: Int,
    val checked: Int,
    val discrepancies: Int,
    val flagged: Int,
)

/** Last finished review per root location. */
data class LocationReviewedRow(
    @ColumnInfo(name = "location_id") val locationId: String,
    @ColumnInfo(name = "finished_at") val finishedAt: Long,
)

/** Article or unit offered by the "found here" search. */
data class FoundCandidate(
    @ColumnInfo(name = "article_id") val articleId: String,
    @ColumnInfo(name = "article_name") val articleName: String,
    val tracking: Tracking,
    val unit: String,
    @ColumnInfo(name = "item_id") val itemId: String?,
    @ColumnInfo(name = "serial_number") val serialNumber: String?,
    @ColumnInfo(name = "item_status") val itemStatus: ItemStatus?,
    @ColumnInfo(name = "location_id") val locationId: String?,
)

/**
 * Stock reviews: start, check lines, finish. Every write is one transaction and re-checks that the review is open.
 * The stock only changes in [finishReview], and only by inserting stock_movement rows (append-only).
 */
@Dao
abstract class ReviewDao {
    // ---------- Reads ----------

    @Query(
        """
        SELECT r.id, r.location_id, r.status, r.started_at, r.finished_at,
            COUNT(l.id) AS total,
            COALESCE(SUM(CASE WHEN l.result <> 'unchecked' THEN 1 ELSE 0 END), 0) AS checked,
            COALESCE(SUM(CASE WHEN l.result IN ('missing', 'elsewhere')
                OR (l.result = 'counted' AND l.counted_quantity <> l.expected_quantity)
                OR (l.result = 'present' AND l.expected_quantity = 0) THEN 1 ELSE 0 END), 0) AS discrepancies,
            COALESCE(SUM(CASE WHEN l.info_flagged THEN 1 ELSE 0 END), 0) AS flagged
        FROM stock_review r
        LEFT JOIN stock_review_line l ON l.review_id = r.id AND l.deleted_at IS NULL
        WHERE r.deleted_at IS NULL AND r.status <> 'cancelled'
        GROUP BY r.id
        ORDER BY CASE WHEN r.status = 'open' THEN 0 ELSE 1 END, COALESCE(r.finished_at, r.started_at) DESC, r.id
        """,
    )
    abstract fun observeReviews(): Flow<List<ReviewSummaryRow>>

    @Query("SELECT * FROM stock_review WHERE id = :id AND deleted_at IS NULL")
    abstract fun observeReview(id: String): Flow<StockReview?>

    @Query("SELECT * FROM stock_review_line WHERE review_id = :reviewId AND deleted_at IS NULL ORDER BY created_at, id")
    abstract fun observeLines(reviewId: String): Flow<List<StockReviewLine>>

    /** Articles of the review lines, soft-deleted ones included so their line keeps a name. */
    @Query("SELECT * FROM article WHERE id IN (SELECT article_id FROM stock_review_line WHERE review_id = :reviewId)")
    abstract fun observeReviewArticles(reviewId: String): Flow<List<Article>>

    /** Units of the review lines, soft-deleted ones included. */
    @Query("SELECT * FROM item WHERE id IN (SELECT item_id FROM stock_review_line WHERE review_id = :reviewId AND item_id IS NOT NULL)")
    abstract fun observeReviewItems(reviewId: String): Flow<List<Item>>

    /** All locations, soft-deleted ones included, so old reviews keep their path. */
    @Query("SELECT * FROM location")
    abstract fun observeAllLocations(): Flow<List<Location>>

    @Query(
        """
        SELECT location_id, MAX(finished_at) AS finished_at FROM stock_review
        WHERE status = 'finished' AND deleted_at IS NULL AND finished_at IS NOT NULL
        GROUP BY location_id
        """,
    )
    abstract fun observeLastReviewed(): Flow<List<LocationReviewedRow>>

    /** Articles and units whose name, SKU or serial contains [query] (case-insensitive), active ones only. */
    @Query(
        """
        SELECT a.id AS article_id, a.name AS article_name, a.tracking, a.unit,
            NULL AS item_id, NULL AS serial_number, NULL AS item_status, NULL AS location_id
        FROM article a
        WHERE a.deleted_at IS NULL AND a.tracking = 'quantity'
            AND (a.name LIKE '%' || :query || '%' OR a.sku LIKE '%' || :query || '%')
        UNION ALL
        SELECT a.id, a.name, a.tracking, a.unit, i.id, i.serial_number, i.status, i.location_id
        FROM item i JOIN article a ON a.id = i.article_id
        WHERE i.deleted_at IS NULL AND a.deleted_at IS NULL
            AND (a.name LIKE '%' || :query || '%' OR a.sku LIKE '%' || :query || '%' OR i.serial_number LIKE '%' || :query || '%')
        ORDER BY article_name COLLATE NOCASE, serial_number COLLATE NOCASE
        LIMIT 50
        """,
    )
    abstract suspend fun searchFoundCandidates(query: String): List<FoundCandidate>

    @Query("SELECT * FROM stock_review WHERE id = :id AND deleted_at IS NULL")
    abstract suspend fun getReview(id: String): StockReview?

    @Query("SELECT * FROM stock_review WHERE location_id = :locationId AND status = 'open' AND deleted_at IS NULL ORDER BY started_at LIMIT 1")
    abstract suspend fun openReviewFor(locationId: String): StockReview?

    @Query("SELECT * FROM stock_review_line WHERE review_id = :reviewId AND deleted_at IS NULL ORDER BY created_at, id")
    abstract suspend fun linesOf(reviewId: String): List<StockReviewLine>

    @Query("SELECT * FROM stock_review_line WHERE id = :id AND deleted_at IS NULL")
    protected abstract suspend fun getLine(id: String): StockReviewLine?

    @Query("SELECT * FROM location WHERE deleted_at IS NULL")
    protected abstract suspend fun activeLocations(): List<Location>

    @Query("SELECT * FROM article WHERE id = :id AND deleted_at IS NULL")
    protected abstract suspend fun getActiveArticle(id: String): Article?

    @Query("SELECT id FROM article WHERE id IN (:ids) AND deleted_at IS NULL")
    protected abstract suspend fun activeArticleIds(ids: List<String>): List<String>

    @Query("SELECT * FROM item WHERE id = :id")
    protected abstract suspend fun getItem(id: String): Item?

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM stock_movement WHERE article_id = :articleId AND location_id = :locationId")
    abstract suspend fun stockAt(articleId: String, locationId: String): Long

    /** Where the unit is according to its movements, null when out of stock. */
    @Query("SELECT location_id FROM stock_movement WHERE item_id = :itemId GROUP BY location_id HAVING SUM(quantity) > 0 LIMIT 1")
    protected abstract suspend fun itemLocationFromMovements(itemId: String): String?

    /** Quantity-tracked active articles with stock at each location of [ids]. */
    @Query(
        """
        SELECT m.article_id, NULL AS item_id, m.location_id, SUM(m.quantity) AS quantity
        FROM stock_movement m JOIN article a ON a.id = m.article_id
        WHERE a.deleted_at IS NULL AND a.tracking = 'quantity' AND m.location_id IN (:ids)
        GROUP BY m.article_id, m.location_id
        HAVING SUM(m.quantity) <> 0
        """,
    )
    protected abstract suspend fun quantityPositions(ids: List<String>): List<ReviewPosition>

    /** Active units in stock at a location of [ids], by their movements. */
    @Query(
        """
        SELECT m.article_id, m.item_id, m.location_id, SUM(m.quantity) AS quantity
        FROM stock_movement m
        JOIN item i ON i.id = m.item_id
        JOIN article a ON a.id = m.article_id
        WHERE m.item_id IS NOT NULL AND i.deleted_at IS NULL AND a.deleted_at IS NULL AND m.location_id IN (:ids)
        GROUP BY m.item_id, m.location_id
        HAVING SUM(m.quantity) > 0
        """,
    )
    protected abstract suspend fun unitPositions(ids: List<String>): List<ReviewPosition>

    // ---------- Row writes ----------

    @Insert
    protected abstract suspend fun insertReview(review: StockReview)

    @Update
    protected abstract suspend fun updateReview(review: StockReview)

    @Insert
    protected abstract suspend fun insertLines(lines: List<StockReviewLine>)

    @Update
    protected abstract suspend fun updateLines(lines: List<StockReviewLine>)

    @Update
    protected abstract suspend fun updateItem(item: Item)

    @Insert
    protected abstract suspend fun insertMovement(movement: StockMovement)

    // ---------- Review ----------

    /**
     * Opens the review of [locationId] and its descendants, or returns the open one (one open review per location).
     * Adds lines for the stock found in the scope. Returns the review id.
     */
    @Transaction
    open suspend fun startReview(locationId: String, now: Long, newId: () -> String): String {
        openReviewFor(locationId)?.let {
            syncLines(it.id, now, newId)
            return it.id
        }
        if (activeLocations().none { it.id == locationId }) throw StockTargetNotFoundException("location $locationId")
        val review = StockReview(id = newId(), locationId = locationId, startedAt = now, createdAt = now, updatedAt = now)
        insertReview(review)
        syncLines(review.id, now, newId)
        return review.id
    }

    /** Active locations of the review: its root and all descendants. Empty when the root was deleted. */
    @Transaction
    open suspend fun scopeIds(reviewId: String): List<String> {
        val review = getReview(reviewId) ?: return emptyList()
        return activeLocations().subtreeIds(review.locationId)
    }

    /** Adds lines for stock that appeared in the scope and refreshes unchecked quantities. No-op when not open. */
    @Transaction
    open suspend fun syncLines(reviewId: String, now: Long, newId: () -> String) {
        val review = getReview(reviewId) ?: return
        if (review.status != ReviewStatus.OPEN) return
        val ids = activeLocations().subtreeIds(review.locationId)
        val positions = if (ids.isEmpty()) emptyList() else quantityPositions(ids) + unitPositions(ids)
        val sync = syncReviewLines(reviewId, linesOf(reviewId), positions, now, newId)
        if (sync.inserts.isNotEmpty()) insertLines(sync.inserts)
        if (sync.updates.isNotEmpty()) updateLines(sync.updates)
    }

    /** Records the counted quantity of a quantity line. The expected quantity is snapshotted now. */
    @Transaction
    open suspend fun setCount(lineId: String, countedMilli: Long, now: Long) {
        require(countedMilli >= 0) { "A count is never negative" }
        val line = openLine(lineId)
        require(!line.isSerial) { "Not a quantity line" }
        val expected = stockAt(line.articleId, line.locationId)
        updateLine(line.copy(expectedMilli = expected, countedMilli = countedMilli, result = ReviewResult.COUNTED, checkedAt = now, updatedAt = now))
    }

    /** The counted quantity is the stock: counted = expected = current stock. */
    @Transaction
    open suspend fun markCorrect(lineId: String, now: Long) {
        val line = openLine(lineId)
        require(!line.isSerial) { "Not a quantity line" }
        val expected = stockAt(line.articleId, line.locationId).coerceAtLeast(0)
        updateLine(line.copy(expectedMilli = expected, countedMilli = expected, result = ReviewResult.COUNTED, checkedAt = now, updatedAt = now))
    }

    /** Present, missing or found elsewhere ([foundLocationId], active and different from the line location). */
    @Transaction
    open suspend fun setUnitResult(lineId: String, result: ReviewResult, foundLocationId: String?, now: Long) {
        val line = openLine(lineId)
        require(line.isSerial) { "Not a unit line" }
        require(result in allowedResults(serial = true) && result != ReviewResult.UNCHECKED) { "Use resetLine" }
        if (result == ReviewResult.ELSEWHERE) {
            requireNotNull(foundLocationId) { "Where it was found is needed" }
            require(foundLocationId != line.locationId) { "Found at its own location: use present" }
            if (activeLocations().none { it.id == foundLocationId }) throw StockTargetNotFoundException("location $foundLocationId")
        }
        updateLine(
            line.copy(
                result = result,
                foundLocationId = if (result == ReviewResult.ELSEWHERE) foundLocationId else null,
                checkedAt = now,
                updatedAt = now,
            ),
        )
    }

    /** Back to unchecked. A unit added as "found here" is removed instead (soft delete). */
    @Transaction
    open suspend fun resetLine(lineId: String, now: Long) {
        val line = openLine(lineId)
        if (line.isAddedUnit) {
            updateLine(line.copy(deletedAt = now, updatedAt = now))
            return
        }
        val expected = if (line.isSerial) line.expectedMilli else stockAt(line.articleId, line.locationId)
        updateLine(
            line.copy(
                expectedMilli = expected,
                countedMilli = null,
                result = ReviewResult.UNCHECKED,
                foundLocationId = null,
                checkedAt = null,
                updatedAt = now,
            ),
        )
    }

    /** Flags (or unflags) the info of the line as needing a fix, with an optional note. */
    @Transaction
    open suspend fun setFlag(lineId: String, flagged: Boolean, note: String?, now: Long) {
        val line = openLine(lineId)
        updateLine(line.copy(infoFlagged = flagged, note = note?.trim()?.takeIf { it.isNotEmpty() }, updatedAt = now))
    }

    /**
     * A unit found at [locationId]. If the review already has it: present when it is its line location, else
     * found elsewhere. Otherwise a new line, present here and not expected. Returns the line id.
     */
    @Transaction
    open suspend fun addFoundUnit(reviewId: String, itemId: String, locationId: String, now: Long, newId: () -> String): String {
        val review = openReview(reviewId)
        requireInScope(review, locationId)
        val item = getItem(itemId)?.takeIf { it.deletedAt == null } ?: throw StockTargetNotFoundException("item $itemId")
        getActiveArticle(item.articleId) ?: throw StockTargetNotFoundException("article ${item.articleId}")
        val existing = linesOf(reviewId).firstOrNull { it.itemId == itemId }
        if (existing != null) {
            val result = if (existing.locationId == locationId) ReviewResult.PRESENT else ReviewResult.ELSEWHERE
            setUnitResult(existing.id, result, locationId.takeIf { result == ReviewResult.ELSEWHERE }, now)
            return existing.id
        }
        val line = newLine(newId(), reviewId, item.articleId, itemId, locationId, 0, now, ReviewResult.PRESENT)
        insertLines(listOf(line))
        return line.id
    }

    /** A quantity article found at [locationId]: its line there, created when missing (to count it). Returns the line id. */
    @Transaction
    open suspend fun addFoundArticle(reviewId: String, articleId: String, locationId: String, now: Long, newId: () -> String): String {
        val review = openReview(reviewId)
        requireInScope(review, locationId)
        val article = getActiveArticle(articleId) ?: throw StockTargetNotFoundException("article $articleId")
        require(article.tracking == Tracking.QUANTITY) { "Serial articles are found by unit" }
        linesOf(reviewId).firstOrNull { !it.isSerial && it.articleId == articleId && it.locationId == locationId }?.let { return it.id }
        val line = newLine(newId(), reviewId, articleId, null, locationId, stockAt(articleId, locationId), now)
        insertLines(listOf(line))
        return line.id
    }

    /** Closes the review without changing anything. */
    @Transaction
    open suspend fun cancelReview(reviewId: String, now: Long) {
        val review = openReview(reviewId)
        updateReview(review.copy(status = ReviewStatus.CANCELLED, finishedAt = now, updatedAt = now))
    }

    /** What [finishReview] would do now, for the summary. Nothing is written. */
    @Transaction
    open suspend fun planFinish(reviewId: String, resolutions: Map<String, MissingResolution>): ReviewFinishPlan =
        planReviewFinish(finishInputs(openReview(reviewId).id), resolutions)

    /**
     * Applies the corrections and closes the review, in one transaction. Only inserts stock_movement rows
     * (INVENTORY corrections, TRANSFER pairs, RETURN, LOSS) and updates the units status and location cache.
     * Every movement gets [comment] and occurs [now]. Returns the plan applied.
     */
    @Transaction
    open suspend fun finishReview(
        reviewId: String,
        resolutions: Map<String, MissingResolution>,
        comment: String,
        now: Long,
        newId: () -> String,
    ): ReviewFinishPlan {
        val review = openReview(reviewId)
        val plan = planReviewFinish(finishInputs(reviewId), resolutions)
        for (change in plan.changes) applyChange(change, comment, now, newId)
        updateReview(review.copy(status = ReviewStatus.FINISHED, finishedAt = now, updatedAt = now))
        return plan
    }

    // ---------- Helpers ----------

    private suspend fun finishInputs(reviewId: String): FinishInputs {
        val lines = linesOf(reviewId)
        val stock = lines.filter { !it.isSerial }
            .associate { (it.articleId to it.locationId) to stockAt(it.articleId, it.locationId) }
        val units = lines.mapNotNull { it.itemId }.distinct().mapNotNull { id ->
            getItem(id)?.let { item ->
                id to UnitState(item.status, itemLocationFromMovements(id), deleted = item.deletedAt != null)
            }
        }.toMap()
        return FinishInputs(
            lines = lines,
            stock = stock,
            units = units,
            activeArticleIds = activeArticleIds(lines.map { it.articleId }.distinct()).toSet(),
            activeLocationIds = activeLocations().mapTo(HashSet()) { it.id },
        )
    }

    private suspend fun applyChange(change: ReviewChange, comment: String, now: Long, newId: () -> String) {
        fun movement(locationId: String, quantity: Long, reason: MovementReason, itemId: String?) = StockMovement(
            id = newId(), articleId = change.articleId, itemId = itemId, locationId = locationId,
            quantityMilli = quantity, reason = reason, comment = comment, occurredAt = now, createdAt = now,
        )
        when (change) {
            is ReviewChange.Correction -> {
                if (change.deltaMilli < 0) {
                    val available = stockAt(change.articleId, change.locationId)
                    if (available + change.deltaMilli < 0) throw InsufficientStockException(available)
                }
                insertMovement(movement(change.locationId, change.deltaMilli, MovementReason.INVENTORY, null))
            }
            is ReviewChange.MoveUnit -> {
                insertMovement(movement(change.fromLocationId, -UNIT_MILLI, MovementReason.TRANSFER, change.itemId))
                insertMovement(movement(change.toLocationId, UNIT_MILLI, MovementReason.TRANSFER, change.itemId))
                syncUnit(change.itemId, null, now)
            }
            is ReviewChange.BringBack -> {
                val (status, reason) = when (change.from) {
                    ItemStatus.LOST -> unitTransition(ItemStatus.LOST, UnitAction.FOUND)!!.let { it.newStatus to it.reason!! }
                    ItemStatus.LENT -> unitTransition(ItemStatus.LENT, UnitAction.RETURN)!!.let { it.newStatus to it.reason!! }
                    ItemStatus.AVAILABLE, ItemStatus.REPAIR -> change.from to MovementReason.INVENTORY
                }
                insertMovement(movement(change.locationId, UNIT_MILLI, reason, change.itemId))
                syncUnit(change.itemId, status, now)
            }
            is ReviewChange.LoseUnit -> {
                val transition = unitTransition(change.from, UnitAction.LOSE) ?: throw InvalidItemStatusException(change.from)
                val reason = transition.reason
                if (transition.delta < 0 && reason != null) {
                    val from = itemLocationFromMovements(change.itemId)
                    if (from != null) insertMovement(movement(from, transition.delta, reason, change.itemId))
                }
                syncUnit(change.itemId, transition.newStatus, now)
            }
        }
    }

    /** Sets the unit status ([status] null = unchanged) and recomputes its location cache from the movements. */
    private suspend fun syncUnit(itemId: String, status: ItemStatus?, now: Long) {
        val item = getItem(itemId) ?: throw StockTargetNotFoundException("item $itemId")
        updateItem(item.copy(status = status ?: item.status, locationId = itemLocationFromMovements(itemId), updatedAt = now))
    }

    private suspend fun updateLine(line: StockReviewLine) = updateLines(listOf(line))

    private suspend fun openReview(reviewId: String): StockReview {
        val review = getReview(reviewId) ?: throw ReviewClosedException("review $reviewId")
        if (review.status != ReviewStatus.OPEN) throw ReviewClosedException("review $reviewId is ${review.status}")
        return review
    }

    private suspend fun openLine(lineId: String): StockReviewLine {
        val line = getLine(lineId) ?: throw ReviewClosedException("line $lineId")
        openReview(line.reviewId)
        return line
    }

    private suspend fun requireInScope(review: StockReview, locationId: String) {
        require(locationId in activeLocations().subtreeIds(review.locationId)) { "Location outside the review" }
    }
}
