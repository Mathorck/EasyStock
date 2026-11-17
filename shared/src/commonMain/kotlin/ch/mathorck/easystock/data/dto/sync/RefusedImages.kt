package ch.mathorck.easystock.data.dto.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The refused photos of the server [serverUrl]: another server's are never sent. */
@Serializable
data class RefusedImages(val serverUrl: String, val images: Map<String, String?>) {
    fun encode(): String = Json.encodeToString(serializer(), this)

    companion object {
        /** Null when [value] is null or can't be read (a lost list: the photos are sent with their next change). */
        fun decode(value: String?): RefusedImages? {
            if (value.isNullOrEmpty()) return null
            return try {
                Json.decodeFromString(serializer(), value)
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
