package ch.mathorck.easystock.services.reset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ch.mathorck.easystock.data.db.rememberAppDatabase
import ch.mathorck.easystock.services.auth.rememberSessionStore

// iOS stores no photos or temporary files yet: only the database is wiped
@Composable
actual fun rememberDataReset(): DataReset {
    val db = rememberAppDatabase()
    val session = rememberSessionStore()
    return remember(db) { DataReset(db, wipeFiles = { _ -> }, clearSession = { session.clear() }) }
}
