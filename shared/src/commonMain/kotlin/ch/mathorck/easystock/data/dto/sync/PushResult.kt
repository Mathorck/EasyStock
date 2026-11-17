package ch.mathorck.easystock.data.dto.sync

import ch.mathorck.easystock.enums.sync.PushStatus
import ch.mathorck.easystock.services.sync.serverSeq
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * The answer for one pushed row. [row]: the fields the server changed ([PushStatus.CHANGED]) or its
 * current version ([PushStatus.REJECTED], empty or absent when it has none). [serverSeq]: the row's
 * `server_seq` on the server after this push (the server row's for a rejected row), null when the server
 * has no such row: the next `baseSeq` of this row.
 */
@Serializable
data class PushResult(
    val table: String,
    val id: String,
    val status: PushStatus,
    val conflict: String? = null,
    val reason: String? = null,
    val row: JsonObject? = null,
    val serverSeq: Long? = null,
)
