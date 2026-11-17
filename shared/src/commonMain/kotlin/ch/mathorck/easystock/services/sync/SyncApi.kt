package ch.mathorck.easystock.services.sync

import ch.mathorck.easystock.data.dto.server.ServerInfo
import ch.mathorck.easystock.data.dto.sync.PullAnswer
import ch.mathorck.easystock.data.dto.sync.PushAnswer
import ch.mathorck.easystock.data.dto.sync.PushRequest
import ch.mathorck.easystock.services.server.ServerJson
import io.ktor.client.call.body

/** Reason of a stale edit (first change wins): `row` is the whole server row, `serverSeq` its seq. */
const val CONFLICT_REASON = "conflict"

/** The sync calls. A fake in tests. */
interface SyncApi {
    /**
     * Sends [body], a [PushRequest] as JSON, exactly as stored (a resent batch must be identical).
     * [epoch]: the data set the phone holds ([DATASET_EPOCH_HEADER]), null to send none.
     */
    suspend fun push(body: String, epoch: String? = null): PushAnswer

    suspend fun pull(since: Long, limit: Int = PULL_LIMIT, epoch: String? = null): PullAnswer

    /** `GET /server-info` (public): its `datasetEpoch` tells a data set reset before anything is pushed. */
    suspend fun serverInfo(): ServerInfo = ServerInfo()

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

/** The JSON body of a push. */
fun encodePush(request: PushRequest): String = ServerJson.encodeToString(PushRequest.serializer(), request)

fun decodePush(body: String): PushRequest = ServerJson.decodeFromString(PushRequest.serializer(), body)
