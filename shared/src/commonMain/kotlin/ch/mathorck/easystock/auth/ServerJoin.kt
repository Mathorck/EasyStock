package ch.mathorck.easystock.auth

import androidx.compose.runtime.Composable
import ch.mathorck.easystock.data.db.SessionDao
import ch.mathorck.easystock.data.model.ServerUser
import ch.mathorck.easystock.data.model.SyncState
import ch.mathorck.easystock.server.JoinApi
import ch.mathorck.easystock.server.ServerConnection
import ch.mathorck.easystock.server.TokenAnswer
import ch.mathorck.easystock.server.WireRole

/**
 * The empty-phone rule (docs/server-mode/02-app.md): a phone joins a server only when every synced table
 * is empty, soft-deleted rows included ([SessionDao.syncedRowCount]), and the image folder is empty.
 */
class EmptyPhoneCheck(
    private val syncedRowCount: suspend () -> Long,
    /** True when the photo folder holds at least one file. */
    private val hasPhotos: suspend () -> Boolean,
) {
    constructor(dao: SessionDao, hasPhotos: suspend () -> Boolean) : this(dao::syncedRowCount, hasPhotos)

    suspend fun isEmpty(): Boolean = syncedRowCount() == 0L && !hasPhotos()
}

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

/** What the connect screen needs from the platform. */
class JoinPlatform(
    val emptyCheck: EmptyPhoneCheck,
    val join: ServerJoin,
    /** A client without token for [ServerConnection] (OkHttp pinned to its fingerprint on Android). */
    val joinApi: (ServerConnection) -> JoinApi,
    /** Sent with every join call, shown in the admin device list. */
    val deviceName: String,
    val appVersion: String,
)

@Composable
expect fun rememberJoinPlatform(): JoinPlatform
