package ch.mathorck.easystock.services.auth

import androidx.compose.runtime.Composable
import ch.mathorck.easystock.data.dao.sync.SessionDao
import ch.mathorck.easystock.data.dto.server.TokenAnswer
import ch.mathorck.easystock.data.model.server.ServerUser
import ch.mathorck.easystock.data.model.sync.SyncState
import ch.mathorck.easystock.services.server.ServerConnection
import ch.mathorck.easystock.services.server.WireRole

/** The role stored in `sync_state.role` for a role name of the server ([Role.fromDb] reads it back). */
fun storedRoleOf(wireRole: String): String = WireRole.parse(wireRole)?.dbValue ?: wireRole

/**
 * Writes a successful join on this phone. Order, so that a crash at any point leaves a standalone phone
 * (standalone = no `sync_state` row):
 * 1. the refresh token, in the session file (alone it does nothing; [forgetOrphanSession] drops it at
 *    the next start);
 * 2. `sync_state` and the own `server_user` row, in one transaction, only if the phone is still empty.
 */
class ServerJoin(private val dao: SessionDao, private val session: SessionStore) {
    /** False when the phone is no longer empty or already joined: nothing is kept. */
    suspend fun commit(connection: ServerConnection, answer: TokenAnswer): Boolean {
        val user = requireNotNull(answer.user) { "Token answer without user" }
        session.clear()
        session.saveRefreshToken(answer.refreshToken)
        val joined = runCatching {
            dao.join(
                SyncState(
                    serverUrl = connection.serverUrl,
                    certFingerprint = connection.fingerprint,
                    userId = user.id,
                    role = storedRoleOf(user.role),
                    since = 0,
                    lastSyncAt = null,
                    applying = false,
                ),
                ServerUser(user.id, user.displayName),
            )
        }
        if (joined.getOrNull() != true) {
            session.clear()
            joined.exceptionOrNull()?.let { throw it }
            return false
        }
        session.accessToken = answer.accessToken
        return true
    }

    /** At start: a refresh token without `sync_state` is left by a join that stopped half way. */
    suspend fun forgetOrphanSession() {
        if (dao.syncState() == null && session.refreshToken() != null) session.clear()
    }
}

@Composable
expect fun rememberJoinPlatform(): JoinPlatform
