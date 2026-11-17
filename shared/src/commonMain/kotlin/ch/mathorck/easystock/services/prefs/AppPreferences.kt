package ch.mathorck.easystock.services.prefs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI preferences of this phone, kept in a [KeyValueStore]. Each one is a [StateFlow] so the screens
 * apply a change at once. Not data: "reset all data" keeps them (like the app language).
 */
class AppPreferences(private val store: KeyValueStore) {
    private val _dynamicColor = MutableStateFlow(store.getBoolean(KEY_DYNAMIC_COLOR, true))

    /** Use the phone's own colors (Material You) when the platform has them. On by default. */
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    fun setDynamicColor(enabled: Boolean) {
        store.putBoolean(KEY_DYNAMIC_COLOR, enabled)
        _dynamicColor.value = enabled
    }

    /** True while the phone is connected as a Guest (see `alerts/AlertPermissionRule.kt`). */
    var wasGuest: Boolean
        get() = store.getBoolean(KEY_WAS_GUEST, false)
        set(value) = store.putBoolean(KEY_WAS_GUEST, value)

    private companion object {
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_WAS_GUEST = "was_guest"
    }
}

/** The preferences of the app, provided by `App()`. The default is in memory (previews). */
val LocalAppPreferences = compositionLocalOf { AppPreferences(MemoryKeyValueStore()) }

/** The preferences stored on this phone. */
@Composable
expect fun rememberAppPreferences(): AppPreferences
