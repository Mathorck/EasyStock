package ch.mathorck.easystock.composables.server.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ch.mathorck.easystock.auth.Role
import ch.mathorck.easystock.backup.QrCodeImage
import ch.mathorck.easystock.composables.settings.EmptyListHint
import ch.mathorck.easystock.server.ApiError
import ch.mathorck.easystock.composables.server.problemMessage
import ch.mathorck.easystock.viewmodels.admin.ActionFailure
import ch.mathorck.easystock.viewmodels.admin.Loaded
import ch.mathorck.easystock.viewmodels.formatDateTime
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.admin_error_conflict
import easystock.shared.generated.resources.admin_error_forbidden
import easystock.shared.generated.resources.admin_error_invalid
import easystock.shared.generated.resources.admin_error_network
import easystock.shared.generated.resources.admin_error_not_found
import easystock.shared.generated.resources.admin_error_rate_limited
import easystock.shared.generated.resources.admin_error_server
import easystock.shared.generated.resources.admin_error_session_revoked
import easystock.shared.generated.resources.admin_error_too_old
import easystock.shared.generated.resources.admin_never
import easystock.shared.generated.resources.admin_qr_close
import easystock.shared.generated.resources.admin_retry
import easystock.shared.generated.resources.admin_role_admin
import easystock.shared.generated.resources.admin_role_editor
import easystock.shared.generated.resources.admin_role_guest
import easystock.shared.generated.resources.admin_role_viewer
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

internal fun Role.label(): StringResource = when (this) {
    Role.ADMIN -> Res.string.admin_role_admin
    Role.EDITOR -> Res.string.admin_role_editor
    Role.VIEWER -> Res.string.admin_role_viewer
    Role.GUEST -> Res.string.admin_role_guest
}

/** The message of a failed call; the Problem Details type is more precise than the status. */
internal fun ActionFailure.message(): StringResource = problemMessage(problemType) ?: error.message()

internal fun ApiError.message(): StringResource = when (this) {
    ApiError.NETWORK -> Res.string.admin_error_network
    ApiError.SESSION_REVOKED -> Res.string.admin_error_session_revoked
    ApiError.APP_TOO_OLD -> Res.string.admin_error_too_old
    ApiError.FORBIDDEN -> Res.string.admin_error_forbidden
    ApiError.NOT_FOUND -> Res.string.admin_error_not_found
    ApiError.CONFLICT -> Res.string.admin_error_conflict
    ApiError.INVALID -> Res.string.admin_error_invalid
    ApiError.RATE_LIMITED -> Res.string.admin_error_rate_limited
    ApiError.SERVER -> Res.string.admin_error_server
}

/** An ISO 8601 time of the server, in the phone's time zone; "never" when there is none. */
@Composable
internal fun serverTime(iso: String?): String {
    if (iso.isNullOrBlank()) return stringResource(Res.string.admin_never)
    return formatServerTime(iso) ?: iso
}

internal fun formatServerTime(iso: String, timeZone: TimeZone = TimeZone.currentSystemDefault()): String? =
    runCatching { formatDateTime(Instant.parse(iso).toEpochMilliseconds(), timeZone) }.getOrNull()

/**
 * Loading, error (with retry), empty or the content of [loaded], with pull-to-refresh. The content
 * stays shown when a refresh fails, with the error above it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> LoadedContent(
    loaded: Loaded<T>,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    isEmpty: (T) -> Boolean,
    emptyText: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    PullToRefreshBox(isRefreshing = loaded.refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        val data = loaded.data
        when {
            data == null && loaded.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            data == null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ErrorBlock(loaded.error ?: ApiError.SERVER, onRetry)
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                loaded.error?.let { ErrorBlock(it, onRetry) }
                if (isEmpty(data)) EmptyListHint(emptyText) else content(data)
            }
        }
    }
}

@Composable
internal fun ErrorBlock(error: ApiError, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(error.message()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onRetry) { Text(stringResource(Res.string.admin_retry)) }
    }
}

/**
 * A QR code full screen, on white with high contrast for the other phone's camera. [title] and
 * [subtitle] say what it is for; never the token.
 */
@Composable
internal fun FullScreenQr(text: String, title: String, subtitle: String?, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FullScreenQrContent(text, title, subtitle, onClose)
    }
}

@Composable
internal fun FullScreenQrContent(text: String, title: String, subtitle: String?, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color.White).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.Black, textAlign = TextAlign.Center)
        QrCodeImage(text, description = title, modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp))
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray, textAlign = TextAlign.Center)
        }
        Button(onClick = onClose) { Text(stringResource(Res.string.admin_qr_close)) }
    }
}
