package ch.mathorck.easystock.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.composables.attributes.AttributeError
import ch.mathorck.easystock.composables.attributes.validateAttributes
import ch.mathorck.easystock.data.db.ArticleDetailDao
import ch.mathorck.easystock.data.db.AttributeDao
import ch.mathorck.easystock.data.db.CategoryAttributeWithDefinition
import ch.mathorck.easystock.data.db.CREATABLE_STATUSES
import ch.mathorck.easystock.data.db.ItemChanges
import ch.mathorck.easystock.data.db.editableStatuses
import ch.mathorck.easystock.data.db.DuplicateSerialException
import ch.mathorck.easystock.data.db.ItemLastLocation
import ch.mathorck.easystock.data.db.MAX_SERIAL_LENGTH
import ch.mathorck.easystock.data.db.STOCK_IN_REASONS
import ch.mathorck.easystock.data.db.STOCK_OUT_REASONS
import ch.mathorck.easystock.data.db.StockActionDao
import ch.mathorck.easystock.data.db.StockDao
import ch.mathorck.easystock.data.db.UNIT_MILLI
import ch.mathorck.easystock.data.db.UnitAction
import ch.mathorck.easystock.data.db.canMoveUnit
import ch.mathorck.easystock.data.db.unitActions
import ch.mathorck.easystock.data.db.unitTransition
import ch.mathorck.easystock.data.model.Item
import ch.mathorck.easystock.data.model.ItemStatus
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.StockMovement
import ch.mathorck.easystock.util.parseQuantity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// ---------- Form and validation (pure) ----------

/** Pick an existing location or create a new one. */
enum class LocationChoice { EXISTING, NEW }

/** Max length of `location.name` (docs/schema.dbml). */
private const val LOCATION_NAME_MAX = 100

/** What the action sheet does. The first three are for quantity-tracked articles, the others for serial units. */
enum class StockActionKind { ADD, REMOVE, MOVE, ADD_UNIT, MOVE_UNIT, UNIT_STATUS, EDIT_UNIT }

/** Errors of the action sheet. Separate from [FieldError] so other forms are not affected. */
enum class StockActionError { REQUIRED, INVALID_QUANTITY, NOT_POSITIVE, EXCEEDS_STOCK, TOO_LONG, DUPLICATE_SERIAL, SAME_LOCATION }

/** Raw input of the action sheet, as typed. */
data class StockActionForm(
    val kind: StockActionKind,
    /** Location of the row the action was started from (fixed). Null for "add to a location". */
    val sourceLocationId: String? = null,
    /** The unit, for [StockActionKind.MOVE_UNIT], [StockActionKind.UNIT_STATUS] and [StockActionKind.EDIT_UNIT]. */
    val itemId: String? = null,
    val unitAction: UnitAction? = null,
    val targetMode: LocationChoice = LocationChoice.EXISTING,
    val targetLocationId: String? = null,
    val newLocationName: String = "",
    val newLocationParentId: String? = null,
    val quantity: String = "",
    val reason: MovementReason? = defaultReason(kind),
    val serial: String = "",
    /** Unit properties ([StockActionKind.ADD_UNIT] and [StockActionKind.EDIT_UNIT]). */
    val itemStatus: ItemStatus = ItemStatus.AVAILABLE,
    /** Unit-specific attribute values, overriding the article ones. Same keys as `article.attributes`. */
    val itemAttributes: JsonObject? = null,
    val archived: Boolean = false,
    val comment: String = "",
) {
    /** The user picks a destination (existing or new). */
    val needsTarget: Boolean
        get() = when (kind) {
            StockActionKind.ADD -> sourceLocationId == null
            StockActionKind.REMOVE -> false
            StockActionKind.MOVE, StockActionKind.ADD_UNIT, StockActionKind.MOVE_UNIT -> true
            StockActionKind.UNIT_STATUS -> unitAction == UnitAction.RETURN || unitAction == UnitAction.FOUND
            StockActionKind.EDIT_UNIT -> false
        }

    /** The sheet edits the properties of a unit (serial, status, attributes). */
    val hasUnitProperties: Boolean get() = kind == StockActionKind.ADD_UNIT || kind == StockActionKind.EDIT_UNIT

    val needsQuantity: Boolean get() = kind == StockActionKind.ADD || kind == StockActionKind.REMOVE || kind == StockActionKind.MOVE
}

/** Reasons the user can pick for [kind]. Empty when the reason is fixed (transfer, units). */
fun reasonsFor(kind: StockActionKind): List<MovementReason> = when (kind) {
    StockActionKind.ADD -> STOCK_IN_REASONS
    StockActionKind.REMOVE -> STOCK_OUT_REASONS
    else -> emptyList()
}

private fun defaultReason(kind: StockActionKind): MovementReason? = when (kind) {
    StockActionKind.ADD -> MovementReason.PURCHASE
    StockActionKind.REMOVE -> MovementReason.CONSUMPTION
    else -> null
}

/** Checks a typed quantity: required, valid, > 0 and, when [availableMilli] is given, not more than it. */
fun validateQuantity(text: String, availableMilli: Long?): StockActionError? {
    if (text.isBlank()) return StockActionError.REQUIRED
    val milli = parseQuantity(text) ?: return StockActionError.INVALID_QUANTITY
    return when {
        milli <= 0 -> StockActionError.NOT_POSITIVE
        availableMilli != null && milli > availableMilli -> StockActionError.EXCEEDS_STOCK
        else -> null
    }
}

/** Optional serial number: max length, unique ignoring case among [existing] (deleted items included). */
fun validateSerial(text: String, existing: List<String>): StockActionError? {
    val value = text.trim()
    return when {
        value.isEmpty() -> null
        value.length > MAX_SERIAL_LENGTH -> StockActionError.TOO_LONG
        existing.any { it.equals(value, ignoreCase = true) } -> StockActionError.DUPLICATE_SERIAL
        else -> null
    }
}

data class StockActionErrors(
    val quantity: StockActionError? = null,
    val reason: StockActionError? = null,
    val location: StockActionError? = null,
    val locationName: StockActionError? = null,
    val serial: StockActionError? = null,
    /** The unit is gone or its status no longer allows the action. */
    val unit: StockActionError? = null,
    /** Item attribute values that are not valid, keyed by attribute key. */
    val attributes: Map<String, AttributeError> = emptyMap(),
) {
    val isValid: Boolean get() = listOf(quantity, reason, location, locationName, serial, unit).all { it == null } && attributes.isEmpty()
}

// ---------- Unit properties (pure) ----------

/**
 * Attributes of the category as optional overrides on a unit: a unit never has to repeat the article value,
 * so "required" does not apply.
 */
fun itemAttributeOverrides(attributes: List<CategoryAttributeWithDefinition>): List<CategoryAttributeWithDefinition> =
    attributes.map { it.copy(link = it.link.copy(required = false)) }

/** Data the form is checked against. */
data class StockActionContext(
    /** Stock of the article per location id. */
    val stockByLocation: Map<String, Long>,
    val activeLocationIds: Set<String>,
    /** All serial numbers, deleted items included. */
    val serials: List<String>,
    val items: List<Item>,
    /** Attributes of the article category, for the unit attribute values. */
    val itemAttributes: List<CategoryAttributeWithDefinition> = emptyList(),
)

/** [rejectedSerial]: a serial the database refused on the last save. */
fun StockActionForm.validate(context: StockActionContext, rejectedSerial: String? = null): StockActionErrors {
    val item = itemId?.let { id -> context.items.firstOrNull { it.id == id } }
    val currentLocation: String? = when (kind) {
        StockActionKind.MOVE -> sourceLocationId
        StockActionKind.MOVE_UNIT -> item?.locationId
        else -> null
    }
    val available = when (kind) {
        StockActionKind.REMOVE, StockActionKind.MOVE -> sourceLocationId?.let { context.stockByLocation[it] ?: 0 } ?: 0
        else -> null
    }
    val newTarget = needsTarget && targetMode == LocationChoice.NEW
    return StockActionErrors(
        quantity = if (needsQuantity) validateQuantity(quantity, available) else null,
        reason = if (reasonsFor(kind).isNotEmpty() && reason !in reasonsFor(kind)) StockActionError.REQUIRED else null,
        location = when {
            !needsTarget || newTarget -> null
            targetLocationId == null || targetLocationId !in context.activeLocationIds -> StockActionError.REQUIRED
            targetLocationId == currentLocation -> StockActionError.SAME_LOCATION
            else -> null
        },
        locationName = when {
            !newTarget -> null
            newLocationName.isBlank() -> StockActionError.REQUIRED
            newLocationName.trim().length > LOCATION_NAME_MAX -> StockActionError.TOO_LONG
            else -> null
        },
        serial = when (kind) {
            StockActionKind.ADD_UNIT -> validateSerial(serial, context.serials + listOfNotNull(rejectedSerial))
            // The unit may keep its own serial
            StockActionKind.EDIT_UNIT -> validateSerial(serial, context.serials.withoutOne(item?.serialNumber) + listOfNotNull(rejectedSerial))
            else -> null
        },
        unit = when (kind) {
            StockActionKind.MOVE_UNIT -> if (item == null || !canMoveUnit(item.status)) StockActionError.REQUIRED else null
            StockActionKind.UNIT_STATUS -> {
                val action = unitAction
                if (item == null || action == null || unitTransition(item.status, action) == null) StockActionError.REQUIRED else null
            }
            StockActionKind.ADD_UNIT -> if (itemStatus in CREATABLE_STATUSES) null else StockActionError.REQUIRED
            StockActionKind.EDIT_UNIT -> if (item == null || itemStatus !in editableStatuses(item.status)) StockActionError.REQUIRED else null
            else -> null
        },
        attributes = if (hasUnitProperties) validateAttributes(itemAttributeOverrides(context.itemAttributes), itemAttributes) else emptyMap(),
    )
}

/** Copy without the first entry equal to [value] (ignoring case). */
private fun List<String>.withoutOne(value: String?): List<String> {
    val index = value?.let { v -> indexOfFirst { it.equals(v, ignoreCase = true) } } ?: -1
    return if (index < 0) this else filterIndexed { i, _ -> i != index }
}

/** The new location typed in the form, or null when an existing one is picked. Call only on a valid form. */
fun StockActionForm.newLocation(now: Long, newId: () -> String): Location? =
    if (needsTarget && targetMode == LocationChoice.NEW) {
        Location(id = newId(), name = newLocationName.trim(), parentId = newLocationParentId, createdAt = now, updatedAt = now)
    } else {
        null
    }

// ---------- Units (display) ----------

/** One serial unit. [path] is null when the unit is out of stock (lent, lost). */
data class UnitRow(
    val itemId: String,
    val serial: String?,
    val status: ItemStatus,
    val path: String?,
    /** Location of its last movement, the default place when it comes back. */
    val lastLocationId: String?,
    val actions: List<UnitAction>,
    val canMove: Boolean,
)

/** [locations] should include soft-deleted ones so every unit has a path. */
fun unitRows(items: List<Item>, lastLocations: List<ItemLastLocation>, locations: List<Location>): List<UnitRow> {
    val paths = buildLocationOptions(locations).associate { it.id to it.path }
    val last = lastLocations.associate { it.itemId to it.locationId }
    return items.map { item ->
        UnitRow(
            itemId = item.id,
            serial = item.serialNumber,
            status = item.status,
            path = item.locationId?.let { paths[it] ?: it },
            lastLocationId = last[item.id],
            actions = unitActions(item.status),
            canMove = canMoveUnit(item.status),
        )
    }
}

// ---------- ViewModel ----------

data class StockActionUiState(
    val form: StockActionForm,
    val errors: StockActionErrors,
    /** Active locations as a tree, for the pickers. */
    val locations: List<StockLocationNode>,
    /** Path of [StockActionForm.sourceLocationId], or of the unit's location. */
    val sourcePath: String?,
    /** Stock at the source, for remove and move. */
    val availableMilli: Long?,
    /** Serial of the unit, for unit actions. */
    val unitSerial: String?,
    /** Stored status of the unit being edited (the form status may differ). */
    val currentStatus: ItemStatus? = null,
    /** Attributes of the article category: the fields of the unit attribute values. */
    val itemAttributes: List<CategoryAttributeWithDefinition> = emptyList(),
    /** Attribute values of the article, shown as placeholders for the unit ones. */
    val inheritedAttributes: JsonObject? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val canSave: Boolean get() = errors.isValid && !saving
}

private data class ActionStatus(val saving: Boolean = false, val failed: Boolean = false, val rejectedSerial: String? = null)

private data class ActionData(
    val stock: Map<String, Long>,
    val active: List<Location>,
    val all: List<Location>,
    val serials: List<String>,
    val items: List<Item>,
    val itemAttributes: List<CategoryAttributeWithDefinition> = emptyList(),
    val inheritedAttributes: JsonObject? = null,
)

/** Stock actions of one article (add, remove, move, serial units) and its unit list. */
@OptIn(ExperimentalUuidApi::class)
class StockActionViewModel(
    private val articleId: String,
    private val dao: StockActionDao,
    detailDao: ArticleDetailDao,
    stockDao: StockDao,
    attributeDao: AttributeDao,
    private val clock: Clock = Clock.System,
    private val newId: () -> String = { Uuid.random().toString() },
) : ViewModel() {
    /** Category attributes of the article and its own values (what a unit can override). */
    private val attributeSource: Flow<Pair<List<CategoryAttributeWithDefinition>, JsonObject?>> =
        detailDao.observeArticle(articleId).flatMapLatest { found ->
            val inherited = found?.article?.attributes
            val categoryId = found?.article?.categoryId
            if (categoryId == null) {
                flowOf(emptyList<CategoryAttributeWithDefinition>() to inherited)
            } else {
                attributeDao.observeCategoryAttributes(categoryId).map { it to inherited }
            }
        }

    private val data: Flow<ActionData> = combine(
        combine(
            detailDao.observeStockByLocation(articleId),
            stockDao.observeLocations(),
            detailDao.observeAllLocations(),
            detailDao.observeSerials(),
            detailDao.observeItems(articleId),
        ) { stock, active, all, serials, items ->
            ActionData(stock.associate { it.locationId to it.stockMilli }, active, all, serials, items)
        },
        attributeSource,
    ) { base, (attributes, inherited) -> base.copy(itemAttributes = attributes, inheritedAttributes = inherited) }

    val units: StateFlow<List<UnitRow>> = combine(
        detailDao.observeItems(articleId),
        detailDao.observeLastItemLocations(articleId),
        detailDao.observeAllLocations(),
    ) { items, last, locations -> unitRows(items, last, locations) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Null when the sheet is closed. */
    private val form = MutableStateFlow<StockActionForm?>(null)
    private val status = MutableStateFlow(ActionStatus())
    private var latest: StockActionUiState? = null
    private var latestData: ActionData? = null

    /** Bumped when the sheet opens or closes, so a late save result is ignored. */
    private var generation = 0

    val sheet: StateFlow<StockActionUiState?> = combine(form, data, status) { form, data, status ->
        latestData = data
        form?.let { buildState(it, data, status) }.also { latest = it }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun buildState(form: StockActionForm, data: ActionData, status: ActionStatus): StockActionUiState {
        val context = StockActionContext(data.stock, data.active.mapTo(HashSet()) { it.id }, data.serials, data.items, data.itemAttributes)
        val paths = buildLocationOptions(data.all).associate { it.id to it.path }
        val item = form.itemId?.let { id -> data.items.firstOrNull { it.id == id } }
        val source = form.sourceLocationId ?: item?.locationId
        return StockActionUiState(
            form = form,
            errors = form.validate(context, status.rejectedSerial),
            locations = data.active.toLocationTree(),
            sourcePath = source?.let { paths[it] ?: it },
            availableMilli = form.sourceLocationId?.let { data.stock[it] ?: 0 },
            unitSerial = item?.serialNumber,
            currentStatus = item?.status,
            itemAttributes = data.itemAttributes,
            inheritedAttributes = data.inheritedAttributes,
            saving = status.saving,
            saveFailed = status.failed,
        )
    }

    fun open(form: StockActionForm) {
        generation++
        status.value = ActionStatus()
        this.form.value = form
    }

    /** Opens a unit action. A return or a find starts on the unit's last location when it is still active. */
    fun openUnitAction(unit: UnitRow, action: UnitAction) {
        val active = latestData?.active.orEmpty()
        val target = unit.lastLocationId?.takeIf { id -> active.any { it.id == id } }
        open(StockActionForm(kind = StockActionKind.UNIT_STATUS, itemId = unit.itemId, unitAction = action, targetLocationId = target))
    }

    /** Opens the properties of a unit (serial, status, attributes), prefilled with the stored values. */
    fun openEditUnit(unit: UnitRow) {
        val item = latestData?.items?.firstOrNull { it.id == unit.itemId } ?: return
        open(
            StockActionForm(
                kind = StockActionKind.EDIT_UNIT,
                itemId = item.id,
                serial = item.serialNumber.orEmpty(),
                itemStatus = item.status,
                itemAttributes = item.attributes,
                archived = item.archived,
            ),
        )
    }

    fun update(transform: (StockActionForm) -> StockActionForm) {
        form.update { it?.let(transform) }
        status.update { it.copy(failed = false) }
    }

    fun close() {
        generation++
        form.value = null
        status.value = ActionStatus()
    }

    fun save() {
        val current = latest ?: return
        if (!current.canSave || status.value.saving || current.form != form.value) return
        status.update { it.copy(saving = true, failed = false) }
        val startedIn = generation
        viewModelScope.launch {
            try {
                write(current.form)
                if (startedIn == generation) close()
            } catch (e: CancellationException) {
                throw e
            } catch (e: DuplicateSerialException) {
                if (startedIn == generation) status.update { it.copy(saving = false, rejectedSerial = e.serial) }
            } catch (e: Exception) {
                // InsufficientStockException included: stock changed meanwhile, the observed stock also refreshes the error
                if (startedIn == generation) status.update { it.copy(saving = false, failed = true) }
            }
        }
    }

    private suspend fun write(form: StockActionForm) {
        val now = clock.now().toEpochMilliseconds()
        val newLocation = form.newLocation(now, newId)
        val target = newLocation?.id ?: form.targetLocationId
        val comment = form.comment.trim().ifEmpty { null }
        when (form.kind) {
            StockActionKind.ADD, StockActionKind.REMOVE -> {
                val quantity = requireNotNull(parseQuantity(form.quantity))
                val location = requireNotNull(if (form.kind == StockActionKind.ADD) form.sourceLocationId ?: target else form.sourceLocationId)
                val signed = if (form.kind == StockActionKind.ADD) quantity else -quantity
                dao.adjust(movement(location, signed, requireNotNull(form.reason), comment, now), newLocation)
            }
            StockActionKind.MOVE -> {
                val quantity = requireNotNull(parseQuantity(form.quantity))
                dao.transfer(
                    out = movement(requireNotNull(form.sourceLocationId), -quantity, MovementReason.TRANSFER, comment, now),
                    into = movement(requireNotNull(target), quantity, MovementReason.TRANSFER, comment, now),
                    newLocation = newLocation,
                )
            }
            StockActionKind.ADD_UNIT -> {
                val item = Item(
                    id = newId(),
                    articleId = articleId,
                    serialNumber = form.serial.trim().ifEmpty { null },
                    status = form.itemStatus,
                    locationId = requireNotNull(target),
                    attributes = form.itemAttributes,
                    createdAt = now,
                    updatedAt = now,
                )
                val movement = movement(target, UNIT_MILLI, MovementReason.PURCHASE, comment, now).copy(itemId = item.id)
                dao.addItem(item, movement, newLocation)
            }
            StockActionKind.EDIT_UNIT ->
                dao.updateItemProperties(
                    requireNotNull(form.itemId),
                    ItemChanges(form.serial.trim().ifEmpty { null }, form.itemStatus, form.itemAttributes, form.archived),
                    now,
                )
            StockActionKind.MOVE_UNIT ->
                dao.moveItem(requireNotNull(form.itemId), requireNotNull(target), newLocation, newId(), newId(), comment, now)
            StockActionKind.UNIT_STATUS ->
                dao.applyUnitAction(requireNotNull(form.itemId), requireNotNull(form.unitAction), target, newLocation, newId(), comment, now)
        }
    }

    private fun movement(locationId: String, quantityMilli: Long, reason: MovementReason, comment: String?, now: Long) = StockMovement(
        id = newId(),
        articleId = articleId,
        locationId = locationId,
        quantityMilli = quantityMilli,
        reason = reason,
        comment = comment,
        occurredAt = now,
        createdAt = now,
    )
}
