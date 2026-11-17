package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

// JSON bodies of the /admin endpoints, the same shapes as the server's Models (AdminUserModels.cs,
// AdminDeviceModels.cs, AdminInviteModels.cs, ServerSettings.cs; server/openapi/v1.json). Lists are plain
// JSON arrays. A field the app can live without has a default, so a newer server stays readable.

/** A pending promotion of a Guest: they must choose a username and password to get [role]. */
@Serializable
data class PendingPromotion(
    val role: String,
    val expiresAt: String? = null,
)
