package ch.mathorck.easystock.enums.attributes

import ch.mathorck.easystock.data.model.AttributeDataTypeSerializer
import kotlinx.serialization.Serializable

// Enum-like columns are stored as lowercase strings (see docs/schema.dbml).
// Backups (JSON) use the same values, unknown values are rejected.

@Serializable(with = AttributeDataTypeSerializer::class)
enum class AttributeDataType {
    TEXT, NUMBER, BOOL, DATE, ENUM,

    /** Number with a unit chosen per value, see [ch.mathorck.easystock.composables.attributes.readMeasure]. */
    MEASURE,
}
