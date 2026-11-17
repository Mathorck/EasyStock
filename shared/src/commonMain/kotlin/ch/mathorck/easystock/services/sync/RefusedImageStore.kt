package ch.mathorck.easystock.services.sync

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
