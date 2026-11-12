package ch.mathorck.easystock.push

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch

/**
 * While connected, each time the app comes to the front: registers again with the distributor the user
 * chose and sends the address the server doesn't have yet. Composes nothing in standalone mode.
 */
@Composable
fun PushHost(connected: Boolean) {
    if (!connected) return
    val registrar = rememberPushRegistrar() ?: return
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(registrar) {
        scope.launch { registrar.refresh() }
        onPauseOrDispose {}
    }
}
