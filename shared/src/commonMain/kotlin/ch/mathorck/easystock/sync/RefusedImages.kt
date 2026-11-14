package ch.mathorck.easystock.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The photos a server refused (hash to the Problem `type` of the refusal), kept across a restart so they
 * are sent again at the next cycles without waiting for a change of their article. Not in the database:
 * a preferences entry, no schema change.
 */
interface RefusedImageStore {
    /** The encoded list ([RefusedImages.encode]), null when none. */
    suspend fun load(): String?

    /** Null forgets it. */
    suspend fun save(value: String?)
}

class InMemoryRefusedImageStore : RefusedImageStore {
    @Volatile
    private var value: String? = null

    override suspend fun load(): String? = value

    override suspend fun save(value: String?) {
        this.value = value
    }
}

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
