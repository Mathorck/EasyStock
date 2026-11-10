package ch.mathorck.easystock.data.db

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
import ch.mathorck.easystock.data.model.StockMovement
import ch.mathorck.easystock.data.model.Tracking
import kotlinx.serialization.json.JsonObject

// ---------- Rules (pure, shared with the UI) ----------

/** One unit of a serial-tracked article, in thousandths. */
const val UNIT_MILLI: Long = 1000

/** Max length of `item.serial_number` (docs/schema.dbml). */
const val MAX_SERIAL_LENGTH = 100

/** Reasons allowed when adding stock to a location. */
val STOCK_IN_REASONS = listOf(MovementReason.PURCHASE, MovementReason.RETURN, MovementReason.INVENTORY)

/** Reasons allowed when removing stock from a location. */
val STOCK_OUT_REASONS = listOf(MovementReason.CONSUMPTION, MovementReason.LOSS, MovementReason.INVENTORY)

/** Status changes of one serial unit. A move is separate ([StockActionDao.moveItem]). */
enum class UnitAction { LEND, RETURN, REPAIR, END_REPAIR, LOSE, FOUND }

/**
 * What a [UnitAction] does from a status. [delta] is the movement on the unit (+1000, -1000 or 0 for none).
 * [needsLocation]: the unit comes back into stock, the user picks where.
 */
data class UnitTransition(val newStatus: ItemStatus, val delta: Long, val reason: MovementReason?, val needsLocation: Boolean)

/**
 * The transition for [action] from [status], or null when not allowed.
 * Stock = SUM(quantity) counts the units on hand: a loan and a loss take the unit out, a return or a find
 * brings it back. A repair changes the status only (no repair reason in the schema, the unit stays counted).
 * Losing a lent unit adds no movement: the loan already took it out.
 */
fun unitTransition(status: ItemStatus, action: UnitAction): UnitTransition? = when (action) {
    UnitAction.LEND -> if (status == ItemStatus.AVAILABLE) {
        UnitTransition(ItemStatus.LENT, -UNIT_MILLI, MovementReason.LOAN, needsLocation = false)
    } else {
        null
    }
    UnitAction.RETURN -> if (status == ItemStatus.LENT) {
        UnitTransition(ItemStatus.AVAILABLE, UNIT_MILLI, MovementReason.RETURN, needsLocation = true)
    } else {
        null
    }
    UnitAction.REPAIR -> if (status == ItemStatus.AVAILABLE) {
        UnitTransition(ItemStatus.REPAIR, 0, null, needsLocation = false)
    } else {
        null
    }
    UnitAction.END_REPAIR -> if (status == ItemStatus.REPAIR) {
        UnitTransition(ItemStatus.AVAILABLE, 0, null, needsLocation = false)
    } else {
        null
    }
    UnitAction.LOSE -> when (status) {
        ItemStatus.AVAILABLE, ItemStatus.REPAIR -> UnitTransition(ItemStatus.LOST, -UNIT_MILLI, MovementReason.LOSS, needsLocation = false)
        ItemStatus.LENT -> UnitTransition(ItemStatus.LOST, 0, null, needsLocation = false)
        ItemStatus.LOST -> null
    }
    UnitAction.FOUND -> if (status == ItemStatus.LOST) {
        UnitTransition(ItemStatus.AVAILABLE, UNIT_MILLI, MovementReason.INVENTORY, needsLocation = true)
    } else {
        null
    }
}

/** Actions offered for a unit in [status]. Move is offered on top when the unit is in stock. */
fun unitActions(status: ItemStatus): List<UnitAction> = UnitAction.entries.filter { unitTransition(status, it) != null }

/** Statuses a new unit can start with: it comes into stock, so lent and lost make no sense. */
val CREATABLE_STATUSES = listOf(ItemStatus.AVAILABLE, ItemStatus.REPAIR)

/**
 * Statuses the edit sheet offers for a unit now in [current] status. Available and repair both keep the unit
 * in stock, so they can be switched freely. Lent and lost change the stock: use the unit actions
 * (they add the movements), so the status is fixed here.
 */
fun editableStatuses(current: ItemStatus): List<ItemStatus> =
    if (current in CREATABLE_STATUSES) CREATABLE_STATUSES else listOf(current)

/** A unit can move only while it is in stock at a location (available or in repair). */
fun canMoveUnit(status: ItemStatus): Boolean = status == ItemStatus.AVAILABLE || status == ItemStatus.REPAIR

/** New values of the editable properties of a unit. The location is not one of them (it comes from the movements). */
data class ItemChanges(
    val serialNumber: String?,
    val status: ItemStatus,
    val attributes: JsonObject?,
    val archived: Boolean,
)

// ---------- Errors ----------

/** Removing or moving more than the stock at the location. Nothing is saved. */
class InsufficientStockException(val availableMilli: Long) : IllegalStateException("Only $availableMilli in stock")

/** The serial number is already used by an item, deleted ones included (unique index). */
class DuplicateSerialException(val serial: String) : IllegalStateException("Serial number already used: $serial")

/** The unit is not in a status that allows the action (changed meanwhile). */
class InvalidItemStatusException(val status: ItemStatus) : IllegalStateException("Not allowed for status $status")

/** The article, item or location is gone (deleted meanwhile) or does not match. */
class StockTargetNotFoundException(what: String) : IllegalStateException(what)

// ---------- DAO ----------

/**
 * Writes for the article detail screen: add, remove and move stock, serial units and their status.
 * Every method is one transaction and re-checks its rules against the stored data.
 * Movements are append-only (insert only). `item.location_id` is recomputed from the movements.
 */
@Dao
abstract class StockActionDao {
    @Insert
    protected abstract suspend fun insertLocation(location: Location)

    @Insert
    protected abstract suspend fun insertItem(item: Item)

    @Update
    protected abstract suspend fun updateItem(item: Item)

    @Insert
    protected abstract suspend fun insertMovement(movement: StockMovement)

    @Query("SELECT * FROM article WHERE id = :id AND deleted_at IS NULL")
    protected abstract suspend fun getActiveArticle(id: String): Article?

    @Query("SELECT * FROM item WHERE id = :id AND deleted_at IS NULL")
    protected abstract suspend fun getActiveItem(id: String): Item?

    @Query("SELECT COUNT(*) FROM location WHERE id = :id AND deleted_at IS NULL")
    protected abstract suspend fun countActiveLocations(id: String): Int

    /** Ignores case. Includes soft-deleted items: they keep their serial and the unique index. */
    @Query("SELECT COUNT(*) FROM item WHERE serial_number = :serial COLLATE NOCASE")
    protected abstract suspend fun countItemsWithSerial(serial: String): Int

    /** Ignores case. Includes soft-deleted items (unique index). */
    @Query("SELECT COUNT(*) FROM item WHERE serial_number = :serial COLLATE NOCASE AND id != :exceptId")
    protected abstract suspend fun countOtherItemsWithSerial(serial: String, exceptId: String): Int

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM stock_movement WHERE article_id = :articleId AND location_id = :locationId")
    abstract suspend fun stockAt(articleId: String, locationId: String): Long

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM stock_movement WHERE item_id = :itemId AND location_id = :locationId")
    protected abstract suspend fun itemStockAt(itemId: String, locationId: String): Long

    /** Where the unit is according to its movements, null when it is out of stock (lent, lost). */
    @Query(
        """
        SELECT location_id FROM stock_movement
        WHERE item_id = :itemId
        GROUP BY location_id
        HAVING SUM(quantity) > 0
        LIMIT 1
        """,
    )
    protected abstract suspend fun itemLocationFromMovements(itemId: String): String?

    /** Adds stock (positive [StockMovement.quantityMilli]) or removes it (negative), never below 0 at the location. */
    @Transaction
    open suspend fun adjust(movement: StockMovement, newLocation: Location? = null) {
        require(movement.itemId == null) { "Use the item methods for serial units" }
        require(movement.quantityMilli != 0L) { "Quantity must not be 0" }
        val allowed = if (movement.quantityMilli > 0) STOCK_IN_REASONS else STOCK_OUT_REASONS
        require(movement.reason in allowed) { "Reason ${movement.reason} not allowed for this direction" }
        requireQuantityArticle(movement.articleId)
        if (newLocation != null) {
            require(movement.quantityMilli > 0 && newLocation.id == movement.locationId) { "New location only for an entry" }
            insertLocation(newLocation)
        } else if (movement.quantityMilli > 0) {
            requireActiveLocation(movement.locationId)
        }
        if (movement.quantityMilli < 0) {
            val available = stockAt(movement.articleId, movement.locationId)
            if (available + movement.quantityMilli < 0) throw InsufficientStockException(available)
        }
        insertMovement(movement)
    }

    /**
     * Moves [out] (negative, at the source) to [into] (positive, at the destination): two rows, reason TRANSFER.
     * The source can be a soft-deleted location, the destination must be active (or [newLocation]).
     */
    @Transaction
    open suspend fun transfer(out: StockMovement, into: StockMovement, newLocation: Location? = null) {
        requireTransferPair(out, into)
        require(out.itemId == null) { "Use moveItem for serial units" }
        requireQuantityArticle(out.articleId)
        addDestination(into.locationId, newLocation)
        val available = stockAt(out.articleId, out.locationId)
        if (available + out.quantityMilli < 0) throw InsufficientStockException(available)
        insertMovement(out)
        insertMovement(into)
    }

    /** Inserts a new serial unit and its +1 PURCHASE movement. [Item.locationId] must match the movement. */
    @Transaction
    open suspend fun addItem(item: Item, movement: StockMovement, newLocation: Location? = null) {
        require(item.articleId == movement.articleId && movement.itemId == item.id) { "Movement does not match the item" }
        require(movement.quantityMilli == UNIT_MILLI && movement.reason == MovementReason.PURCHASE) { "A new unit is +1 purchase" }
        require(item.status == ItemStatus.AVAILABLE || item.status == ItemStatus.REPAIR) { "A new unit is in stock" }
        require(item.locationId == movement.locationId) { "A new unit is at its location" }
        val article = getActiveArticle(item.articleId) ?: throw StockTargetNotFoundException("article ${item.articleId}")
        require(article.tracking == Tracking.SERIAL) { "Article is not serial-tracked" }
        item.serialNumber?.let {
            require(it.isNotBlank() && it == it.trim() && it.length <= MAX_SERIAL_LENGTH) { "Invalid serial number" }
            if (countItemsWithSerial(it) > 0) throw DuplicateSerialException(it)
        }
        addDestination(movement.locationId, newLocation)
        insertItem(item)
        insertMovement(movement)
    }

    /**
     * Saves the serial number, status, attributes and archived flag of a unit and sets `updated_at` to [now].
     * Never touches the movements or the location. The status can only switch between available and repair
     * (both keep the unit in stock); the other changes need a unit action, which adds the movements.
     * Throws [StockTargetNotFoundException], [DuplicateSerialException] (ignoring case, deleted units included)
     * or [InvalidItemStatusException]; nothing is saved then.
     */
    @Transaction
    open suspend fun updateItemProperties(itemId: String, changes: ItemChanges, now: Long) {
        val item = getActiveItem(itemId) ?: throw StockTargetNotFoundException("item $itemId")
        changes.serialNumber?.let {
            require(it.isNotBlank() && it == it.trim() && it.length <= MAX_SERIAL_LENGTH) { "Invalid serial number" }
            if (countOtherItemsWithSerial(it, itemId) > 0) throw DuplicateSerialException(it)
        }
        if (changes.status != item.status && changes.status !in editableStatuses(item.status)) {
            throw InvalidItemStatusException(item.status)
        }
        updateItem(
            item.copy(
                serialNumber = changes.serialNumber,
                status = changes.status,
                attributes = changes.attributes,
                archived = changes.archived,
                updatedAt = now,
            ),
        )
    }

    /** Moves a unit in stock (available or repair): -1 at its location, +1 at [toLocationId], TRANSFER. */
    @Transaction
    open suspend fun moveItem(
        itemId: String,
        toLocationId: String,
        newLocation: Location?,
        outId: String,
        inId: String,
        comment: String?,
        now: Long,
    ) {
        val item = getActiveItem(itemId) ?: throw StockTargetNotFoundException("item $itemId")
        if (!canMoveUnit(item.status)) throw InvalidItemStatusException(item.status)
        val from = itemLocationFromMovements(itemId) ?: throw InsufficientStockException(0)
        require(from != toLocationId) { "Destination is the current location" }
        addDestination(toLocationId, newLocation)
        insertMovement(movement(outId, item, from, -UNIT_MILLI, MovementReason.TRANSFER, comment, now))
        insertMovement(movement(inId, item, toLocationId, UNIT_MILLI, MovementReason.TRANSFER, comment, now))
        syncItem(item, item.status, now)
    }

    /**
     * Applies [action] to a unit (see [unitTransition]). [locationId] is where the unit comes back
     * when the transition needs one (return, found), ignored otherwise. [movementId] is used only if a movement is added.
     */
    @Transaction
    open suspend fun applyUnitAction(
        itemId: String,
        action: UnitAction,
        locationId: String?,
        newLocation: Location?,
        movementId: String,
        comment: String?,
        now: Long,
    ) {
        val item = getActiveItem(itemId) ?: throw StockTargetNotFoundException("item $itemId")
        val transition = unitTransition(item.status, action) ?: throw InvalidItemStatusException(item.status)
        val reason = transition.reason
        when {
            transition.delta > 0 && reason != null -> {
                val target = requireNotNull(locationId) { "A location is needed" }
                addDestination(target, newLocation)
                insertMovement(movement(movementId, item, target, transition.delta, reason, comment, now))
            }
            transition.delta < 0 && reason != null -> {
                val from = itemLocationFromMovements(itemId) ?: throw InsufficientStockException(0)
                insertMovement(movement(movementId, item, from, transition.delta, reason, comment, now))
            }
        }
        syncItem(item, transition.newStatus, now)
    }

    /** Sets the status and the location cache (recomputed from the movements, null when out of stock). */
    private suspend fun syncItem(item: Item, status: ItemStatus, now: Long) {
        updateItem(item.copy(status = status, locationId = itemLocationFromMovements(item.id), updatedAt = now))
    }

    private suspend fun requireQuantityArticle(articleId: String) {
        val article = getActiveArticle(articleId) ?: throw StockTargetNotFoundException("article $articleId")
        require(article.tracking == Tracking.QUANTITY) { "Article is serial-tracked" }
    }

    private suspend fun requireActiveLocation(id: String) {
        if (countActiveLocations(id) == 0) throw StockTargetNotFoundException("location $id")
    }

    /** Inserts [newLocation] when given (it must be [locationId]), else checks the location is active. */
    private suspend fun addDestination(locationId: String, newLocation: Location?) {
        if (newLocation != null) {
            require(newLocation.id == locationId) { "Movement does not point to the new location" }
            insertLocation(newLocation)
        } else {
            requireActiveLocation(locationId)
        }
    }

    private fun requireTransferPair(out: StockMovement, into: StockMovement) {
        require(out.reason == MovementReason.TRANSFER && into.reason == MovementReason.TRANSFER) { "A transfer uses reason TRANSFER" }
        require(out.quantityMilli < 0 && into.quantityMilli == -out.quantityMilli) { "A transfer is -q then +q" }
        require(out.articleId == into.articleId && out.itemId == into.itemId) { "Both rows must be the same article" }
        require(out.occurredAt == into.occurredAt) { "Both rows must have the same occurredAt" }
        require(out.locationId != into.locationId) { "Source and destination must differ" }
        require(out.id != into.id) { "Two distinct rows" }
    }

    private fun movement(
        id: String,
        item: Item,
        locationId: String,
        quantityMilli: Long,
        reason: MovementReason,
        comment: String?,
        now: Long,
    ) = StockMovement(
        id = id,
        articleId = item.articleId,
        itemId = item.id,
        locationId = locationId,
        quantityMilli = quantityMilli,
        reason = reason,
        comment = comment,
        occurredAt = now,
        createdAt = now,
    )
}
