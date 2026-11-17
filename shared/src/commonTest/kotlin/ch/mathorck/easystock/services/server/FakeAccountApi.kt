package ch.mathorck.easystock.services.server

import ch.mathorck.easystock.data.dto.server.CompletePromotionRequest
import ch.mathorck.easystock.data.dto.server.MeAnswer
import ch.mathorck.easystock.data.dto.server.PendingPromotion
import ch.mathorck.easystock.data.dto.server.SessionUser
import ch.mathorck.easystock.data.dto.server.TokenAnswer

internal class FakeAccountApi : AccountApi {
    var me = MeAnswer("u1", "Visitor", "guest", "guest", shortCode = "K7Q2", pendingPromotion = PendingPromotion("member"))
    var meError: Exception? = null
    var logoutError: Exception? = null
    var promotionError: Exception? = null
    var logouts = 0
    var alertsError: Exception? = null
    val alertCalls = mutableListOf<Boolean>()
    val promotions = mutableListOf<CompletePromotionRequest>()
    val promoted = TokenAnswer("a2", "r2", user = SessionUser("u1", "Ann", "member", "human"))

    override suspend fun me(): MeAnswer {
        meError?.let { throw it }
        return me
    }

    override suspend fun setAlertsEnabled(enabled: Boolean) {
        alertCalls += enabled
        alertsError?.let { throw it }
    }

    var nameError: Exception? = null
    var passwordError: Exception? = null
    val names = mutableListOf<String>()
    val passwords = mutableListOf<Pair<String, String>>()

    override suspend fun setDisplayName(name: String): MeAnswer {
        names += name
        nameError?.let { throw it }
        return me.copy(displayName = name)
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        passwords += currentPassword to newPassword
        passwordError?.let { throw it }
    }

    /** When set, the logout call waits for it. */
    var logoutGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun logout() {
        logoutGate?.await()
        logouts++
        logoutError?.let { throw it }
    }

    override suspend fun completePromotion(request: CompletePromotionRequest): TokenAnswer {
        promotions += request
        promotionError?.let { throw it }
        return promoted
    }

    /** The passwords sent with `DELETE /me` (null: a Guest). */
    val deletes = mutableListOf<String?>()
    var deleteError: Exception? = null

    /** Runs after a successful delete, as another call failing with 401 at that moment would. */
    var afterDelete: () -> Unit = {}

    override suspend fun deleteAccount(password: String?) {
        deletes += password
        deleteError?.let { throw it }
        afterDelete()
    }
}
