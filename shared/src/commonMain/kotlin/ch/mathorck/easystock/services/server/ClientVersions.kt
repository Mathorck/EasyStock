package ch.mathorck.easystock.services.server

import ch.mathorck.easystock.data.db.DATA_SCHEMA_VERSION

/** Headers sent with every request, so the server can refuse a too old app (426). */
data class ClientVersions(
    /** App version name, e.g. `1.4.0`. */
    val appVersion: String,
    /** Shared data version of this app ([DATA_SCHEMA_VERSION], not [DB_VERSION]: an index-only version keeps it). */
    val schemaVersion: Int = DATA_SCHEMA_VERSION,
) {
    companion object {
        const val APP_HEADER = "X-EasyStock-App-Version"
        const val SCHEMA_HEADER = "X-EasyStock-Schema-Version"
    }
}
