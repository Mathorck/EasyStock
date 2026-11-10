package ch.mathorck.easystock

import ch.mathorck.easystock.enums.AppLanguage

/** The language chosen in the app, System when none was chosen. */
expect fun currentAppLanguage(): AppLanguage

/** Switches the app language and persists the choice. */
expect fun applyAppLanguage(language: AppLanguage)
