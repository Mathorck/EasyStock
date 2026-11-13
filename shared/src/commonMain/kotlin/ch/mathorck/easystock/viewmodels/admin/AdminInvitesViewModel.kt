package ch.mathorck.easystock.viewmodels.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.mathorck.easystock.auth.Role
import ch.mathorck.easystock.server.AdminApi
import ch.mathorck.easystock.server.Invite
import ch.mathorck.easystock.server.PublicInvite
import ch.mathorck.easystock.server.QrGrant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminInvitesState(
    val invites: Loaded<List<Invite>> = Loaded(),
    val busy: Boolean = false,
    val failure: ActionFailure? = null,
    /** The invite just created, to show full screen. Its token is only known now. */
    val created: Invite? = null,
)

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

data class PublicInviteState(
    val invite: Loaded<PublicInvite> = Loaded(),
    val busy: Boolean = false,
    val failure: ActionFailure? = null,
    /**
     * The token of the printed code, kept in memory only while this screen is open. The server may keep
     * only a hash: then the QR code and the PDF are only possible right after [regenerate][PublicInviteViewModel.regenerate].
     */
    val grant: QrGrant? = null,
    /** The new code was made: the old printed sheets stop working. */
    val regenerated: Boolean = false,
    /** Name printed on the sheet (`server_name` setting, else `/server-info`). Null until known. */
    val serverName: String? = null,
)

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
            val name = runCatching { api.settings().serverName }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: runCatching { api.serverInfo().serverName }.getOrNull()?.takeIf { it.isNotBlank() }
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

/** A grant the app can turn into a QR code. */
val QrGrant.usable: Boolean get() = !token.isNullOrBlank() || !qr.isNullOrBlank()
