package ch.mathorck.easystock

import ch.mathorck.easystock.enums.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class AppLanguageTest {
    @Test
    fun emptyTagIsSystem() {
        assertEquals(AppLanguage.System, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.System, AppLanguage.fromTag(""))
    }

    @Test
    fun regionTagMapsToLanguage() {
        assertEquals(AppLanguage.French, AppLanguage.fromTag("fr-CH"))
        assertEquals(AppLanguage.German, AppLanguage.fromTag("de_CH"))
        assertEquals(AppLanguage.English, AppLanguage.fromTag("EN-us"))
    }

    @Test
    fun unsupportedTagIsSystem() {
        assertEquals(AppLanguage.System, AppLanguage.fromTag("it"))
    }

    @Test
    fun tagsRoundTrip() {
        AppLanguage.entries.forEach { assertEquals(it, AppLanguage.fromTag(it.tag)) }
    }
}
