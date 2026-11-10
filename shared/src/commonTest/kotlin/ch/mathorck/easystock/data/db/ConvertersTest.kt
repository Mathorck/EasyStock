package ch.mathorck.easystock.data.db

import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.ItemStatus
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.Tracking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun enumsAreStoredAsLowercaseSchemaValues() {
        assertEquals("quantity", converters.fromTracking(Tracking.QUANTITY))
        assertEquals("serial", converters.fromTracking(Tracking.SERIAL))
        assertEquals("available", converters.fromItemStatus(ItemStatus.AVAILABLE))
        assertEquals("consumption", converters.fromMovementReason(MovementReason.CONSUMPTION))
        assertEquals("enum", converters.fromAttributeDataType(AttributeDataType.ENUM))
    }

    @Test
    fun everyEnumValueRoundTrips() {
        AttributeDataType.entries.forEach { assertEquals(it, converters.toAttributeDataType(converters.fromAttributeDataType(it))) }
        Tracking.entries.forEach { assertEquals(it, converters.toTracking(converters.fromTracking(it))) }
        ItemStatus.entries.forEach { assertEquals(it, converters.toItemStatus(converters.fromItemStatus(it))) }
        MovementReason.entries.forEach { assertEquals(it, converters.toMovementReason(converters.fromMovementReason(it))) }
    }

    @Test
    fun unknownEnumValueThrows() {
        assertFailsWith<IllegalArgumentException> { converters.toMovementReason("stolen") }
        assertFailsWith<IllegalArgumentException> { converters.toTracking("QUANTITY") }
    }

    @Test
    fun jsonObjectRoundTrips() {
        val attributes = buildJsonObject {
            put("color", "red")
            put("length", 20)
            put("insulated", true)
        }
        val stored = converters.fromJsonObject(attributes)
        assertEquals("""{"color":"red","length":20,"insulated":true}""", stored)
        assertEquals(attributes, converters.toJsonObject(stored))
        assertEquals(JsonPrimitive(20), converters.toJsonObject(stored)!!["length"])
    }

    @Test
    fun stringListRoundTrips() {
        val options = listOf("red", "blue", "with \"quotes\"")
        val stored = converters.fromStringList(options)
        assertEquals(options, converters.toStringList(stored))
        assertEquals("""["red","blue","with \"quotes\""]""", stored)
    }

    @Test
    fun nullJsonStaysNull() {
        assertNull(converters.fromJsonObject(null))
        assertNull(converters.toJsonObject(null))
        assertNull(converters.fromStringList(null))
        assertNull(converters.toStringList(null))
    }
}
