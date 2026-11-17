package ch.mathorck.easystock.composables.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import ch.mathorck.easystock.enums.backup.BackupError
import ch.mathorck.easystock.services.backup.BackupFiles
import ch.mathorck.easystock.services.backup.ExportedBackup
import ch.mathorck.easystock.services.backup.ImportInput
import ch.mathorck.easystock.services.backup.QrMatrix

// The iOS target is not supported: no QR encoder, scanner or file pickers. Compiles only.

actual fun encodeQr(text: String): QrMatrix? = null

@Composable
actual fun QrScanner(onScanned: (String) -> Unit, modifier: Modifier) {
}

@Composable
actual fun rememberBackupFiles(
    onOpened: (ImportInput.File) -> Unit,
    onSaved: () -> Unit,
    onError: (BackupError) -> Unit,
): BackupFiles = remember {
    object : BackupFiles {
        override fun save(export: ExportedBackup) = onError(BackupError.FAILED)

        override fun share(export: ExportedBackup) = onError(BackupError.FAILED)

        override fun open() = onError(BackupError.FAILED)
    }
}

@Composable
actual fun rememberIsChangingConfigurations(): () -> Boolean = { false }

@Composable
actual fun SecureScreen() {
}

actual fun secureFullWidthDialogProperties(): DialogProperties = DialogProperties(usePlatformDefaultWidth = false)
