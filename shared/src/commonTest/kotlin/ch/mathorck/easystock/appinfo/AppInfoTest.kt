package ch.mathorck.easystock.appinfo

import ch.mathorck.easystock.nfc.NfcAvailability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppInfoTest {
    private val info = AppInfo(
        platform = PlatformInfo(
            versionName = "0.0.0-dev",
            versionCode = 1,
            debugBuild = true,
            applicationId = "ch.mathorck.easystock",
            installedAt = 1,
            updatedAt = 2,
            os = "Android 16 (API 36)",
            device = "Google Pixel 10 Pro",
            locale = "fr-CH",
            nfc = NfcAvailability.Disabled,
            databaseBytes = 98_304,
            photoBytes = 1_572_864,
            photoCount = 3,
            freeBytes = 5L * 1024 * 1024 * 1024,
        ),
        counts = DataCounts(articles = 12, locations = 4, items = 7, stockMovements = 90, nfcTags = 5, openReviews = 1),
        dbVersion = 7,
        backupFormatVersion = 1,
        appLanguage = "fr",
        installedAt = "2026-11-01 08:00",
        updatedAt = "2026-11-10 14:57",
    )

    @Test
    fun formatsBytes() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1023 B", formatBytes(1023))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("96.0 KB", formatBytes(98_304))
        assertEquals("1.5 MB", formatBytes(1_572_864))
        assertEquals("5.0 GB", formatBytes(5L * 1024 * 1024 * 1024))
    }

    @Test
    fun roundingNeverShows1024OfAUnit() {
        assertEquals("1.0 MB", formatBytes(1024 * 1024 - 1))
    }

    @Test
    fun reportHoldsEveryValue() {
        val text = info.toReportText()
        val expected = listOf(
            "Version: 0.0.0-dev (1)",
            "Build type: debug",
            "Application id: ch.mathorck.easystock",
            "Installed: 2026-11-01 08:00",
            "Updated: 2026-11-10 14:57",
            "Database schema version: 7",
            "Backup format version: 1",
            "Articles: 12",
            "Locations: 4",
            "Items (units): 7",
            "Stock movements: 90",
            "NFC tags linked: 5",
            "Open reviews: 1",
            "Database size: 96.0 KB",
            "Photos: 3 (1.5 MB)",
            "OS: Android 16 (API 36)",
            "Device: Google Pixel 10 Pro",
            "App language: fr",
            "System locale: fr-CH",
            "NFC: disabled",
            "Free storage: 5.0 GB",
        )
        expected.forEach { assertTrue(it in text.lines(), "missing: $it") }
    }

    @Test
    fun reportSaysSoftDeletedRowsAreExcluded() {
        assertTrue("soft-deleted excluded" in info.toReportText())
    }

    @Test
    fun reportOmitsUnknownValues() {
        val text = info.copy(
            platform = info.platform.copy(freeBytes = null, nfc = NfcAvailability.Unavailable),
            installedAt = null,
            updatedAt = null,
        ).toReportText()
        assertFalse("Free storage" in text)
        assertFalse("Installed" in text)
        assertTrue("NFC: not present" in text)
        assertTrue("Build type: debug" in text)
        assertEquals(text, text.trimEnd())
    }

    @Test
    fun releaseBuildType() {
        assertTrue("Build type: release" in info.copy(platform = info.platform.copy(debugBuild = false)).toReportText())
    }
}
