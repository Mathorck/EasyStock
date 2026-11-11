package ch.mathorck.easystock.appinfo

import ch.mathorck.easystock.nfc.NfcAvailability
import kotlin.math.round

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

/** Row counts of the active data (soft-deleted rows are excluded, stock movements have none). */
data class DataCounts(
    val articles: Int = 0,
    val locations: Int = 0,
    val items: Int = 0,
    val stockMovements: Int = 0,
    val nfcTags: Int = 0,
    val openReviews: Int = 0,
)

/** All values of the app info screen. */
data class AppInfo(
    val platform: PlatformInfo,
    val counts: DataCounts,
    val dbVersion: Int,
    val backupFormatVersion: Int,
    /** Language chosen in the app, "system" when none. */
    val appLanguage: String,
    /** Already formatted in the user's time zone. */
    val installedAt: String?,
    val updatedAt: String?,
)

/** Binary units (1 KB = 1024 bytes), one decimal from KB up: 1536 -> "1.5 KB". */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    var tenths = round(value * 10).toLong()
    // Rounding can reach 1024.0 ("1023.97 KB"): move to the next unit
    if (tenths >= 10240 && unit < units.lastIndex) {
        unit++
        tenths = round(value / 1024 * 10).toLong()
    }
    return "${tenths / 10}.${tenths % 10} ${units[unit]}"
}

/**
 * Plain-text block for bug reports. English labels on purpose (read by the developer).
 * Counts and technical values only: no article names, serial numbers or tag IDs.
 */
fun AppInfo.toReportText(): String = buildString {
    val p = platform
    appendLine("EasyStock app info")
    appendLine()
    appendLine("[App]")
    appendLine("Version: ${p.versionName} (${p.versionCode})")
    appendLine("Build type: ${if (p.debugBuild) "debug" else "release"}")
    appendLine("Application id: ${p.applicationId}")
    installedAt?.let { appendLine("Installed: $it") }
    updatedAt?.let { appendLine("Updated: $it") }
    appendLine()
    appendLine("[Data] (active rows only, soft-deleted excluded)")
    appendLine("Database schema version: $dbVersion")
    appendLine("Backup format version: $backupFormatVersion")
    appendLine("Articles: ${counts.articles}")
    appendLine("Locations: ${counts.locations}")
    appendLine("Items (units): ${counts.items}")
    appendLine("Stock movements: ${counts.stockMovements}")
    appendLine("NFC tags linked: ${counts.nfcTags}")
    appendLine("Open reviews: ${counts.openReviews}")
    appendLine("Database size: ${formatBytes(p.databaseBytes)}")
    appendLine("Photos: ${p.photoCount} (${formatBytes(p.photoBytes)})")
    appendLine()
    appendLine("[Device]")
    appendLine("OS: ${p.os}")
    appendLine("Device: ${p.device}")
    appendLine("App language: $appLanguage")
    appendLine("System locale: ${p.locale}")
    appendLine("NFC: ${p.nfc.reportName()}")
    p.freeBytes?.let { appendLine("Free storage: ${formatBytes(it)}") }
}.trimEnd()

fun NfcAvailability.reportName(): String = when (this) {
    NfcAvailability.Enabled -> "enabled"
    NfcAvailability.Disabled -> "disabled"
    NfcAvailability.Unavailable -> "not present"
    NfcAvailability.Unknown -> "unknown"
}

const val REPO_URL = "https://github.com/Mathorck/EasyStock"
const val ISSUES_URL = "$REPO_URL/issues"
const val LICENSE_URL = "$REPO_URL/blob/main/LICENSE"
