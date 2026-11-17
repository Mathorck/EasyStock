package ch.mathorck.easystock.services.auth

import ch.mathorck.easystock.services.server.JoinApi
import ch.mathorck.easystock.services.server.ServerConnection

/** What the connect screen needs from the platform. */
class JoinPlatform(
    val emptyCheck: EmptyPhoneCheck,
    val join: ServerJoin,
    /** A client without token for [ServerConnection] (OkHttp pinned to its fingerprint on Android). */
    val joinApi: (ServerConnection) -> JoinApi,
    /** Sent with every join call, shown in the admin device list. */
    val deviceName: String,
    val appVersion: String,
)
