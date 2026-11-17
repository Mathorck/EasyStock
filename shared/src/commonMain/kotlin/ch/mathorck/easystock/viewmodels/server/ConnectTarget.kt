package ch.mathorck.easystock.viewmodels.server

import ch.mathorck.easystock.services.server.JoinCode
import ch.mathorck.easystock.services.server.ServerConnection

/** A server to join, and the code it was reached with (null: a typed address, the user logs in). */
data class ConnectTarget(
    val connection: ServerConnection,
    val code: JoinCode? = null,
    /** A typed address without scheme tried over HTTPS: this plain HTTP address can be offered after a failure. */
    val httpFallback: String? = null,
)
