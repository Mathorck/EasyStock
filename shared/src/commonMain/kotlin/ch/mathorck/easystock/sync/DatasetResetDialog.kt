package ch.mathorck.easystock.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.dataset_reset_lost
import easystock.shared.generated.resources.dataset_reset_message
import easystock.shared.generated.resources.dataset_reset_ok
import easystock.shared.generated.resources.dataset_reset_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Tells the user the server replaced its data set and the phone started over ([SyncEngine.resetNotice]). */
@Composable
fun DatasetResetDialog(engine: SyncEngine) {
    val notice by engine.resetNotice.collectAsStateWithLifecycle()
    val shown = notice ?: return
    AlertDialog(
        onDismissRequest = engine::resetNoticeShown,
        title = { Text(stringResource(Res.string.dataset_reset_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.dataset_reset_message))
                if (shown.lostChanges > 0) {
                    Text(pluralStringResource(Res.plurals.dataset_reset_lost, shown.lostChanges, shown.lostChanges))
                }
            }
        },
        confirmButton = { TextButton(onClick = engine::resetNoticeShown) { Text(stringResource(Res.string.dataset_reset_ok)) } },
    )
}
