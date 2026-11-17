package ch.mathorck.easystock.services.backup

// The iOS target is not supported: backups are Android only. Compiles, throws when used.

internal fun unsupported(): Nothing = throw UnsupportedOperationException("Backups are not available on iOS")

@androidx.compose.runtime.Composable
actual fun rememberBackupService(): BackupService = unsupported()
