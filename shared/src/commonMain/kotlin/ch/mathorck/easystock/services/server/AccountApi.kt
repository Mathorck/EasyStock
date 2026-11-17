package ch.mathorck.easystock.services.server

import ch.mathorck.easystock.data.dto.server.CompletePromotionRequest
import ch.mathorck.easystock.data.dto.server.MeAnswer
import ch.mathorck.easystock.data.dto.server.TokenAnswer

/** The calls of the Account screen, with the session of the connected server. */
interface AccountApi {
    suspend fun me(): MeAnswer

    /** `PATCH /me` `{"alertsEnabled": enabled}`: the alert preference (Guests cannot). */
    suspend fun setAlertsEnabled(enabled: Boolean)

    /** `PATCH /me` `{"displayName": name}`: answers the updated account. */
    suspend fun setDisplayName(name: String): MeAnswer

    /**
     * `POST /me/password` (full accounts only). The server ends every other session of the user and keeps
     * this one, so the tokens of this phone stay valid.
     */
    suspend fun changePassword(currentPassword: String, newPassword: String)

    /** Revokes this session on the server; a Guest account is deleted. */
    suspend fun logout()

    /** A Guest with a pending promotion chooses a username and a password. Returns new tokens. */
    suspend fun completePromotion(request: CompletePromotionRequest): TokenAnswer

    /**
     * `DELETE /me` (right to erasure): the server erases the account and its personal data, the movements stay
     * as "Deleted user". [password]: the current password, null for a Guest (who has none). Every session of
     * the user ends, this one too.
     */
    suspend fun deleteAccount(password: String?)
}
