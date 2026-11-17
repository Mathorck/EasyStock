package ch.mathorck.easystock.services.alerts

import ch.mathorck.easystock.enums.auth.Action
import ch.mathorck.easystock.enums.auth.Role
import ch.mathorck.easystock.services.auth.Permissions
import kotlin.time.Clock

/**
 * Shows the low-stock alerts pulled from the server (02-app.md, "Low-stock alert notifications"),
 * called by the sync engine after each pull. Never shows anything in standalone mode: no
 * `sync_state`, nothing read or written. Each alert is shown at most once ([planAlerts]).
 */
class AlertNotifier(
    private val store: AlertStore,
    private val notifications: AlertNotifications,
    private val clock: Clock = Clock.System,
) {
    suspend fun check() {
        val stored = store.storedRole() ?: return
        val canReceive = Permissions.can(Action.RECEIVE_ALERTS, connected = true, role = Role.fromDb(stored))
        val pending = store.pending()
        val shown = notifications.shown()
        val ids = pending.mapTo(LinkedHashSet()) { it.articleId }.apply { shown.forEach { addAll(it.articleIds) } }
        val articles = if (ids.isEmpty()) emptyMap() else store.articleStock(ids).associateBy { it.article.id }
        val plan = planAlerts(pending, shown, articles, canReceive, clock.now().toEpochMilliseconds())
        plan.cancel.forEach(notifications::cancel)
        plan.single.forEach { notifications.show(it) }
        plan.group?.let { notifications.showGroup(it) }
        if (plan.markNotified.isNotEmpty()) store.markNotified(plan.markNotified)
    }
}
