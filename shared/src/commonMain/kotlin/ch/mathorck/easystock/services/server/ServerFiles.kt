package ch.mathorck.easystock.services.server

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** [ServerFileStore.clear] outside any screen scope: for a screen that is closing. Errors are ignored. */
fun ServerFileStore.clearLater() {
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { runCatching { clear() } }
}

/** Save, share and pick files with the system pickers. */
interface ServerFiles : ServerFileStore {
    /** Lets the user choose where to save [file]. */
    fun save(file: LocalFile)

    /** Sends [file] to another app with the share sheet. */
    fun share(file: LocalFile)

    /** Lets the user pick a file; it is copied to the cache before `onPicked`. */
    fun pick()
}

/** Platform files of the admin screens. [onFailed] gets file errors (no space, no picker...). */
@Composable
expect fun rememberServerFiles(
    onPicked: (LocalFile) -> Unit,
    onSaved: () -> Unit,
    onFailed: () -> Unit,
): ServerFiles
