package ch.mathorck.easystock.alerts

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.mathorck.easystock.auth.Action
import ch.mathorck.easystock.auth.LocalPermissions
import ch.mathorck.easystock.data.db.rememberAppDatabase
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.alerts_permission_allow
import easystock.shared.generated.resources.alerts_permission_later
import easystock.shared.generated.resources.alerts_permission_message
import easystock.shared.generated.resources.alerts_permission_title
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource

/**
 * The low-stock notifications outside the sync: removes them when the phone leaves the server, sends a
 * tapped one to the Stock page ([onOpen]), and asks the notification permission once after joining
 * ([askPermission], cleared by [onAsked]).
 */
@Composable
fun AlertHost(askPermission: Boolean, onAsked: () -> Unit, onOpen: () -> Unit) {
    val notifications = rememberAlertNotifications()
    val db = rememberAppDatabase()
    // The database, not LocalPermissions: that one says standalone until the database answers
    LaunchedEffect(db, notifications) {
        if (notifications == null) return@LaunchedEffect
        db.sessionDao().observeRole().map { it != null }.distinctUntilChanged().collect { connected ->
            if (!connected) notifications.cancelAll()
        }
    }
    val target by AlertNavigation.target.collectAsStateWithLifecycle()
    LaunchedEffect(target) {
        // Consumed by the Stock page
        if (target != null) onOpen()
    }
    // Outside the prompt: the launcher must outlive the explanation dialog
    val permission = rememberNotificationPermission()
    if (askPermission) NotificationPermissionPrompt(permission, onAsked)
}

/** A short explanation, then the system dialog (Android 13+). Skipped when nothing must be asked. */
@Composable
private fun NotificationPermissionPrompt(permission: NotificationPermission?, onDone: () -> Unit) {
    val permissions = LocalPermissions.current
    // Waits for the joined session: LocalPermissions may still be the standalone one for a moment
    if (!permissions.connected) return
    val needed = remember(permission) { permission?.canRequest == true }
    // Guests never get alerts
    val wanted = permissions.can(Action.RECEIVE_ALERTS)
    LaunchedEffect(needed, wanted) {
        if (!needed || !wanted) onDone()
    }
    if (!needed || !wanted || permission == null) return
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(Res.string.alerts_permission_title)) },
        text = { Text(stringResource(Res.string.alerts_permission_message)) },
        confirmButton = {
            TextButton(onClick = {
                permission.request()
                onDone()
            }) { Text(stringResource(Res.string.alerts_permission_allow)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(Res.string.alerts_permission_later)) } },
    )
}
