package ch.mathorck.easystock.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

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
