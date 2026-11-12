package ch.mathorck.easystock.sync

import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.StockMovement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MovementKeptTest {
    private val phone = StockMovement("m1", "a1", null, "l1", 3000, MovementReason.PURCHASE, "box", occurredAt = 5, createdAt = 5)
    private val sent = toWire(SyncTable.STOCK_MOVEMENT, phone)
    private val serverRow = buildJsonObject { put("quantity", 2000) }

    private fun kept(row: kotlinx.serialization.json.JsonObject? = serverRow, table: String = "stock_movement") =
        PushResult(table, "m1", PushStatus.CHANGED, conflict = ConflictCodes.MOVEMENT_KEPT, row = row)

    @Test
    fun movementKeptWritesTheServerRowAndKeepsThePhoneOne() {
        val decision = decidePush(kept(), sent, sent)
        assertEquals(PushDecision.MovementKept(sent.overlay(serverRow), sent), decision)
        assertEquals(JsonPrimitive(2000), (decision as PushDecision.MovementKept).server["quantity"])
    }

    @Test
    fun movementKeptWithoutRowKeepsThePhoneVersion() {
        assertEquals(PushDecision.Done, decidePush(kept(row = null), sent, sent))
    }

    @Test
    fun movementKeptOnAnotherTableIsAPlainChange() {
        assertEquals(PushDecision.Write(sent.overlay(serverRow)), decidePush(kept(table = "article"), sent, sent))
    }

    @Test
    fun aMovementChangedAgainIsKept() {
        val current = toWire(SyncTable.STOCK_MOVEMENT, phone.copy(comment = "other"))
        assertEquals(PushDecision.Keep, decidePush(kept(), sent, current))
    }

    @Test
    fun theReasonHoldsThePhoneRow() {
        val reason = MovementKept.reasonOf(sent)
        assertEquals(MovementKept.REASON, MovementKept.codeOf(reason))
        assertEquals(sent, MovementKept.phoneRowOf(reason))
        assertEquals(phone, MovementKept.phoneMovementOf(reason))
        assertEquals("not-allowed", MovementKept.codeOf("not-allowed"))
        assertNull(MovementKept.phoneRowOf("not-allowed"))
        assertNull(MovementKept.phoneRowOf("movement-kept:{broken"))
        assertNull(MovementKept.phoneMovementOf("movement-kept:{}"))
    }

    private fun block(
        movement: StockMovement = phone,
        canStockAction: Boolean = true,
        articleActive: Boolean = true,
        locationActive: Boolean = true,
        stock: Long = 0,
    ) = keepMineBlock(movement, canStockAction, articleActive, locationActive, stock)

    @Test
    fun keepMineIsOfferedForAPlainMovement() {
        assertNull(block())
        assertNull(block(phone.copy(quantityMilli = -1000, reason = MovementReason.CONSUMPTION), stock = 1000))
        // A removal from a deleted location is still allowed (as StockActionDao.adjust)
        assertNull(block(phone.copy(quantityMilli = -1000, reason = MovementReason.LOSS), locationActive = false, stock = 2000))
    }

    @Test
    fun keepMineIsNotOfferedWhenTheRulesForbidIt() {
        assertEquals(KeepMineBlock.ROLE, block(canStockAction = false))
        assertEquals(KeepMineBlock.SERIAL_UNIT, block(phone.copy(itemId = "i1", quantityMilli = 1000)))
        assertEquals(KeepMineBlock.TRANSFER, block(phone.copy(reason = MovementReason.TRANSFER)))
        assertEquals(KeepMineBlock.INVALID, block(phone.copy(quantityMilli = 0)))
        assertEquals(KeepMineBlock.INVALID, block(phone.copy(reason = MovementReason.LOSS)))
        assertEquals(KeepMineBlock.INVALID, block(phone.copy(quantityMilli = -1000)))
        assertEquals(KeepMineBlock.ARTICLE_GONE, block(articleActive = false))
        assertEquals(KeepMineBlock.LOCATION_GONE, block(locationActive = false))
        assertEquals(
            KeepMineBlock.NOT_ENOUGH_STOCK,
            block(phone.copy(quantityMilli = -1500, reason = MovementReason.CONSUMPTION), stock = 1000),
        )
    }

    @Test
    fun keepMineIsANewMovementDatedNow() {
        val authored = phone.copy(createdBy = "u2", deviceId = "d2")
        assertEquals(
            StockMovement("m9", "a1", null, "l1", 3000, MovementReason.PURCHASE, "box", occurredAt = 99, createdAt = 99),
            keepMineMovement(authored, "m9", 99),
        )
    }
}
