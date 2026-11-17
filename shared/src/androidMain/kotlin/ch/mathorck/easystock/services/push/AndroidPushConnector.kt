package ch.mathorck.easystock.services.push

import android.content.Context
import android.content.pm.PackageManager
import org.unifiedpush.android.connector.UnifiedPush

/** The UnifiedPush connector library. No VAPID key: the server sends a plain "sync" body. */
internal class AndroidPushConnector(private val context: Context) : PushConnector {
    override fun distributors(): List<PushDistributor> = UnifiedPush.getDistributors(context)
        .filter { it != context.packageName }
        .map { PushDistributor(it, labelOf(it)) }

    private fun labelOf(packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

    override fun use(distributor: String) {
        UnifiedPush.saveDistributor(context, distributor)
        UnifiedPush.register(context)
    }

    override fun register() {
        // Null when the distributor was uninstalled: the library then calls onUnregistered
        if (UnifiedPush.getSavedDistributor(context) != null) UnifiedPush.register(context)
    }

    override fun unregister() {
        UnifiedPush.unregister(context)
    }
}
