package ch.mathorck.easystock.reset

import androidx.compose.runtime.Composable
import androidx.room.execSQL
import androidx.room.useWriterConnection
import ch.mathorck.easystock.data.db.AppDatabase
import ch.mathorck.easystock.data.db.BackupDao
import ch.mathorck.easystock.sync.SyncCoordinator
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Erases all the data of this phone, as after a fresh install: the server session ([clearSession]:
 * tokens, open client), every table (server mode tables included), then the files the app owns
 * ([wipeFiles]: article photos, temporary backups and photos). Also the wipe of a logout.
 *
 * A full wipe the user asked for and confirmed in writing: rows are hard-deleted, the soft-delete
 * and append-only `stock_movement` rules don't apply (see [BackupDao.wipeEverything]).
 * A fresh install has no seed data, so nothing is recreated. The UI preferences are kept (not
 * data, stored by the platform): the app language and `AppPreferences` (phone colors).
 */
class DataReset(
    private val db: AppDatabase,
    /** Closes the server client and forgets the tokens. First: no call can run on the wiped data. */
    private val clearSession: suspend () -> Unit = {},
    private val wipeFiles: suspend () -> Unit,
) {
    /**
     * Database first: if the files then fail, only orphan files are left, never an article
     * pointing at a deleted photo. Not cancellable: the screen that starts it closes right after.
     */
    suspend fun run() = withContext(NonCancellable) {
        // Stops a running sync cycle first: no pulled row is written after the wipe
        SyncCoordinator.stopAndLock { wipe() }
    }

    private suspend fun wipe() {
        clearSession()
        db.backupDao().wipeEverything()
        // Deleted rows can stay readable in unused pages and in the write-ahead log: VACUUM
        // (outside a transaction) rebuilds the file, the checkpoint then writes back and empties the log
        db.useWriterConnection { connection ->
            connection.execSQL("VACUUM")
            connection.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
        }
        wipeFiles()
    }
}

/** The reset of the app database and files. */
@Composable
expect fun rememberDataReset(): DataReset

/**
 * True when [input] is the confirmation [word] (localized, e.g. "RÉINITIALISER"): spaces around
 * are ignored, and so are case and accents ("reinitialiser" matches). A German umlaut can also be
 * typed as two letters ("ZURUECKSETZEN"). An empty word never matches.
 */
fun matchesConfirmation(input: String, word: String): Boolean {
    if (word.isBlank()) return false
    val typed = foldConfirmation(input)
    return typed == foldConfirmation(word) || typed == foldConfirmation(word, umlautAsTwoLetters = true)
}

/** Upper case without accents: "Réinitialiser" gives "REINITIALISER". */
internal fun foldConfirmation(text: String, umlautAsTwoLetters: Boolean = false): String {
    val upper = text.trim().uppercase()
    return buildString {
        for (c in upper) {
            when {
                umlautAsTwoLetters && c in UMLAUTS -> append(UMLAUTS.getValue(c))
                else -> append(ACCENTS[c] ?: c)
            }
        }
    }
}

private val UMLAUTS = mapOf('Ä' to "AE", 'Ö' to "OE", 'Ü' to "UE")

private val ACCENTS: Map<Char, Char> = buildMap {
    fun put(letters: String, base: Char) = letters.forEach { put(it, base) }
    put("ÀÁÂÃÄÅĀĂĄ", 'A')
    put("ÇĆĈĊČ", 'C')
    put("ÈÉÊËĒĔĖĘĚ", 'E')
    put("ÌÍÎÏĨĪĬĮİ", 'I')
    put("ÑŃŅŇ", 'N')
    put("ÒÓÔÕÖØŌŎŐ", 'O')
    put("ÙÚÛÜŨŪŬŮŰŲ", 'U')
    put("ÝŸŶ", 'Y')
}
