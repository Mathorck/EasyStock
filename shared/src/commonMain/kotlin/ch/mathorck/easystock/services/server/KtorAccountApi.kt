package ch.mathorck.easystock.services.server

import ch.mathorck.easystock.data.dto.server.ChangePasswordRequest
import ch.mathorck.easystock.data.dto.server.CompletePromotionRequest
import ch.mathorck.easystock.data.dto.server.DeleteMeRequest
import ch.mathorck.easystock.data.dto.server.MeAnswer
import ch.mathorck.easystock.data.dto.server.TokenAnswer
import ch.mathorck.easystock.data.dto.server.UpdateAlertsRequest
import ch.mathorck.easystock.data.dto.server.UpdateDisplayNameRequest
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.ByteReadChannel

/** [AccountApi] over the shared [ServerClient]. */
class KtorAccountApi(private val client: ServerClient) : AccountApi, MyDataApi {
    private val http get() = client.http

    override fun equals(other: Any?): Boolean = other is KtorAccountApi && other.client === client

    override fun hashCode(): Int = client.hashCode()

    override suspend fun me(): MeAnswer = apiCall { http.get("me").body() }

    override suspend fun setAlertsEnabled(enabled: Boolean) {
        apiCall { http.patch("me") { json(UpdateAlertsRequest(enabled)) } }
    }

    override suspend fun setDisplayName(name: String): MeAnswer =
        apiCall { http.patch("me") { json(UpdateDisplayNameRequest(name)) }.body() }

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        apiCall { http.post("me/password") { json(ChangePasswordRequest(currentPassword, newPassword)) } }
    }

    override suspend fun logout() {
        apiCall { http.post("auth/logout") }
    }

    override suspend fun completePromotion(request: CompletePromotionRequest): TokenAnswer =
        apiCall { http.post("auth/complete-promotion") { json(request) }.body() }

    override suspend fun deleteAccount(password: String?) {
        apiCall { http.delete("me") { json(DeleteMeRequest(password)) } }
    }

    override suspend fun downloadMyData(write: suspend (ByteReadChannel) -> Unit) {
        apiCall {
            http.prepareGet("me/data") {
                // It may be large: only the socket timeout applies
                timeout { requestTimeoutMillis = Long.MAX_VALUE }
            }.execute { write(it.bodyAsChannel()) }
        }
    }
}
