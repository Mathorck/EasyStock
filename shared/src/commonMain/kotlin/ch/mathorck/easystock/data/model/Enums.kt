package ch.mathorck.easystock.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// Enum-like columns are stored as lowercase strings (see docs/schema.dbml).
// Backups (JSON) use the same values, unknown values are rejected.

@Serializable(with = AttributeDataTypeSerializer::class)
enum class AttributeDataType { TEXT, NUMBER, BOOL, DATE, ENUM }

@Serializable(with = TrackingSerializer::class)
enum class Tracking { QUANTITY, SERIAL }

@Serializable(with = ItemStatusSerializer::class)
enum class ItemStatus { AVAILABLE, LENT, REPAIR, LOST }

@Serializable(with = MovementReasonSerializer::class)
enum class MovementReason { PURCHASE, CONSUMPTION, INVENTORY, TRANSFER, LOAN, RETURN, LOSS }

/** Value stored in the database. */
val Enum<*>.dbValue: String get() = name.lowercase()

/** Parses a stored value. Throws on unknown values instead of guessing. */
inline fun <reified E : Enum<E>> enumFromDb(value: String): E =
    enumValues<E>().firstOrNull { it.dbValue == value }
        ?: throw IllegalArgumentException("Unknown ${E::class.simpleName} value: '$value'")

/** JSON as the database value ([dbValue]). */
abstract class DbEnumSerializer<E : Enum<E>>(name: String, private val values: Array<E>) : KSerializer<E> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ch.mathorck.easystock.$name", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: E) = encoder.encodeString(value.dbValue)

    override fun deserialize(decoder: Decoder): E {
        val value = decoder.decodeString()
        return values.firstOrNull { it.dbValue == value } ?: throw SerializationException("Unknown value: '$value'")
    }
}

object AttributeDataTypeSerializer : DbEnumSerializer<AttributeDataType>("AttributeDataType", AttributeDataType.entries.toTypedArray())
object TrackingSerializer : DbEnumSerializer<Tracking>("Tracking", Tracking.entries.toTypedArray())
object ItemStatusSerializer : DbEnumSerializer<ItemStatus>("ItemStatus", ItemStatus.entries.toTypedArray())
object MovementReasonSerializer : DbEnumSerializer<MovementReason>("MovementReason", MovementReason.entries.toTypedArray())
