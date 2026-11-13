package ch.mathorck.easystock.viewmodels.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.server.AdminApi
import ch.mathorck.easystock.server.AdminSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminSettingsState(
    val settings: Loaded<AdminSettings> = Loaded(),
    val saving: Boolean = false,
    val failure: ActionFailure? = null,
    val saved: Boolean = false,
)

/** Server settings: the server name. */
class AdminSettingsViewModel(private val api: AdminApi) : ViewModel() {
    private val _state = MutableStateFlow(AdminSettingsState())
    val state: StateFlow<AdminSettingsState> = _state.asStateFlow()

    private val settings = MutableStateFlow(Loaded<AdminSettings>())
    private val loader = Loader(viewModelScope, settings) { api.settings() }

    init {
        viewModelScope.launch { settings.collect { loaded -> _state.update { it.copy(settings = loaded) } } }
    }

    /** Loads, or reloads with the current data shown (pull-to-refresh, screen opened again). Not while saving. */
    fun refresh() {
        if (!_state.value.saving) loader.load(refresh = true)
    }

    fun retry() {
        if (!_state.value.saving) loader.load()
    }

    /** Saves [typed] as the server name, trimmed. Ignored unless [isNewServerName]. */
    fun setServerName(typed: String) {
        val current = settings.value.data ?: return
        if (_state.value.saving || !isNewServerName(typed, current.serverName)) return
        val sent = current.copy(serverName = typed.trim())
        // A load still running would read the old name and set it back after the save
        loader.cancel()
        _state.update { it.copy(saving = true, failure = null, saved = false) }
        viewModelScope.launch {
            try {
                val answer = api.updateSettings(sent)
                // A server that answers without the name keeps what was sent
                settings.value = Loaded(answer.copy(serverName = answer.serverName.ifBlank { sent.serverName }), loading = false)
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, failure = e.toFailure()) }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(failure = null, saved = false) }
}

/** The server name rule: [typed], trimmed, is not blank and differs from [current]. */
fun isNewServerName(typed: String, current: String): Boolean {
    val name = typed.trim()
    return name.isNotEmpty() && name != current
}

/** The Save button and the keyboard Done: a new name, loaded and nothing being saved. */
fun AdminSettingsState.canSaveServerName(typed: String): Boolean =
    !saving && settings.data?.let { isNewServerName(typed, it.serverName) } == true
