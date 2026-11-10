package ch.mathorck.easystock.composables.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.mathorck.easystock.viewmodels.CatalogueError
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.attributes_back
import easystock.shared.generated.resources.attributes_cancel
import easystock.shared.generated.resources.attributes_delete
import easystock.shared.generated.resources.attributes_error_duplicate_option
import easystock.shared.generated.resources.attributes_error_label_taken
import easystock.shared.generated.resources.attributes_error_no_options
import easystock.shared.generated.resources.attributes_error_too_long
import easystock.shared.generated.resources.attributes_category_error_deleted
import easystock.shared.generated.resources.attributes_category_error_taken
import easystock.shared.generated.resources.ic_arrow_back
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Full-screen settings subpage with a back arrow. The page Scaffold already applies the system bar insets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSubpage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(Res.drawable.ic_arrow_back), contentDescription = stringResource(Res.string.attributes_back))
                }
            },
            actions = actions,
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        content()
    }
}

/** Centered hint for an empty list. */
@Composable
internal fun EmptyListHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

/** Confirmation before a soft delete. */
@Composable
internal fun ConfirmDeleteDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.attributes_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.attributes_cancel)) } },
    )
}

/** Message under a field; REQUIRED shows nothing (the "*" in the label says it). */
@Composable
internal fun CatalogueError.message(forCategory: Boolean = false): String? = when (this) {
    CatalogueError.REQUIRED -> null
    CatalogueError.TOO_LONG -> stringResource(Res.string.attributes_error_too_long)
    CatalogueError.DUPLICATE ->
        stringResource(if (forCategory) Res.string.attributes_category_error_taken else Res.string.attributes_error_label_taken)
    CatalogueError.DELETED_NAME -> stringResource(Res.string.attributes_category_error_deleted)
    CatalogueError.NO_OPTIONS -> stringResource(Res.string.attributes_error_no_options)
    CatalogueError.DUPLICATE_OPTION -> stringResource(Res.string.attributes_error_duplicate_option)
}
