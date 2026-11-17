package ch.mathorck.easystock.data.dto.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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
