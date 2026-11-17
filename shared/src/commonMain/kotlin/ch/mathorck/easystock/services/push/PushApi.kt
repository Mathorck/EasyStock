package ch.mathorck.easystock.services.push

/** The push calls. A fake in tests. */
interface PushApi {
    /** Saves [endpoint] for the current session (the server replaces the previous one). */
    suspend fun register(endpoint: String)

    /** Forgets the address of the current session. */
    suspend fun unregister()
}
