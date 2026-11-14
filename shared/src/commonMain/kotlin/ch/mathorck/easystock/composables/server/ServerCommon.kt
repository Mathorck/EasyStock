package ch.mathorck.easystock.composables.server

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.mathorck.easystock.server.ConnectError
import ch.mathorck.easystock.server.ServerConnection
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.ic_lock
import easystock.shared.generated.resources.ic_warning
import easystock.shared.generated.resources.server_error_account_disabled
import easystock.shared.generated.resources.server_error_account_locked
import easystock.shared.generated.resources.server_error_api_unsupported
import easystock.shared.generated.resources.server_error_app_too_old
import easystock.shared.generated.resources.server_error_code_expired
import easystock.shared.generated.resources.server_error_code_invalid
import easystock.shared.generated.resources.server_error_code_used
import easystock.shared.generated.resources.server_error_guest_limit
import easystock.shared.generated.resources.server_error_invalid
import easystock.shared.generated.resources.server_error_local
import easystock.shared.generated.resources.server_error_network
import easystock.shared.generated.resources.server_error_no_promotion
import easystock.shared.generated.resources.server_error_not_a_server
import easystock.shared.generated.resources.server_error_not_set_up
import easystock.shared.generated.resources.server_error_rate_limited
import easystock.shared.generated.resources.server_error_server
import easystock.shared.generated.resources.server_error_session_revoked
import easystock.shared.generated.resources.server_error_setup_done
import easystock.shared.generated.resources.server_error_username_taken
import easystock.shared.generated.resources.server_error_wrong_credentials
import easystock.shared.generated.resources.server_guest_off
import easystock.shared.generated.resources.server_pinned
import easystock.shared.generated.resources.server_error_local_network
import easystock.shared.generated.resources.server_secured
import easystock.shared.generated.resources.server_unsecured
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** The address without scheme, as typed: `stock.example.org`, `192.168.1.20:8080`. */
internal fun ServerConnection.host(): String = serverUrl.removePrefix("https://").removePrefix("http://")

internal fun ConnectError.message(): StringResource = when (this) {
    ConnectError.NETWORK -> Res.string.server_error_network
    ConnectError.LOCAL_NETWORK_DENIED -> Res.string.server_error_local_network
    ConnectError.NOT_A_SERVER -> Res.string.server_error_not_a_server
    ConnectError.API_UNSUPPORTED -> Res.string.server_error_api_unsupported
    ConnectError.APP_TOO_OLD -> Res.string.server_error_app_too_old
    ConnectError.SETUP_DONE -> Res.string.server_error_setup_done
    ConnectError.NOT_SET_UP -> Res.string.server_error_not_set_up
    ConnectError.WRONG_CREDENTIALS -> Res.string.server_error_wrong_credentials
    ConnectError.ACCOUNT_LOCKED -> Res.string.server_error_account_locked
    ConnectError.ACCOUNT_DISABLED -> Res.string.server_error_account_disabled
    ConnectError.CODE_INVALID -> Res.string.server_error_code_invalid
    ConnectError.CODE_EXPIRED -> Res.string.server_error_code_expired
    ConnectError.CODE_USED -> Res.string.server_error_code_used
    ConnectError.GUEST_SIGNUP_OFF -> Res.string.server_guest_off
    ConnectError.GUEST_LIMIT_REACHED -> Res.string.server_error_guest_limit
    ConnectError.USERNAME_TAKEN -> Res.string.server_error_username_taken
    ConnectError.INVALID -> Res.string.server_error_invalid
    ConnectError.RATE_LIMITED -> Res.string.server_error_rate_limited
    ConnectError.SESSION_REVOKED -> Res.string.server_error_session_revoked
    ConnectError.NO_PROMOTION -> Res.string.server_error_no_promotion
    ConnectError.SERVER -> Res.string.server_error_server
    ConnectError.LOCAL -> Res.string.server_error_local
}

/** HTTPS (pinned or not) or plain HTTP, with an icon: HTTP is always shown as unsecured. */
@Composable
internal fun ConnectionSecurity(connection: ServerConnection, modifier: Modifier = Modifier) {
    val (icon, text) = when {
        !connection.isHttps -> Res.drawable.ic_warning to Res.string.server_unsecured
        connection.fingerprint != null -> Res.drawable.ic_lock to Res.string.server_pinned
        else -> Res.drawable.ic_lock to Res.string.server_secured
    }
    val color = if (connection.isHttps) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
