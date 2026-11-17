package ch.mathorck.easystock.viewmodels.server.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.data.dto.server.PublicInvite
import ch.mathorck.easystock.services.server.AdminApi
import ch.mathorck.easystock.util.runCatchingCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The printed QR code for Guests. */
class PublicInviteViewModel(private val api: AdminApi) : ViewModel() {
    private val _state = MutableStateFlow(PublicInviteState())
    val state: StateFlow<PublicInviteState> = _state.asStateFlow()

    private val invite = MutableStateFlow(Loaded<PublicInvite>())
    private val loader = Loader(viewModelScope, invite) { api.publicInvite() }

    init {
        viewModelScope.launch {
            invite.collect { loaded ->
                _state.update { state ->
                    state.copy(invite = loaded, grant = loaded.data?.grant?.takeIf { it.usable } ?: state.grant)
                }
            }
        }
        loadServerName()
    }

    private fun loadServerName() {
        viewModelScope.launch {
            val name = runCatchingCancellable { api.settings().serverName }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: runCatchingCancellable { api.serverInfo().serverName }.getOrNull()?.takeIf { it.isNotBlank() }
            _state.update { it.copy(serverName = name) }
        }
    }

    /** Loads, or reloads with the current data shown (pull-to-refresh, screen opened again). Also reloads the server name, which an admin may have renamed. */
    fun refresh() {
        loadServerName()
        loader.load(refresh = true)
    }

    fun retry() = loader.load()

    fun setEnabled(enabled: Boolean) = update(enabled = enabled)

    /** [limit] null = no limit. */
    fun setGuestLimit(limit: Int?) = update(limit = limit)

    private fun update(enabled: Boolean? = null, limit: Int? = UNCHANGED) {
        val current = _state.value.invite.data ?: return
        act {
            api.updatePublicInvite(
                enabled = enabled ?: current.enabled,
                guestLimit = if (limit == UNCHANGED) current.guestLimit else limit,
            )
        }
    }

    /** New token: old printed sheets stop working. */
    fun regenerate() = act(regenerating = true) { api.regeneratePublicInvite() }

    fun consumeFailure() = _state.update { it.copy(failure = null, regenerated = false) }

    private fun act(regenerating: Boolean = false, block: suspend () -> PublicInvite) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, failure = null, regenerated = false) }
        viewModelScope.launch {
            try {
                val answer = block()
                invite.value = Loaded(answer, loading = false)
                _state.update { it.copy(busy = false, regenerated = regenerating) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, failure = e.toFailure()) }
            }
        }
    }

    private companion object {
        /** Marks "keep the limit": no real limit is negative. */
        const val UNCHANGED = -1
    }
}
