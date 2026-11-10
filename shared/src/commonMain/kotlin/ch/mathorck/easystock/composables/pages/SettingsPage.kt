package ch.mathorck.easystock.composables.pages

import ch.mathorck.easystock.theme.EasyStockTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ch.mathorck.easystock.applyAppLanguage
import ch.mathorck.easystock.currentAppLanguage
import ch.mathorck.easystock.enums.AppLanguage
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.ic_language
import easystock.shared.generated.resources.settings_cancel
import easystock.shared.generated.resources.settings_language
import easystock.shared.generated.resources.settings_language_english
import easystock.shared.generated.resources.settings_language_french
import easystock.shared.generated.resources.settings_language_german
import easystock.shared.generated.resources.settings_language_system
import easystock.shared.generated.resources.settings_section_general
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsPage() {
    var language by remember { mutableStateOf(currentAppLanguage()) }
    SettingsContent(
        language = language,
        onLanguageSelected = {
            language = it
            applyAppLanguage(it)
        },
    )
}

/** Stateless settings list, previewable without platform code. */
@Composable
fun SettingsContent(language: AppLanguage, onLanguageSelected: (AppLanguage) -> Unit) {
    // Not saveable: the activity recreates on a language change, the dialog must not reopen
    var showLanguageDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHeader(stringResource(Res.string.settings_section_general))
        ListItem(
            headlineContent = { Text(stringResource(Res.string.settings_language)) },
            supportingContent = { Text(stringResource(language.label())) },
            leadingContent = { Icon(painterResource(Res.drawable.ic_language), contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            modifier = Modifier.clickable { showLanguageDialog = true },
        )
    }

    if (showLanguageDialog) {
        LanguageDialog(
            selected = language,
            onSelected = {
                showLanguageDialog = false
                if (it != language) onLanguageSelected(it)
            },
            onDismiss = { showLanguageDialog = false },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun LanguageDialog(
    selected: AppLanguage,
    onSelected: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_language)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { option ->
                    ListItem(
                        headlineContent = { Text(stringResource(option.label())) },
                        leadingContent = { RadioButton(selected = option == selected, onClick = null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier.fillMaxWidth().selectable(
                            selected = option == selected,
                            role = Role.RadioButton,
                            onClick = { onSelected(option) },
                        ),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.settings_cancel)) }
        },
    )
}

// Language names are written in their own language
private fun AppLanguage.label(): StringResource = when (this) {
    AppLanguage.System -> Res.string.settings_language_system
    AppLanguage.English -> Res.string.settings_language_english
    AppLanguage.French -> Res.string.settings_language_french
    AppLanguage.German -> Res.string.settings_language_german
}

@Preview(showBackground = true)
@Composable
private fun SettingsPagePreview() {
    EasyStockTheme {
        SettingsContent(language = AppLanguage.System, onLanguageSelected = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPageDarkPreview() {
    EasyStockTheme(darkTheme = true) {
        Column(Modifier.background(MaterialTheme.colorScheme.background)) {
            SettingsContent(language = AppLanguage.French, onLanguageSelected = {})
        }
    }
}
