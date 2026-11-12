package ch.mathorck.easystock.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** True when the platform has system colors (Android 12+): the setting is shown only then. */
expect fun isDynamicColorSupported(): Boolean

/** The system's own color scheme (Material You on Android 12+), or null when the platform has none. */
@Composable
expect fun platformColorScheme(dark: Boolean): ColorScheme?
