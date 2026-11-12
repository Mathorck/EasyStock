package ch.mathorck.easystock.prefs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppPreferencesTest {
    @Test
    fun dynamicColorIsOnByDefault() {
        assertTrue(AppPreferences(MemoryKeyValueStore()).dynamicColor.value)
    }

    @Test
    fun settingUpdatesTheFlowAndIsStored() {
        val store = MemoryKeyValueStore()
        val prefs = AppPreferences(store)
        prefs.setDynamicColor(false)
        assertFalse(prefs.dynamicColor.value)
        // A new instance (app restart) reads the stored value
        assertFalse(AppPreferences(store).dynamicColor.value)
        prefs.setDynamicColor(true)
        assertEquals(true, AppPreferences(store).dynamicColor.value)
    }
}
