package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

/** `PATCH /me` with the alert preference (the server also takes `displayName`, which the app does not send here). */
@Serializable
data class UpdateAlertsRequest(val alertsEnabled: Boolean)
