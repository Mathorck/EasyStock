package ch.mathorck.easystock.viewmodels.backup

import ch.mathorck.easystock.enums.backup.BackupError
import ch.mathorck.easystock.services.server.JoinCode

data class BackupUiState(
    val step: BackupStep = BackupStep.Home,
    /** Shown in a dialog, the step stays. */
    val error: BackupError? = null,
    /** A server join code was scanned: the screen hands it to Settings › Server, then [BackupViewModel.joinCodeHandled]. */
    val joinCode: JoinCode? = null,
)
