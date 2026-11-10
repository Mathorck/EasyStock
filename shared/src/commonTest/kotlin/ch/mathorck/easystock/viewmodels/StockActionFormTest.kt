package ch.mathorck.easystock.viewmodels

import ch.mathorck.easystock.composables.attributes.AttributeError
import ch.mathorck.easystock.data.db.CategoryAttributeWithDefinition
import ch.mathorck.easystock.data.db.ItemLastLocation
import ch.mathorck.easystock.data.db.editableStatuses
import ch.mathorck.easystock.data.db.UnitAction
import ch.mathorck.easystock.data.db.unitActions
import ch.mathorck.easystock.data.db.unitTransition
import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.AttributeDefinition
import ch.mathorck.easystock.data.model.CategoryAttribute
import ch.mathorck.easystock.data.model.Item
import ch.mathorck.easystock.data.model.ItemStatus
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.MovementReason
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StockActionFormTest {
    private val context = StockActionContext(
        stockByLocation = mapOf("shelf" to 10_000L, "van" to 500L),
        activeLocationIds = setOf("shelf", "van"),
        serials = listOf("SN-1"),
        items = listOf(
            Item(id = "i1", articleId = "a", serialNumber = "SN-1", locationId = "shelf", createdAt = 0, updatedAt = 0),
            Item(id = "i2", articleId = "a", status = ItemStatus.LENT, createdAt = 0, updatedAt = 0),
        ),
    )

    // ---------- Quantity ----------

    @Test
    fun quantityIsRequiredValidAndPositive() {
        assertEquals(StockActionError.REQUIRED, validateQuantity(" ", null))
        assertEquals(StockActionError.INVALID_QUANTITY, validateQuantity("-1", null))
        assertEquals(StockActionError.INVALID_QUANTITY, validateQuantity("1.2345", null))
        assertEquals(StockActionError.NOT_POSITIVE, validateQuantity("0", null))
        assertNull(validateQuantity("0,001", null))
    }

    @Test
    fun quantityCannotExceedTheAvailableStock() {
        assertNull(validateQuantity("10", 10_000))
        assertEquals(StockActionError.EXCEEDS_STOCK, validateQuantity("10.001", 10_000))
        assertEquals(StockActionError.EXCEEDS_STOCK, validateQuantity("1", 0))
    }

    @Test
    fun removeChecksTheStockAtItsLocation() {
        val form = StockActionForm(kind = StockActionKind.REMOVE, sourceLocationId = "van", quantity = "1")
        assertEquals(StockActionError.EXCEEDS_STOCK, form.validate(context).quantity)
        assertTrue(form.copy(quantity = "0.5").validate(context).isValid)
    }

    @Test
    fun addHasNoUpperLimit() {
        val form = StockActionForm(kind = StockActionKind.ADD, sourceLocationId = "van", quantity = "999999")
        assertTrue(form.validate(context).isValid)
    }

    // ---------- Reasons ----------

    @Test
    fun reasonsDependOnTheDirection() {
        assertEquals(listOf(MovementReason.PURCHASE, MovementReason.RETURN, MovementReason.INVENTORY), reasonsFor(StockActionKind.ADD))
        assertEquals(listOf(MovementReason.CONSUMPTION, MovementReason.LOSS, MovementReason.INVENTORY), reasonsFor(StockActionKind.REMOVE))
        assertTrue(reasonsFor(StockActionKind.MOVE).isEmpty())
    }

    @Test
    fun defaultReasonsAreValidAndAnOutReasonIsRejectedForAnAdd() {
        assertEquals(MovementReason.PURCHASE, StockActionForm(kind = StockActionKind.ADD).reason)
        assertEquals(MovementReason.CONSUMPTION, StockActionForm(kind = StockActionKind.REMOVE).reason)
        val form = StockActionForm(kind = StockActionKind.ADD, sourceLocationId = "shelf", quantity = "1", reason = MovementReason.CONSUMPTION)
        assertEquals(StockActionError.REQUIRED, form.validate(context).reason)
    }

    // ---------- Locations ----------

    @Test
    fun moveNeedsAnotherActiveLocation() {
        val form = StockActionForm(kind = StockActionKind.MOVE, sourceLocationId = "shelf", quantity = "2")
        assertEquals(StockActionError.REQUIRED, form.validate(context).location)
        assertEquals(StockActionError.SAME_LOCATION, form.copy(targetLocationId = "shelf").validate(context).location)
        assertEquals(StockActionError.REQUIRED, form.copy(targetLocationId = "gone").validate(context).location)
        assertTrue(form.copy(targetLocationId = "van").validate(context).isValid)
        assertEquals(StockActionError.EXCEEDS_STOCK, form.copy(targetLocationId = "van", quantity = "11").validate(context).quantity)
    }

    @Test
    fun newLocationNeedsAName() {
        val form = StockActionForm(kind = StockActionKind.ADD, quantity = "1", targetMode = LocationChoice.NEW)
        assertEquals(StockActionError.REQUIRED, form.validate(context).locationName)
        assertEquals(StockActionError.TOO_LONG, form.copy(newLocationName = "x".repeat(101)).validate(context).locationName)
        val valid = form.copy(newLocationName = " Garage ", newLocationParentId = "shelf")
        assertTrue(valid.validate(context).isValid)
        val location = valid.newLocation(now = 7, newId = { "new" })
        assertEquals(Location(id = "new", name = "Garage", parentId = "shelf", createdAt = 7, updatedAt = 7), location)
    }

    @Test
    fun addAtARowNeedsNoPicker() {
        assertFalse(StockActionForm(kind = StockActionKind.ADD, sourceLocationId = "shelf").needsTarget)
        assertTrue(StockActionForm(kind = StockActionKind.ADD).needsTarget)
        assertNull(StockActionForm(kind = StockActionKind.ADD, sourceLocationId = "shelf", targetMode = LocationChoice.NEW).newLocation(0) { "x" })
    }

    // ---------- Serial units ----------

    @Test
    fun serialIsOptionalLimitedAndUniqueIgnoringCase() {
        assertNull(validateSerial("  ", listOf("SN-1")))
        assertNull(validateSerial("SN-2", listOf("SN-1")))
        assertEquals(StockActionError.DUPLICATE_SERIAL, validateSerial(" sn-1 ", listOf("SN-1")))
        assertEquals(StockActionError.TOO_LONG, validateSerial("x".repeat(101), emptyList()))
        assertNull(validateSerial("x".repeat(100), emptyList()))
    }

    @Test
    fun addUnitUsesTheRejectedSerial() {
        val form = StockActionForm(kind = StockActionKind.ADD_UNIT, serial = "SN-9", targetLocationId = "shelf")
        assertTrue(form.validate(context).isValid)
        assertEquals(StockActionError.DUPLICATE_SERIAL, form.validate(context, rejectedSerial = "sn-9").serial)
    }

    @Test
    fun moveUnitNeedsAnotherLocationAndAUnitInStock() {
        val form = StockActionForm(kind = StockActionKind.MOVE_UNIT, itemId = "i1", targetLocationId = "shelf")
        assertEquals(StockActionError.SAME_LOCATION, form.validate(context).location)
        assertTrue(form.copy(targetLocationId = "van").validate(context).isValid)
        assertEquals(StockActionError.REQUIRED, form.copy(itemId = "i2", targetLocationId = "van").validate(context).unit)
    }

    @Test
    fun returnNeedsALocationAndALentUnit() {
        val form = StockActionForm(kind = StockActionKind.UNIT_STATUS, itemId = "i2", unitAction = UnitAction.RETURN)
        assertTrue(form.needsTarget)
        assertEquals(StockActionError.REQUIRED, form.validate(context).location)
        assertTrue(form.copy(targetLocationId = "shelf").validate(context).isValid)
        assertEquals(StockActionError.REQUIRED, form.copy(itemId = "i1", targetLocationId = "shelf").validate(context).unit)
        assertFalse(form.copy(unitAction = UnitAction.LEND).needsTarget)
    }

    @Test
    fun loanAndLossTakeTheUnitOutOfStock() {
        val lend = unitTransition(ItemStatus.AVAILABLE, UnitAction.LEND)!!
        assertEquals(ItemStatus.LENT, lend.newStatus)
        assertEquals(-1000, lend.delta)
        assertEquals(MovementReason.LOAN, lend.reason)

        val back = unitTransition(ItemStatus.LENT, UnitAction.RETURN)!!
        assertEquals(1000, back.delta)
        assertEquals(MovementReason.RETURN, back.reason)
        assertTrue(back.needsLocation)

        assertEquals(-1000, unitTransition(ItemStatus.AVAILABLE, UnitAction.LOSE)!!.delta)
        assertEquals(-1000, unitTransition(ItemStatus.REPAIR, UnitAction.LOSE)!!.delta)
        // Already out of stock through the loan: no second movement
        assertEquals(0, unitTransition(ItemStatus.LENT, UnitAction.LOSE)!!.delta)
        assertEquals(1000, unitTransition(ItemStatus.LOST, UnitAction.FOUND)!!.delta)
    }

    @Test
    fun repairOnlyChangesTheStatus() {
        assertEquals(0, unitTransition(ItemStatus.AVAILABLE, UnitAction.REPAIR)!!.delta)
        assertEquals(ItemStatus.AVAILABLE, unitTransition(ItemStatus.REPAIR, UnitAction.END_REPAIR)!!.newStatus)
    }

    @Test
    fun actionsPerStatus() {
        assertEquals(listOf(UnitAction.LEND, UnitAction.REPAIR, UnitAction.LOSE), unitActions(ItemStatus.AVAILABLE))
        assertEquals(listOf(UnitAction.RETURN, UnitAction.LOSE), unitActions(ItemStatus.LENT))
        assertEquals(listOf(UnitAction.END_REPAIR, UnitAction.LOSE), unitActions(ItemStatus.REPAIR))
        assertEquals(listOf(UnitAction.FOUND), unitActions(ItemStatus.LOST))
    }

    @Test
    fun unitRowsShowThePathAndTheLastLocation() {
        val locations = listOf(
            Location(id = "room", name = "Room", createdAt = 0, updatedAt = 0),
            Location(id = "shelf", name = "Shelf", parentId = "room", createdAt = 0, updatedAt = 0),
        )
        val rows = unitRows(context.items, listOf(ItemLastLocation("i2", "shelf")), locations)
        assertEquals("Room › Shelf", rows[0].path)
        assertTrue(rows[0].canMove)
        assertNull(rows[1].path)
        assertEquals("shelf", rows[1].lastLocationId)
        assertFalse(rows[1].canMove)
    }

    // ---------- Unit properties ----------

    private val ropeAttributes = listOf(
        CategoryAttributeWithDefinition(
            link = CategoryAttribute(id = "l1", categoryId = "c", attributeId = "len", required = true, createdAt = 0, updatedAt = 0),
            definition = AttributeDefinition(id = "len", key = "len", label = "Length", dataType = AttributeDataType.NUMBER, createdAt = 0, updatedAt = 0),
        ),
    )
    private val withAttributes = context.copy(itemAttributes = ropeAttributes)

    @Test
    fun editedUnitMayKeepItsOwnSerialButNotTakeAnother() {
        val other = context.copy(serials = listOf("SN-1", "SN-2"))
        fun edit(serial: String) = StockActionForm(kind = StockActionKind.EDIT_UNIT, itemId = "i1", serial = serial)
        assertTrue(edit("sn-1").validate(other).isValid)
        assertEquals(StockActionError.DUPLICATE_SERIAL, edit(" SN-2 ").validate(other).serial)
        assertEquals(StockActionError.DUPLICATE_SERIAL, edit("SN-9").validate(other, rejectedSerial = "sn-9").serial)
        assertNull(edit("").validate(other).serial)
        assertEquals(StockActionError.TOO_LONG, edit("x".repeat(101)).validate(other).serial)
    }

    @Test
    fun statusCanSwitchOnlyBetweenStatusesThatKeepTheUnitInStock() {
        assertEquals(listOf(ItemStatus.AVAILABLE, ItemStatus.REPAIR), editableStatuses(ItemStatus.AVAILABLE))
        assertEquals(listOf(ItemStatus.AVAILABLE, ItemStatus.REPAIR), editableStatuses(ItemStatus.REPAIR))
        assertEquals(listOf(ItemStatus.LENT), editableStatuses(ItemStatus.LENT))
        assertEquals(listOf(ItemStatus.LOST), editableStatuses(ItemStatus.LOST))

        fun edit(id: String, status: ItemStatus) = StockActionForm(kind = StockActionKind.EDIT_UNIT, itemId = id, itemStatus = status).validate(context)
        assertTrue(edit("i1", ItemStatus.REPAIR).isValid)
        assertEquals(StockActionError.REQUIRED, edit("i1", ItemStatus.LOST).unit)
        assertEquals(StockActionError.REQUIRED, edit("i1", ItemStatus.LENT).unit)
        assertTrue(edit("i2", ItemStatus.LENT).isValid) // unchanged
        assertEquals(StockActionError.REQUIRED, edit("i2", ItemStatus.AVAILABLE).unit)
        assertEquals(StockActionError.REQUIRED, edit("gone", ItemStatus.AVAILABLE).unit)
    }

    @Test
    fun newUnitStartsInStock() {
        fun add(status: ItemStatus) = StockActionForm(kind = StockActionKind.ADD_UNIT, itemStatus = status, targetLocationId = "shelf").validate(context)
        assertTrue(add(ItemStatus.AVAILABLE).isValid)
        assertTrue(add(ItemStatus.REPAIR).isValid)
        assertEquals(StockActionError.REQUIRED, add(ItemStatus.LENT).unit)
        assertEquals(StockActionError.REQUIRED, add(ItemStatus.LOST).unit)
    }

    @Test
    fun itemAttributeOverridesAreOptionalButValidated() {
        // The category says "required", a unit may leave it empty (it inherits the article value)
        assertTrue(itemAttributeOverrides(ropeAttributes).none { it.link.required })
        val empty = StockActionForm(kind = StockActionKind.EDIT_UNIT, itemId = "i1")
        assertTrue(empty.validate(withAttributes).isValid)

        val bad = empty.copy(itemAttributes = buildJsonObject { put("len", "abc") })
        assertEquals(mapOf("len" to AttributeError.INVALID_NUMBER), bad.validate(withAttributes).attributes)
        assertFalse(bad.validate(withAttributes).isValid)

        val good = empty.copy(itemAttributes = buildJsonObject { put("len", JsonPrimitive(12)) })
        assertTrue(good.validate(withAttributes).isValid)
        // The "add unit" sheet checks them too
        val add = StockActionForm(kind = StockActionKind.ADD_UNIT, targetLocationId = "shelf", itemAttributes = bad.itemAttributes)
        assertFalse(add.validate(withAttributes).isValid)
    }

    @Test
    fun editNeedsNoTargetNorQuantity() {
        val form = StockActionForm(kind = StockActionKind.EDIT_UNIT, itemId = "i1")
        assertFalse(form.needsTarget)
        assertFalse(form.needsQuantity)
        assertTrue(form.hasUnitProperties)
    }
}

