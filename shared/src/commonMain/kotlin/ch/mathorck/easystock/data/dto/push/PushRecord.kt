package ch.mathorck.easystock.data.dto.push

import kotlinx.serialization.Serializable

/**
 * What the phone remembers about UnifiedPush, outside the database (it is not data, and a wipe forgets it).
 * [enabled]: the user chose to use [distributor]. [endpoint]: the last address the distributor gave.
 * [sentEndpoint] and [sentServer]: the address the server confirmed, and that server.
 * [failed]: the distributor refused the registration.
 */
@Serializable
data class PushRecord(
    val enabled: Boolean = false,
    val distributor: String? = null,
    val endpoint: String? = null,
    val sentEndpoint: String? = null,
    val sentServer: String? = null,
    val failed: Boolean = false,
)
