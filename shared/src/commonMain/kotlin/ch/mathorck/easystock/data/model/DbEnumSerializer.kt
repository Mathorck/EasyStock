package ch.mathorck.easystock.data.model

import ch.mathorck.easystock.enums.dbValue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** JSON as the database value ([dbValue]). */
abstract class DbEnumSerializer<E : Enum<E>>(name: String, private val values: Array<E>) : KSerializer<E> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ch.mathorck.easystock.$name", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: E) = encoder.encodeString(value.dbValue)

    override fun deserialize(decoder: Decoder): E {
        val value = decoder.decodeString()
        return values.firstOrNull { it.dbValue == value } ?: throw SerializationException("Unknown value: '$value'")
    }
}
