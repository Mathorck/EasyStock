package ch.mathorck.easystock.alerts

import ch.mathorck.easystock.auth.Action
import ch.mathorck.easystock.auth.Permissions
import ch.mathorck.easystock.auth.Role
import ch.mathorck.easystock.data.model.ArticleStock
import ch.mathorck.easystock.data.model.LowStockAlert
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The alert rows the notifier reads and marks (data/db/AlertDao). */
interface AlertStore {
    /** `sync_state.role`, null in standalone mode. */
    suspend fun storedRole(): String?

    /** Alerts not yet notified, oldest first. */
    suspend fun pending(): List<LowStockAlert>

    /** The articles [ids] with their total stock, deleted and archived ones included. */
    suspend fun articleStock(ids: Collection<String>): List<ArticleStock>

    suspend fun markNotified(ids: Collection<String>)
}

/** The phone's notifications (Android: the "Low stock" channel). Fails silently without the permission. */
interface AlertNotifications {
    /** Creates the "Low stock" channel, so the user can turn it off before the first alert. */
    suspend fun createChannel()

    /** The low-stock notifications still shown. */
    suspend fun shown(): List<ShownAlert>

    /** Shows or replaces the notification of [notice] (key: its article id). Tapping it opens the article. */
    suspend fun show(notice: AlertNotice)

    /** Shows or replaces the grouped notification ([GROUP_KEY]). Tapping it opens the Stock page on "low stock". */
    suspend fun showGroup(notices: List<AlertNotice>)

    fun cancel(key: String)

    /** Removes every low-stock notification (the phone left the server). */
    fun cancelAll()
}

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

/** Where a tapped notification leads. */
sealed interface AlertTarget {
    data class Article(val articleId: String) : AlertTarget

    /** The Stock page filtered on "low stock" (grouped notification). */
    data object LowStock : AlertTarget
}

/** The target of the last tapped notification, until the Stock page opens it. */
object AlertNavigation {
    private val _target = MutableStateFlow<AlertTarget?>(null)
    val target: StateFlow<AlertTarget?> = _target.asStateFlow()

    fun open(target: AlertTarget) {
        _target.value = target
    }

    fun consumed() {
        _target.value = null
    }
}
