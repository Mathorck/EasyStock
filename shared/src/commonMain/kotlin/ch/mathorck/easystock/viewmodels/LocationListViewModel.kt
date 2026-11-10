package ch.mathorck.easystock.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.data.db.LocationDao
import ch.mathorck.easystock.data.db.LocationException
import ch.mathorck.easystock.data.model.Location
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A row of the location tree. [articleCount]: articles with stock directly here. */
data class LocationRow(val location: Location, val depth: Int, val articleCount: Int, val childCount: Int)

/** Raw input of the location dialog. [parentId] null = top level. */
data class LocationForm(val name: String = "", val parentId: String? = null)

/** The open dialog. [editing] is null for a new location. [parents]: where it can go (never itself or below). */
data class LocationDialogState(
    val editing: Location?,
    val form: LocationForm,
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
    val saving: Boolean = false,
    val failed: Boolean = false,
)

/** Settings > Locations: tree list, create (also under a parent), rename, move and soft delete. */
@OptIn(ExperimentalUuidApi::class)
class LocationListViewModel(
    private val dao: LocationDao,
    private val clock: Clock = Clock.System,
    private val newId: () -> String = { Uuid.random().toString() },
) : ViewModel() {
    private val dialog = MutableStateFlow<LocationDialogInput?>(null)
    private val pendingDelete = MutableStateFlow<LocationDeleteRequest?>(null)
    private val message = MutableStateFlow<LocationMessage?>(null)

    val state: StateFlow<LocationListUiState> = combine(
        combine(dao.observeLocations(), dao.observeArticleCounts()) { l, c -> l to c },
        dialog, pendingDelete, message,
    ) { (locations, counts), input, pending, message ->
        val articleCounts = counts.associate { it.locationId to it.articleCount }
        val childCounts = locations.groupingBy { it.parentId }.eachCount()
        LocationListUiState(
            loading = false,
            rows = locations.toLocationTree().map {
                LocationRow(it.location, it.depth, articleCounts[it.location.id] ?: 0, childCounts[it.location.id] ?: 0)
            },
            dialog = input?.let {
                val editing = it.editing
                LocationDialogState(
                    editing = editing,
                    form = it.form,
                    error = validateLocationName(it.form.name, locations.childrenOf(it.form.parentId), editing?.id),
                    parents = (if (editing == null) locations else locations.validParentsFor(editing.id)).toLocationTree(),
                    saving = it.saving,
                    saveFailed = it.failed,
                )
            },
            pendingDelete = pending,
            message = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocationListUiState())

    /** New location at the top level, or under [parent] ("Add sub-location"). */
    fun startCreate(parent: Location? = null) {
        dialog.value = LocationDialogInput(editing = null, form = LocationForm(parentId = parent?.id))
    }

    fun startEdit(location: Location) {
        dialog.value = LocationDialogInput(editing = location, form = LocationForm(location.name, location.parentId))
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
                    dao.createLocation(newId(), input.form.name, input.form.parentId, now)
                } else {
                    dao.updateLocation(editing.id, input.form.name, input.form.parentId, now)
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
