package ch.mathorck.easystock.services.server

/**
 * The client of the current connection, rebuilt when the server changes and closed when it is replaced
 * or [release]d. [create] runs only when [clientFor] is called, so only with a `sync_state` row.
 * Used from the main thread (composition).
 */
class ServerClientHolder(private val create: (ServerConnection) -> ServerClient) {
    private var current: ServerClient? = null

    fun clientFor(connection: ServerConnection): ServerClient =
        current?.takeIf { it.connection == connection } ?: create(connection).also {
            current?.close()
            current = it
        }

    /** Leaving the server: closes the client. */
    fun release() {
        current?.close()
        current = null
    }
}
