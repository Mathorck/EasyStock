package ch.mathorck.easystock.reset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ch.mathorck.easystock.auth.rememberSessionStore
import ch.mathorck.easystock.data.db.rememberAppDatabase

// iOS stores no photos or temporary files yet: only the database is wiped
@Composable
actual fun rememberDataReset(): DataReset {
    val db = rememberAppDatabase()
    val session = rememberSessionStore()
    return remember(db) { DataReset(db, wipeFiles = {}, clearSession = { session.clear() }) }
}
