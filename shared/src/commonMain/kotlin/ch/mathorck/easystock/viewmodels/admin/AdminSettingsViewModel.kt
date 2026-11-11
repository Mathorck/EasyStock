package ch.mathorck.easystock.viewmodels.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.auth.Role
import ch.mathorck.easystock.server.AdminApi
import ch.mathorck.easystock.server.AdminSettings
import ch.mathorck.easystock.server.WireRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Retention choices of the movement authors, in days. Null = keep forever. */
val RETENTION_CHOICES: List<Int?> = listOf(null, 30, 90, 180, 365, 730)

data class AdminSettingsState(
    val settings: Loaded<AdminSettings> = Loaded(),
    val saving: Boolean = false,
    val failure: ActionFailure? = null,
    val saved: Boolean = false,
) {
    /** Roles with alerts by default, null when the server has no such setting (the screen hides it). */
    val alertRoles: Set<Role>? get() = settings.data?.alertRoles?.mapNotNullTo(mutableSetOf(), WireRole::parse)
}

/** Server settings: retention of the movement authors, default alert roles. */
class AdminSettingsViewModel(private val api: AdminApi) : ViewModel() {
    private val _state = MutableStateFlow(AdminSettingsState())
    val state: StateFlow<AdminSettingsState> = _state.asStateFlow()

    private val settings = MutableStateFlow(Loaded<AdminSettings>())
    private val loader = Loader(viewModelScope, settings) { api.settings() }

    init {
        viewModelScope.launch { settings.collect { loaded -> _state.update { it.copy(settings = loaded) } } }
    }

    /** Loads, or reloads with the current data shown (pull-to-refresh, screen opened again). */
    fun refresh() = loader.load(refresh = true)

    fun retry() = loader.load()

    fun setRetention(days: Int?) = save { it.copy(movementAuthorRetentionDays = days) }

    fun setAlertRole(role: Role, on: Boolean) = save { current ->
        val roles = current.alertRoles ?: return@save current
        val wire = WireRole.of(role)
        // Also drop the other spelling of the same role (member/editor)
        val others = roles.filter { WireRole.parse(it) != role }
        current.copy(alertRoles = if (on) others + wire else others)
    }

    fun consumeMessage() = _state.update { it.copy(failure = null, saved = false) }

    private fun save(change: (AdminSettings) -> AdminSettings) {
        val current = settings.value.data ?: return
        if (_state.value.saving) return
        val next = change(current)
        if (next == current) return
        _state.update { it.copy(saving = true, failure = null, saved = false) }
        viewModelScope.launch {
            try {
                val answer = api.updateSettings(next)
                // A server that answers without the body fields keeps what was sent
                settings.value = Loaded(merge(next, answer), loading = false)
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, failure = e.toFailure()) }
            }
        }
    }

    private fun merge(sent: AdminSettings, answer: AdminSettings) = AdminSettings(
        serverName = answer.serverName ?: sent.serverName,
        movementAuthorRetentionDays = if (answer == AdminSettings()) sent.movementAuthorRetentionDays else answer.movementAuthorRetentionDays,
        alertRoles = answer.alertRoles ?: sent.alertRoles,
    )
}
