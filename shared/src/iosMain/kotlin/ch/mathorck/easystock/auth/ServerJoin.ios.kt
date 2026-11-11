package ch.mathorck.easystock.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ch.mathorck.easystock.data.db.rememberAppDatabase

// TODO(server mode on iOS): only Android is delivered. Joining fails at the first call (no HTTP engine).
@Composable
actual fun rememberJoinPlatform(): JoinPlatform {
    val db = rememberAppDatabase()
    val session = rememberSessionStore()
    return remember(db) {
        JoinPlatform(
            emptyCheck = EmptyPhoneCheck(db.sessionDao()) { false },
            join = ServerJoin(db.sessionDao(), session),
            joinApi = { throw UnsupportedOperationException("Server mode is not available on iOS yet") },
            deviceName = "iPhone",
            appVersion = "0.0.0",
        )
    }
}
