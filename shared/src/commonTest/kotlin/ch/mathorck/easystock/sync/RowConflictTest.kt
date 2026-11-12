package ch.mathorck.easystock.sync

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RowConflictTest {
    private val phone = buildJsonObject {
        put("id", "c1")
        put("name", "Mine")
        put("updatedAt", 100)
    }

    @Test
    fun theReasonHoldsThePhoneRowAndTheServerSeq() {
        val reason = RowConflict.reasonOf(phone.withBaseSeq(5), 9)
        assertEquals(RowConflict.Payload(phone, 9), RowConflict.payloadOf(reason))
        assertEquals(CONFLICT_REASON, RowConflict.codeOf(reason))
    }

    @Test
    fun otherReasonsHaveNoPayload() {
        assertNull(RowConflict.payloadOf(CONFLICT_REASON))
        assertNull(RowConflict.payloadOf("conflict:not json"))
        assertNull(RowConflict.payloadOf("""conflict:{"row":{"id":"c1"}}"""))
        assertNull(RowConflict.payloadOf("movement-kept:{}"))
        assertEquals("not-allowed", RowConflict.codeOf("not-allowed"))
    }

    @Test
    fun keepMineIsANewLocalEdit() {
        assertEquals(JsonPrimitive(500), RowConflict.keepMineRow(phone, 500)["updatedAt"])
        val noTime = buildJsonObject { put("id", "l1") }
        assertEquals(noTime, RowConflict.keepMineRow(noTime, 500))
    }
}
