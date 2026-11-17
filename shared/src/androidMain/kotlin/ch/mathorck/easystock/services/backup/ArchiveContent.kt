package ch.mathorck.easystock.services.backup

import ch.mathorck.easystock.data.dto.backup.BackupData
import ch.mathorck.easystock.data.dto.backup.BackupManifest

// Pure JVM (java.util.zip, java.io): runs on the device and in the host tests.

/** Archive content without the photos, which stay in the file until copied. */
class ArchiveContent(val manifest: BackupManifest, val data: BackupData, val photos: Set<String>)
