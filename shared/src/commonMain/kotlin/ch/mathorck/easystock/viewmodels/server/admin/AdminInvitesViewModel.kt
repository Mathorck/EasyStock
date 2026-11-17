package ch.mathorck.easystock.viewmodels.server.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.data.dto.server.Invite
import ch.mathorck.easystock.data.dto.server.QrGrant
import ch.mathorck.easystock.enums.auth.Role
import ch.mathorck.easystock.services.server.AdminApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Single-use invites for full accounts. */
class AdminInvitesViewModel(private val api: AdminApi) : ViewModel() {
    private val _state = MutableStateFlow(AdminInvitesState())
    val state: StateFlow<AdminInvitesState> = _state.asStateFlow()

    private val invites = MutableStateFlow(Loaded<List<Invite>>())
    private val loader = Loader(viewModelScope, invites) { api.invites().sortedByDescending { it.createdAt } }

    init {
        viewModelScope.launch { invites.collect { list -> _state.update { it.copy(invites = list) } } }
    }

    /** Loads, or reloads with the current data shown (pull-to-refresh, screen opened again). */
    fun refresh() = loader.load(refresh = true)

    fun retry() = loader.load()

    fun create(role: Role) = act {
        val invite = api.createInvite(role)
        _state.update { it.copy(created = invite) }
    }

    fun closeCreated() = _state.update { it.copy(created = null) }

    fun cancel(inviteId: String) = act { api.cancelInvite(inviteId) }

    fun consumeFailure() = _state.update { it.copy(failure = null) }

    private fun act(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(busy = false) }
                loader.load(refresh = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, failure = e.toFailure()) }
            }
        }
    }
}

/** A grant the app can turn into a QR code. */
val QrGrant.usable: Boolean get() = !token.isNullOrBlank() || !qr.isNullOrBlank()
