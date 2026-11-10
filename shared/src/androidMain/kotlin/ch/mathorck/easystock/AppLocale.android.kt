package ch.mathorck.easystock

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import ch.mathorck.easystock.enums.AppLanguage

// Android per-app language: AppCompat persists the choice and recreates the activity.

actual fun currentAppLanguage(): AppLanguage =
    AppLanguage.fromTag(AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag())

actual fun applyAppLanguage(language: AppLanguage) {
    val locales = language.tag?.let { LocaleListCompat.forLanguageTags(it) }
        ?: LocaleListCompat.getEmptyLocaleList()
    AppCompatDelegate.setApplicationLocales(locales)
}
