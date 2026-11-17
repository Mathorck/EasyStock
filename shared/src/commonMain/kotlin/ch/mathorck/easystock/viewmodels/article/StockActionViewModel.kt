package ch.mathorck.easystock.viewmodels.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.data.dao.article.ArticleDetailDao
import ch.mathorck.easystock.data.dao.article.ItemChanges
import ch.mathorck.easystock.data.dao.article.ItemLastLocation
import ch.mathorck.easystock.data.dao.attributes.AttributeDao
import ch.mathorck.easystock.data.dao.attributes.CategoryAttributeWithDefinition
import ch.mathorck.easystock.data.dao.stock.CREATABLE_STATUSES
import ch.mathorck.easystock.data.dao.stock.MAX_SERIAL_LENGTH
import ch.mathorck.easystock.data.dao.stock.STOCK_IN_REASONS
import ch.mathorck.easystock.data.dao.stock.STOCK_OUT_REASONS
import ch.mathorck.easystock.data.dao.stock.StockActionDao
import ch.mathorck.easystock.data.dao.stock.StockDao
import ch.mathorck.easystock.data.dao.stock.UNIT_MILLI
import ch.mathorck.easystock.data.dao.stock.canMoveUnit
import ch.mathorck.easystock.data.dao.stock.editableStatuses
import ch.mathorck.easystock.data.dao.stock.unitActions
import ch.mathorck.easystock.data.dao.stock.unitTransition
import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.data.model.article.Item
import ch.mathorck.easystock.data.model.location.Location
import ch.mathorck.easystock.data.model.stock.StockMovement
import ch.mathorck.easystock.enums.article.StockActionError
import ch.mathorck.easystock.enums.article.StockActionKind
import ch.mathorck.easystock.enums.attributes.validateAttributes
import ch.mathorck.easystock.enums.location.LocationChoice
import ch.mathorck.easystock.enums.stock.MovementReason
import ch.mathorck.easystock.enums.stock.UnitAction
import ch.mathorck.easystock.exceptions.stock.DuplicateSerialException
import ch.mathorck.easystock.services.attributes.AttributeCatalog
import ch.mathorck.easystock.util.parseQuantity
import ch.mathorck.easystock.viewmodels.location.buildLocationOptions
import ch.mathorck.easystock.viewmodels.stock.SerialIndex
import ch.mathorck.easystock.viewmodels.stock.toLocationTree
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
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

/** Max length of `location.name` (docs/schema.dbml). */
private const val LOCATION_NAME_MAX = 100

/** Reasons the user can pick for [kind]. Empty when the reason is fixed (transfer, units). */
fun reasonsFor(kind: StockActionKind): List<MovementReason> = when (kind) {
    StockActionKind.ADD -> STOCK_IN_REASONS
    StockActionKind.REMOVE -> STOCK_OUT_REASONS
    else -> emptyList()
}

internal fun defaultReason(kind: StockActionKind): MovementReason? = when (kind) {
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

/** Case folding with the same result as `equals(ignoreCase = true)`: equal when the folded strings are equal. */
internal fun String.foldCase(): String = buildString(length) {
    for (c in this@foldCase) append(c.uppercaseChar().lowercaseChar())
}

/**
 * Optional serial number: max length, unique ignoring case among [existing] (deleted items included).
 * [ownSerial]: the stored serial of the edited unit, it may keep it. [rejected]: refused by the database.
 */
fun validateSerial(text: String, existing: SerialIndex, ownSerial: String? = null, rejected: String? = null): StockActionError? {
    val value = text.trim()
    return when {
        value.isEmpty() -> null
        value.length > MAX_SERIAL_LENGTH -> StockActionError.TOO_LONG
        existing.isTaken(value, ownSerial) || rejected?.equals(value, ignoreCase = true) == true -> StockActionError.DUPLICATE_SERIAL
        else -> null
    }
}

// ---------- Unit properties (pure) ----------

/**
 * Attributes of the category as optional overrides on a unit: a unit never has to repeat the article value,
 * so "required" does not apply.
 */
fun itemAttributeOverrides(attributes: List<CategoryAttributeWithDefinition>): List<CategoryAttributeWithDefinition> =
    attributes.map { it.copy(link = it.link.copy(required = false)) }

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
            StockActionKind.ADD_UNIT -> validateSerial(serial, context.serials, rejected = rejectedSerial)
            // The unit may keep its own serial
            StockActionKind.EDIT_UNIT -> validateSerial(serial, context.serials, item?.serialNumber, rejectedSerial)
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

/** The new location typed in the form, or null when an existing one is picked. Call only on a valid form. */
fun StockActionForm.newLocation(now: Long, newId: () -> String): Location? =
    if (needsTarget && targetMode == LocationChoice.NEW) {
        Location(id = newId(), name = newLocationName.trim(), parentId = newLocationParentId, createdAt = now, updatedAt = now)
    } else {
        null
    }

/** [locations] should include soft-deleted ones so every unit has a path. */
fun unitRows(
    items: List<Item>,
    lastLocations: List<ItemLastLocation>,
    locations: List<Location>,
    article: Article? = null,
    catalog: AttributeCatalog? = null,
): List<UnitRow> {
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
            properties = catalog?.unitRows(article?.categoryId, article?.attributes, item.attributes).orEmpty(),
        )
    }
}

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
            // Indexed once per change: the form checks the typed serial on every keystroke
            detailDao.observeSerials().map(::SerialIndex),
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
        detailDao.observeArticle(articleId),
        combine(attributeDao.observeDefinitions(), attributeDao.observeActiveLinks(), ::AttributeCatalog),
    ) { items, last, locations, found, catalog -> unitRows(items, last, locations, found?.article, catalog) }
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
