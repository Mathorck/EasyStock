package ch.mathorck.easystock.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

actual fun isDynamicColorSupported(): Boolean = false

@Composable
actual fun platformColorScheme(dark: Boolean): ColorScheme? = null
