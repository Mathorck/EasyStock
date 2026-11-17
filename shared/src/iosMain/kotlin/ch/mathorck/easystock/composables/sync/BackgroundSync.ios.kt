package ch.mathorck.easystock.composables.sync

import androidx.compose.runtime.Composable
import ch.mathorck.easystock.services.sync.BackgroundSyncScheduler

@Composable
actual fun rememberBackgroundSyncScheduler(): BackgroundSyncScheduler? = null
