package ch.mathorck.easystock

import ch.mathorck.easystock.enums.AppLanguage
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults

// Unverified (no Mac). iOS reads AppleLanguages at launch, so the change applies on restart.

private const val APPLE_LANGUAGES = "AppleLanguages"

actual fun currentAppLanguage(): AppLanguage {
    val defaults = NSUserDefaults.standardUserDefaults
    // Only the app domain counts, the global domain holds the system languages
    val appDomain = defaults.persistentDomainForName(NSBundle.mainBundle.bundleIdentifier ?: "")
    val languages = appDomain?.get(APPLE_LANGUAGES) as? List<*>
    return AppLanguage.fromTag(languages?.firstOrNull() as? String)
}

actual fun applyAppLanguage(language: AppLanguage) {
    val defaults = NSUserDefaults.standardUserDefaults
    val tag = language.tag
    if (tag == null) defaults.removeObjectForKey(APPLE_LANGUAGES) else defaults.setObject(listOf(tag), APPLE_LANGUAGES)
}
