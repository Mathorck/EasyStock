package ch.mathorck.easystock.appinfo

import androidx.compose.runtime.Composable

/** Platform side of the app info screen. Local only, nothing is sent anywhere. */
interface AppInfoPlatform {
    /** Reads package, device and storage facts. Call off the main thread. */
    suspend fun read(): PlatformInfo

    /** Puts [text] on the clipboard. */
    fun copy(text: String)
}

@Composable
expect fun rememberAppInfoPlatform(): AppInfoPlatform
