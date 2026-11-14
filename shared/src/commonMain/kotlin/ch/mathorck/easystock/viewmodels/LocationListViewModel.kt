package ch.mathorck.easystock.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.data.db.LocationDao
import ch.mathorck.easystock.data.db.LocationException
import ch.mathorck.easystock.data.model.Location
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A row of the location tree. [articleCount]: articles with stock directly here. */
data class LocationRow(val location: Location, val depth: Int, val articleCount: Int, val childCount: Int)

/** Raw input of the location dialog. [parentId] null = top level. */
data class LocationForm(
    val name: String = "",
    val parentId: String? = null,
    /** Key of LocationIcons, null = default. */
    val icon: String? = null,
    /** Key of LocationColors, null = default. */
    val color: String? = null,
)

/**
 * The open dialog. [editing] is null for a new location (its current row while editing, so its tag is
 * up to date). [parents]: where it can go (never itself or below). [tagId]: the NFC tag a new location gets.
 */
data class LocationDialogState(
    val editing: Location?,
    val form: LocationForm,
    val tagId: String? = null,
    val error: LocationError?,
    val parents: List<StockLocationNode>,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
) {
    val canSave: Boolean get() = error == null && !saving
}

/** Delete waiting for the user's confirmation. [descendantCount]: sub-locations deleted with it. */
data class LocationDeleteRequest(val location: Location, val descendantCount: Int)

/** One-shot feedback, shown once as a snackbar. */
sealed interface LocationMessage {
    data class HasStock(val name: String) : LocationMessage
    data class Deleted(val count: Int) : LocationMessage
    data object DeleteFailed : LocationMessage
}

data class LocationListUiState(
    val loading: Boolean = true,
    val rows: List<LocationRow> = emptyList(),
    val dialog: LocationDialogState? = null,
    val pendingDelete: LocationDeleteRequest? = null,
    val message: LocationMessage? = null,
)

private data class LocationDialogInput(
    val editing: Location?,
    val form: LocationForm,
    val tagId: String? = null,
    val saving: Boolean = false,
    val failed: Boolean = false,
)

/** The active locations, indexed once per database change (not on every dialog keystroke). */
private class LocationIndex(val locations: List<Location>) {
    val byId: Map<String, Location> = locations.associateBy { it.id }
    val children: Map<String?, List<Location>> = locations.groupBy { it.parentId }
    val tree: List<StockLocationNode> = locations.toLocationTree()
}

/**
 * Settings > Locations: tree list, create (also under a parent), rename, move and soft delete.
 * [computeDispatcher] builds the tree off the main thread (tests pass their own).
 */
@OptIn(ExperimentalUuidApi::class)
class LocationListViewModel(
    private val dao: LocationDao,
    private val clock: Clock = Clock.System,
    private val newId: () -> String = { Uuid.random().toString() },
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val dialog = MutableStateFlow<LocationDialogInput?>(null)
    private val pendingDelete = MutableStateFlow<LocationDeleteRequest?>(null)
    private val message = MutableStateFlow<LocationMessage?>(null)
    private val _created = MutableStateFlow<String?>(null)

    /** ID of the location just created, until [consumeCreated] (the Scan page opens it). */
    val created: StateFlow<String?> = _created

    private val index: Flow<LocationIndex> = dao.observeLocations()
        .map(::LocationIndex)
        .flowOn(computeDispatcher)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val rows: Flow<List<LocationRow>> = combine(index, dao.observeArticleCounts()) { index, counts ->
        val articleCounts = counts.associate { it.locationId to it.articleCount }
        index.tree.map {
            LocationRow(it.location, it.depth, articleCounts[it.location.id] ?: 0, index.children[it.location.id]?.size ?: 0)
        }
    }

    /** Where the open dialog's location can go: rebuilt when the locations or the edited location change. */
    private val parents: Flow<List<StockLocationNode>> = combine(
        index,
        // Null: no dialog; "": a new location
        dialog.map { input -> input?.let { it.editing?.id ?: "" } }.distinctUntilChanged(),
    ) { index, editingId ->
        when (editingId) {
            null -> emptyList()
            "" -> index.tree
            else -> index.locations.validParentsFor(editingId).toLocationTree()
        }
    }

    val state: StateFlow<LocationListUiState> = combine(
        combine(index, rows, parents, ::Triple),
        dialog, pendingDelete, message,
    ) { (index, rows, parents), input, pending, message ->
        LocationListUiState(
            loading = false,
            rows = rows,
            dialog = input?.let {
                val editing = it.editing?.let { e -> index.byId[e.id] ?: e }
                LocationDialogState(
                    editing = editing,
                    form = it.form,
                    tagId = it.tagId,
                    error = validateLocationName(it.form.name, index.children[it.form.parentId].orEmpty(), editing?.id),
                    parents = parents,
                    saving = it.saving,
                    saveFailed = it.failed,
                )
            },
            pendingDelete = pending,
            message = message,
        )
    }.flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocationListUiState())

    /** New location at the top level, or under [parent] ("Add sub-location"), with the NFC tag [tagId] if any. */
    fun startCreate(parent: Location? = null, tagId: String? = null) {
        dialog.value = LocationDialogInput(editing = null, form = LocationForm(parentId = parent?.id), tagId = tagId)
    }

    fun consumeCreated() {
        _created.value = null
    }

    fun startEdit(location: Location) {
        dialog.value = LocationDialogInput(editing = location, form = LocationForm(location.name, location.parentId, location.icon, location.color))
    }

    fun updateForm(transform: (LocationForm) -> LocationForm) {
        dialog.update { input -> input?.copy(form = transform(input.form), failed = false) }
    }

    fun cancel() {
        dialog.value = null
        pendingDelete.value = null
    }

    fun consumeMessage() {
        message.value = null
    }

    fun save() {
        val input = dialog.value ?: return
        val current = state.value.dialog ?: return
        if (!current.canSave || current.form != input.form) return
        dialog.value = input.copy(saving = true, failed = false)
        viewModelScope.launch {
            try {
                val now = clock.now().toEpochMilliseconds()
                val editing = input.editing
                if (editing == null) {
                    val id = newId()
                    dao.createLocation(id, input.form.name, input.form.parentId, now, input.form.icon, input.form.color, input.tagId)
                    _created.value = id
                } else {
                    dao.updateLocation(editing.id, input.form.name, input.form.parentId, now, input.form.icon, input.form.color)
                }
                dialog.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: LocationException) {
                // The location or its parent was deleted meanwhile, or the name is taken: close or show a failure
                if (e.error == LocationError.NOT_FOUND || e.error == LocationError.PARENT_NOT_FOUND) {
                    dialog.value = null
                } else {
                    dialog.update { it?.copy(saving = false, failed = true) }
                }
            } catch (e: Exception) {
                dialog.update { it?.copy(saving = false, failed = true) }
            }
        }
    }

    /** First step of a delete: blocked while stock remains, otherwise waits for the confirmation. */
    fun requestDelete(location: Location) {
        viewModelScope.launch {
            try {
                if (dao.subtreeHoldsStock(location.id)) {
                    message.value = LocationMessage.HasStock(location.name)
                } else {
                    val descendants = (dao.activeLocations().subtreeIds(location.id).size - 1).coerceAtLeast(0)
                    pendingDelete.value = LocationDeleteRequest(location, descendants)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = LocationMessage.DeleteFailed
            }
        }
    }

    /** Soft-deletes the location and its descendants in one transaction. */
    fun confirmDelete() {
        val request = pendingDelete.value ?: return
        pendingDelete.value = null
        viewModelScope.launch {
            try {
                val count = dao.softDeleteLocation(request.location.id, clock.now().toEpochMilliseconds())
                if (dialog.value?.editing?.id == request.location.id) dialog.value = null
                message.value = LocationMessage.Deleted(count)
            } catch (e: CancellationException) {
                throw e
            } catch (e: LocationException) {
                message.value = when (e.error) {
                    LocationError.HAS_STOCK -> LocationMessage.HasStock(request.location.name)
                    else -> LocationMessage.DeleteFailed
                }
            } catch (e: Exception) {
                message.value = LocationMessage.DeleteFailed
            }
        }
    }
}
