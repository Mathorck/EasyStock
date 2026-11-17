package ch.mathorck.easystock.enums.backup

/** Kind of a file or payload, from its first bytes. */
enum class BackupKind { ENCRYPTED, ZIP, QR_BUNDLE, UNKNOWN }
