package ch.mathorck.easystock.composables.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.mathorck.easystock.composables.theme.EasyStockTheme
import ch.mathorck.easystock.data.model.profiles.Profile
import ch.mathorck.easystock.services.profiles.ProfileRegistry
import ch.mathorck.easystock.services.profiles.rememberProfileService
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.attributes_cancel
import easystock.shared.generated.resources.attributes_delete
import easystock.shared.generated.resources.attributes_more
import easystock.shared.generated.resources.attributes_save
import easystock.shared.generated.resources.ic_add
import easystock.shared.generated.resources.ic_check
import easystock.shared.generated.resources.ic_more_vert
import easystock.shared.generated.resources.profiles_active
import easystock.shared.generated.resources.profiles_add
import easystock.shared.generated.resources.profiles_default_name
import easystock.shared.generated.resources.profiles_delete_failed
import easystock.shared.generated.resources.profiles_delete_message
import easystock.shared.generated.resources.profiles_delete_title
import easystock.shared.generated.resources.profiles_name
import easystock.shared.generated.resources.profiles_name_invalid
import easystock.shared.generated.resources.profiles_new_hint
import easystock.shared.generated.resources.profiles_new_title
import easystock.shared.generated.resources.profiles_rename
import easystock.shared.generated.resources.profiles_rename_title
import easystock.shared.generated.resources.profiles_switch_confirm
import easystock.shared.generated.resources.profiles_switch_message
import easystock.shared.generated.resources.profiles_switch_title
import easystock.shared.generated.resources.profiles_title
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › Profiles: separate stocks on the phone, each standalone or joined to its own server.
 * Opening another profile restarts the app. [onBack] runs on the arrow and the system back.
 */
@Composable
fun ProfilesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val service = rememberProfileService()
    val registry by service.registry.collectAsState()
    val scope = rememberCoroutineScope()
    var deleteFailed by remember { mutableStateOf(false) }
    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = onBack)

    ProfilesContent(
        registry = registry,
        deleteFailed = deleteFailed,
        onBack = onBack,
        onCreate = { service.create(it) != null },
        onRename = service::rename,
        onSwitch = service::switchTo,
        onDelete = { profile ->
            deleteFailed = false
            scope.launch {
                // The profile already left the registry: finish erasing its files even if the screen closes
                val result = withContext(NonCancellable) { runCatching { service.delete(profile.id) } }
                deleteFailed = result.isFailure
            }
        },
        modifier = modifier,
    )
}

@Composable
fun ProfilesContent(
    registry: ProfileRegistry,
    onBack: () -> Unit,
    /** False when the name is not valid. */
    onCreate: (String) -> Boolean,
    onRename: (id: String, name: String) -> Boolean,
    onSwitch: (String) -> Unit,
    onDelete: (Profile) -> Unit,
    modifier: Modifier = Modifier,
    deleteFailed: Boolean = false,
) {
    var creating by remember { mutableStateOf(false) }
    var toRename by remember { mutableStateOf<Profile?>(null) }
    var toOpen by remember { mutableStateOf<Profile?>(null) }
    var toDelete by remember { mutableStateOf<Profile?>(null) }

    SettingsSubpage(
        title = stringResource(Res.string.profiles_title),
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(onClick = { creating = true }) {
                Icon(painterResource(Res.drawable.ic_add), contentDescription = stringResource(Res.string.profiles_add))
            }
        },
    ) {
        if (deleteFailed) {
            Text(
                stringResource(Res.string.profiles_delete_failed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        LazyColumn {
            items(registry.profiles, key = { it.id }) { profile ->
                val active = profile.id == registry.activeId
                ListItem(
                    headlineContent = { Text(profileName(profile)) },
                    supportingContent = if (active) {
                        { Text(stringResource(Res.string.profiles_active)) }
                    } else {
                        null
                    },
                    leadingContent = {
                        // Same width on every row, so the names line up
                        Box(Modifier.size(24.dp)) {
                            if (active) {
                                Icon(
                                    painterResource(Res.drawable.ic_check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    trailingContent = {
                        ProfileMenu(
                            onRename = { toRename = profile },
                            // The profile in use can't be deleted: open another one first
                            onDelete = if (active) null else ({ toDelete = profile }),
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.clickable(enabled = !active) { toOpen = profile },
                )
            }
        }
    }

    if (creating) {
        ProfileNameDialog(
            title = stringResource(Res.string.profiles_new_title),
            initial = "",
            hint = stringResource(Res.string.profiles_new_hint),
            onDismiss = { creating = false },
            onSave = { name -> onCreate(name).also { if (it) creating = false } },
        )
    }
    toRename?.let { profile ->
        ProfileNameDialog(
            title = stringResource(Res.string.profiles_rename_title),
            initial = profile.name.orEmpty(),
            onDismiss = { toRename = null },
            onSave = { name -> onRename(profile.id, name).also { if (it) toRename = null } },
        )
    }
    toOpen?.let { profile ->
        AlertDialog(
            onDismissRequest = { toOpen = null },
            title = { Text(stringResource(Res.string.profiles_switch_title, profileName(profile))) },
            text = { Text(stringResource(Res.string.profiles_switch_message)) },
            confirmButton = {
                TextButton(onClick = {
                    toOpen = null
                    onSwitch(profile.id)
                }) { Text(stringResource(Res.string.profiles_switch_confirm)) }
            },
            dismissButton = { TextButton(onClick = { toOpen = null }) { Text(stringResource(Res.string.attributes_cancel)) } },
        )
    }
    toDelete?.let { profile ->
        ConfirmDeleteDialog(
            title = stringResource(Res.string.profiles_delete_title, profileName(profile)),
            message = stringResource(Res.string.profiles_delete_message),
            onConfirm = {
                toDelete = null
                onDelete(profile)
            },
            onDismiss = { toDelete = null },
        )
    }
}

/** The given name, or "Default" for the unnamed profile every install already has. */
@Composable
fun profileName(profile: Profile): String = profile.name ?: stringResource(Res.string.profiles_default_name)

@Composable
private fun ProfileMenu(onRename: () -> Unit, onDelete: (() -> Unit)?) {
    var expanded by remember { mutableStateOf(false) }
    Row {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(Res.drawable.ic_more_vert), contentDescription = stringResource(Res.string.attributes_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.profiles_rename)) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.attributes_delete)) },
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/** [onSave] returns false when the name is not valid: the dialog stays open with an error. */
@Composable
private fun ProfileNameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Boolean, hint: String? = null) {
    var name by remember { mutableStateOf(initial) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        invalid = false
                    },
                    label = { Text(stringResource(Res.string.profiles_name)) },
                    isError = invalid,
                    supportingText = if (invalid) {
                        { Text(stringResource(Res.string.profiles_name_invalid)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { invalid = !onSave(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(Res.string.attributes_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.attributes_cancel)) } },
    )
}

@Preview(showBackground = true)
@Composable
private fun ProfilesContentPreview() {
    EasyStockTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ProfilesContent(
                registry = ProfileRegistry.legacyOnly().adding("8c3f6c0e-5b0e-4c4f-9a4e-2f1d3b7a9c10", "Workshop"),
                onBack = {}, onCreate = { true }, onRename = { _, _ -> true }, onSwitch = {}, onDelete = {},
            )
        }
    }
}
