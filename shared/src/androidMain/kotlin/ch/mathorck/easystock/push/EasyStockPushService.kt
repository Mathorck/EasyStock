package ch.mathorck.easystock.push

import ch.mathorck.easystock.sync.enqueuePushSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/** Work started by the distributor's events, which outlives the service call. */
private val pushScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Receives the UnifiedPush events from the distributor. A message only starts a sync (an expedited WorkManager job, so it ends even if the process is stopped): its content is
 * ignored (the server sends "sync"), and the engine does nothing in standalone mode.
 */
class EasyStockPushService : PushService() {
    override fun onMessage(message: PushMessage, instance: String) {
        enqueuePushSync(applicationContext)
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        val registrar = pushRegistrar(applicationContext)
        pushScope.launch { registrar.onNewEndpoint(endpoint.url) }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        val registrar = pushRegistrar(applicationContext)
        pushScope.launch { registrar.onRegistrationFailed() }
    }

    override fun onUnregistered(instance: String) {
        val registrar = pushRegistrar(applicationContext)
        pushScope.launch { registrar.onUnregistered() }
    }
}
