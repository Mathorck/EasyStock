package ch.mathorck.easystock.data.dto.server

import kotlinx.serialization.Serializable

/** A live device of a user, as listed in [AdminUser.devices] (server `AdminUserDevice`). */
@Serializable
data class AdminDevice(
    val id: String,
    val deviceName: String = "",
    val lastUsedAt: String? = null,
)
