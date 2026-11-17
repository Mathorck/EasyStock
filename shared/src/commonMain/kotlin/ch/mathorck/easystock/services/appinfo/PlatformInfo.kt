package ch.mathorck.easystock.services.appinfo

import ch.mathorck.easystock.enums.nfc.NfcAvailability

/** Facts only the platform knows. Everything here is technical, none is personal data. */
data class PlatformInfo(
    val versionName: String,
    val versionCode: Long,
    val debugBuild: Boolean,
    val applicationId: String,
    /** Epoch milliseconds (UTC), null when unknown. */
    val installedAt: Long?,
    val updatedAt: Long?,
    val os: String,
    val device: String,
    /** The locale the system uses for the app. */
    val locale: String,
    val nfc: NfcAvailability,
    val databaseBytes: Long,
    val photoBytes: Long,
    val photoCount: Int,
    val freeBytes: Long?,
)
