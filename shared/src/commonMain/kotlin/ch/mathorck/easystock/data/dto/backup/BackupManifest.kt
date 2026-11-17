package ch.mathorck.easystock.data.dto.backup

import ch.mathorck.easystock.data.db.DATA_SCHEMA_VERSION
import ch.mathorck.easystock.services.backup.BACKUP_FORMAT
import ch.mathorck.easystock.services.backup.BACKUP_FORMAT_VERSION
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BackupManifest(
    val format: String = BACKUP_FORMAT,
    @SerialName("format_version") val formatVersion: Int = BACKUP_FORMAT_VERSION,
    @SerialName("app_version") val appVersion: String,
    /** [DATA_SCHEMA_VERSION]: the shape of the tables, so the server and older apps with the same tables can import it. */
    @SerialName("schema_version") val schemaVersion: Int = DATA_SCHEMA_VERSION,
    /** ISO 8601, UTC ("2026-11-10T14:03:00Z"). */
    @SerialName("created_at") val createdAt: String,
    /** Rows per table name, plus "images". */
    val counts: Map<String, Int> = emptyMap(),
)
