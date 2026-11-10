package ch.mathorck.easystock.composables.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.mathorck.easystock.data.db.rememberAppDatabase
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.theme.EasyStockTheme
import ch.mathorck.easystock.viewmodels.CategoryDialogState
import ch.mathorck.easystock.viewmodels.CategoryListUiState
import ch.mathorck.easystock.viewmodels.CategoryListViewModel
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.attributes_cancel
import easystock.shared.generated.resources.attributes_categories_empty
import easystock.shared.generated.resources.attributes_categories_title
import easystock.shared.generated.resources.attributes_category_add
import easystock.shared.generated.resources.attributes_category_delete_message
import easystock.shared.generated.resources.attributes_category_delete_title
import easystock.shared.generated.resources.attributes_category_edit_attributes
import easystock.shared.generated.resources.attributes_category_name
import easystock.shared.generated.resources.attributes_category_new_title
import easystock.shared.generated.resources.attributes_category_rename
import easystock.shared.generated.resources.attributes_category_rename_title
import easystock.shared.generated.resources.attributes_delete
import easystock.shared.generated.resources.attributes_error_save
import easystock.shared.generated.resources.attributes_more
import easystock.shared.generated.resources.attributes_save
import easystock.shared.generated.resources.ic_add
import easystock.shared.generated.resources.ic_more_vert
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › Categories. A tap on a category opens its attributes ([CategoryAttributesScreen]).
 * [onBack] runs on the arrow and the system back.
 */
@Composable
fun CategoriesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var openCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    val categoryId = openCategoryId
    if (categoryId != null) {
        // Only one back handler is composed at a time
        CategoryAttributesScreen(categoryId = categoryId, onBack = { openCategoryId = null }, modifier = modifier)
        return
    }

    val db = rememberAppDatabase()
    val viewModel = viewModel(key = "settings-categories") { CategoryListViewModel(db.categoryDao()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val back = {
        viewModel.cancel()
        onBack()
    }
    NavigationBackHandler(state = rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = back)

    CategoriesContent(
        state = state,
        onBack = back,
        onAdd = viewModel::startCreate,
        onOpen = { openCategoryId = it.id },
        onRename = viewModel::startRename,
        onDelete = viewModel::delete,
        modifier = modifier,
    )
    state.dialog?.let { dialog ->
        CategoryDialog(state = dialog, onNameChange = viewModel::updateName, onDismiss = viewModel::cancel, onSave = viewModel::save)
    }
}

@Composable
fun CategoriesContent(
    state: CategoryListUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Category) -> Unit,
    onRename: (Category) -> Unit,
    onDelete: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    var toDelete by remember { mutableStateOf<Category?>(null) }
    SettingsSubpage(
        title = stringResource(Res.string.attributes_categories_title),
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(onClick = onAdd) {
                Icon(painterResource(Res.drawable.ic_add), contentDescription = stringResource(Res.string.attributes_category_add))
            }
        },
    ) {
        if (!state.loading && state.categories.isEmpty()) {
            EmptyListHint(stringResource(Res.string.attributes_categories_empty))
        }
        LazyColumn {
            items(state.categories, key = { it.id }) { category ->
                ListItem(
                    headlineContent = { Text(category.name) },
                    supportingContent = { Text(stringResource(Res.string.attributes_category_edit_attributes)) },
                    trailingContent = {
                        CategoryMenu(onRename = { onRename(category) }, onDelete = { toDelete = category })
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.clickable { onOpen(category) },
                )
            }
        }
    }
    toDelete?.let { category ->
        ConfirmDeleteDialog(
            title = stringResource(Res.string.attributes_category_delete_title),
            message = stringResource(Res.string.attributes_category_delete_message, category.name),
            onConfirm = {
                toDelete = null
                onDelete(category)
            },
            onDismiss = { toDelete = null },
        )
    }
}

@Composable
private fun CategoryMenu(onRename: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(Res.drawable.ic_more_vert), contentDescription = stringResource(Res.string.attributes_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.attributes_category_rename)) },
                onClick = {
                    expanded = false
                    onRename()
                },
            )
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

@Composable
private fun CategoryDialog(state: CategoryDialogState, onNameChange: (String) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (state.editing == null) Res.string.attributes_category_new_title else Res.string.attributes_category_rename_title))
        },
        text = {
            Column {
                val error = state.error?.message(forCategory = true)
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(Res.string.attributes_category_name)) },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.saveFailed) {
                    Text(stringResource(Res.string.attributes_error_save), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = state.canSave) { Text(stringResource(Res.string.attributes_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.attributes_cancel)) } },
    )
}

@Preview(showBackground = true)
@Composable
private fun CategoriesContentPreview() {
    EasyStockTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CategoriesContent(
                state = CategoryListUiState(
                    loading = false,
                    categories = listOf(
                        Category(id = "ropes", name = "Ropes", createdAt = 0, updatedAt = 0),
                        Category(id = "tools", name = "Tools", createdAt = 0, updatedAt = 0),
                    ),
                ),
                onBack = {}, onAdd = {}, onOpen = {}, onRename = {}, onDelete = {},
            )
        }
    }
}
