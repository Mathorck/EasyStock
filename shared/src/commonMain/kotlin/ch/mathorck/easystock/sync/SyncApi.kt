package ch.mathorck.easystock.sync

import ch.mathorck.easystock.server.ServerClient
import ch.mathorck.easystock.server.ServerJson
import ch.mathorck.easystock.server.apiCall
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.content.ByteArrayContent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Sync endpoints, see 03-server-spec.md "Sync" and "Images".

/** `POST /sync/push` body. [rows]: wire rows by table name, in dependency order. */
@Serializable
data class PushRequest(
    val batchId: String,
    val rows: Map<String, List<JsonObject>>,
)

/** Status of one pushed row. */
@Serializable
enum class PushStatus {
    @SerialName("accepted") ACCEPTED,
    @SerialName("changed") CHANGED,
    @SerialName("rejected") REJECTED,
}

/**
 * The answer for one pushed row. [row]: the fields the server changed ([PushStatus.CHANGED]) or its
 * current version ([PushStatus.REJECTED], empty or absent when it has none).
 */
@Serializable
data class PushResult(
    val table: String,
    val id: String,
    val status: PushStatus,
    val conflict: String? = null,
    val reason: String? = null,
    val row: JsonObject? = null,
)

@Serializable
data class PushAnswer(val results: List<PushResult> = emptyList())

@Serializable
data class WireUser(val id: String, val displayName: String)

@Serializable
data class WireAlert(val id: String, val articleId: String, val crossedAt: Long, val stock: Long)

/** `GET /sync/pull` answer: rows by table, in `server_seq` order within each table. */
@Serializable
data class PullAnswer(
    val rows: Map<String, List<JsonObject>> = emptyMap(),
    val users: List<WireUser> = emptyList(),
    val alerts: List<WireAlert> = emptyList(),
    val next: Long,
    val hasMore: Boolean = false,
    /** The server's data set ([DatasetEpochStore]), null from a server without it. */
    val datasetEpoch: String? = null,
)

/** Conflict codes of a "changed" answer (03-server-spec.md, "Conflict rules"). */
object ConflictCodes {
    const val ATTRIBUTE_MERGED = "ATTRIBUTE_MERGED"
    const val CATEGORY_ATTRIBUTE_SKIPPED = "CATEGORY_ATTRIBUTE_SKIPPED"
    const val MOVEMENT_KEPT = "MOVEMENT_KEPT"
}

/** The sync calls. A fake in tests. */
interface SyncApi {
    /**
     * Sends [body], a [PushRequest] as JSON, exactly as stored (a resent batch must be identical).
     * [epoch]: the data set the phone holds ([DATASET_EPOCH_HEADER]), null to send none.
     */
    suspend fun push(body: String, epoch: String? = null): PushAnswer

    suspend fun pull(since: Long, limit: Int = PULL_LIMIT, epoch: String? = null): PullAnswer

    /** True when the server has the photo [sha256]. */
    suspend fun hasImage(sha256: String): Boolean

    suspend fun putImage(sha256: String, jpeg: ByteArray)

    /** The photo, null when the server doesn't have it (404). */
    suspend fun getImage(sha256: String): ByteArray?

    companion object {
        /** Rows per pull page (server default 500, maximum 2000). */
        const val PULL_LIMIT = 500
    }
}

class KtorSyncApi(private val client: ServerClient) : SyncApi {
    private val http get() = client.http

    override suspend fun push(body: String, epoch: String?): PushAnswer = apiCall {
        http.post("sync/push") {
            epoch?.let { header(DATASET_EPOCH_HEADER, it) }
            setBody(TextContent(body, ContentType.Application.Json))
        }.body()
    }

    override suspend fun pull(since: Long, limit: Int, epoch: String?): PullAnswer = apiCall {
        http.get("sync/pull") {
            epoch?.let { header(DATASET_EPOCH_HEADER, it) }
            parameter("since", since)
            parameter("limit", limit)
        }.body()
    }

    override suspend fun hasImage(sha256: String): Boolean = apiCall {
        val response = http.head("images/$sha256") { expectSuccess = false }
        when (response.status) {
            HttpStatusCode.OK -> true
            HttpStatusCode.NotFound -> false
            else -> throw ResponseException(response, "")
        }
    }

    override suspend fun putImage(sha256: String, jpeg: ByteArray) {
        apiCall { http.put("images/$sha256") { setBody(ByteArrayContent(jpeg, ContentType.Image.JPEG)) } }
    }

    override suspend fun getImage(sha256: String): ByteArray? = apiCall {
        val response = http.get("images/$sha256") { expectSuccess = false }
        when (response.status) {
            HttpStatusCode.OK -> response.readRawBytes()
            HttpStatusCode.NotFound -> null
            else -> throw ResponseException(response, "")
        }
    }
}

/** The JSON body of a push. */
fun encodePush(request: PushRequest): String = ServerJson.encodeToString(PushRequest.serializer(), request)

fun decodePush(body: String): PushRequest = ServerJson.decodeFromString(PushRequest.serializer(), body)
