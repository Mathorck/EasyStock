package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

// JSON bodies of the server API (base path /api/v1, camelCase names, ISO 8601 UTC times as strings).
// Same shapes as the server's Models (server/src/EasyStock.Server/Models, server/openapi/v1.json).
// Every response field the app can live without has a default, so a newer server with more or fewer
// fields is still readable.

/** `GET /server-info` (public, no token, no version header needed). */
@Serializable
data class ServerInfo(
    val serverName: String = "",
    val serverVersion: String = "",
    val apiVersion: Int = 0,
    val minAppVersion: String = "",
    val supportedSchemaVersions: List<Int> = emptyList(),
    val setupDone: Boolean = false,
    val hasData: Boolean = false,
    val guestSignupEnabled: Boolean = false,
    /** The server's data set (changes when an Admin replaces the data): see [ch.mathorck.easystock.services.sync.DatasetEpochStore]. */
    val datasetEpoch: String = "",
)
