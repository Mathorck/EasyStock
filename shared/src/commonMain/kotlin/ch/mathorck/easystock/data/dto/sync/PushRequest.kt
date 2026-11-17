package ch.mathorck.easystock.data.dto.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Sync endpoints, see 03-server-spec.md "Sync" and "Images".

/** `POST /sync/push` body. [rows]: wire rows by table name, in dependency order. */
@Serializable
data class PushRequest(
    val batchId: String,
    val rows: Map<String, List<JsonObject>>,
)
