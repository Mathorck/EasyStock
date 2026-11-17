package ch.mathorck.easystock.enums.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Status of one pushed row. */
@Serializable
enum class PushStatus {
    @SerialName("accepted") ACCEPTED,
    @SerialName("changed") CHANGED,
    @SerialName("rejected") REJECTED,
}
