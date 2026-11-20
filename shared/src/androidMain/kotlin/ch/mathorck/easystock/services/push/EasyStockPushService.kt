package ch.mathorck.easystock.services.push

import ch.mathorck.easystock.composables.sync.enqueuePushSync
import ch.mathorck.easystock.services.profiles.activeProfilePaths
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
 * Only the events of the active profile's instance count: the others are synced when their profile is opened.
 */
class EasyStockPushService : PushService() {
    private fun isActive(instance: String) = instance == activeProfilePaths.pushInstance

    override fun onMessage(message: PushMessage, instance: String) {
        if (!isActive(instance)) return
        enqueuePushSync(applicationContext)
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        if (!isActive(instance)) return
        val registrar = pushRegistrar(applicationContext)
        pushScope.launch { registrar.onNewEndpoint(endpoint.url) }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        if (!isActive(instance)) return
        val registrar = pushRegistrar(applicationContext)
        pushScope.launch { registrar.onRegistrationFailed() }
    }

    override fun onUnregistered(instance: String) {
        if (!isActive(instance)) return
        val registrar = pushRegistrar(applicationContext)
        pushScope.launch { registrar.onUnregistered() }
    }
}
