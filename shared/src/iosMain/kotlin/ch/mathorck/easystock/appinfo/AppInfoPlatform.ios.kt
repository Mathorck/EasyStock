package ch.mathorck.easystock.appinfo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ch.mathorck.easystock.nfc.NfcAvailability
import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier
import platform.UIKit.UIDevice
import platform.UIKit.UIPasteboard

// iOS is not supported: version and device only, sizes are left empty.
private class IosAppInfoPlatform : AppInfoPlatform {
    override suspend fun read(): PlatformInfo {
        val info = NSBundle.mainBundle.infoDictionary
        return PlatformInfo(
            versionName = info?.get("CFBundleShortVersionString") as? String ?: "?",
            versionCode = (info?.get("CFBundleVersion") as? String)?.toLongOrNull() ?: 0L,
            debugBuild = false,
            applicationId = NSBundle.mainBundle.bundleIdentifier ?: "?",
            installedAt = null,
            updatedAt = null,
            os = "${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}",
            device = UIDevice.currentDevice.model,
            locale = NSLocale.currentLocale.localeIdentifier,
            nfc = NfcAvailability.Unavailable,
            databaseBytes = 0,
            photoBytes = 0,
            photoCount = 0,
            freeBytes = null,
        )
    }

    override fun copy(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }
}

@Composable
actual fun rememberAppInfoPlatform(): AppInfoPlatform = remember { IosAppInfoPlatform() }
