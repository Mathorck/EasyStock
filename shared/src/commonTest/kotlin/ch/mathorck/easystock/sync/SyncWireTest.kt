package ch.mathorck.easystock.sync

import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.StockMovement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncWireTest {
    @Test
    fun sha256KnownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Hex(ByteArray(0)))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc".encodeToByteArray()))
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            sha256Hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()),
        )
        // Several blocks, a length on two blocks
        assertEquals(
            "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
            sha256Hex(ByteArray(1_000_000) { 'a'.code.toByte() }),
        )
    }

    @Test
    fun namesAreCamelCaseOnTheWire() {
        assertEquals("articleId", snakeToCamel("article_id"))
        assertEquals("imageSha256", snakeToCamel("image_sha256"))
        assertEquals("id", snakeToCamel("id"))
        assertEquals("article_id", camelToSnake("articleId"))
        assertEquals("image_sha256", camelToSnake("imageSha256"))
    }

    private val article = Article(
        id = "a1",
        sku = "BLT-8",
        name = "Bolt",
        categoryId = "c1",
        barcode = null,
        imagePath = "images/x.jpg",
        attributes = buildJsonObject { put("thread_size", "M8") },
        createdAt = 1,
        updatedAt = 2,
    )

    @Test
    fun anArticleCarriesTheHashOfItsPhotoAndEveryNull() {
        val wire = toWire(SyncTable.ARTICLE, article, imageSha256 = "ab".repeat(32))
        assertEquals(JsonPrimitive("c1"), wire["categoryId"])
        assertEquals(JsonPrimitive("ab".repeat(32)), wire["imageSha256"])
        assertFalse("imagePath" in wire)
        assertFalse("image_path" in wire)
        // A cleared tag must reach the server: nulls are sent
        assertEquals(JsonNull, wire["barcode"])
        assertEquals(JsonNull, wire["deletedAt"])
        // Nested JSON content is never renamed
        assertEquals(JsonPrimitive("M8"), (wire["attributes"] as JsonObject)["thread_size"])

        val back: Article = fromWire(SyncTable.ARTICLE, wire, imagePath = "images/y.jpg")
        assertEquals(article.copy(imagePath = "images/y.jpg"), back)
    }

    @Test
    fun aMovementCarriesNoAuthorNorDevice() {
        val movement = StockMovement(
            id = "m1", articleId = "a1", locationId = "l1", quantityMilli = -2000,
            reason = MovementReason.CONSUMPTION, occurredAt = 5, createdAt = 6, createdBy = "u1",
        )
        val wire = toWire(SyncTable.STOCK_MOVEMENT, movement)
        assertFalse("createdBy" in wire)
        assertFalse("deviceId" in wire)
        assertEquals(JsonPrimitive(-2000), wire["quantity"])
        assertEquals(JsonPrimitive("consumption"), wire["reason"])
        // Pulled with the author the server set
        val pulled: StockMovement = fromWire(SyncTable.STOCK_MOVEMENT, JsonObject(wire + ("createdBy" to JsonPrimitive("u2")) + ("serverSeq" to JsonPrimitive(9))))
        assertEquals("u2", pulled.createdBy)
    }

    @Test
    fun helpersReadTheWireRow() {
        val wire = toWire(SyncTable.ARTICLE, article, imageSha256 = null)
        assertEquals("a1", wire.rowId)
        assertNull(wire.imageSha256)
        assertEquals("c1", wire.column("category_id"))
        assertNull(wire.column("barcode"))
        val changed = wire.overlay(buildJsonObject { put("sku", "BLT-8 (2)") })
        assertEquals(JsonPrimitive("BLT-8 (2)"), changed["sku"])
        assertEquals(wire["name"], changed["name"])
        assertTrue(wire.overlay(null) === wire)
    }

    @Test
    fun tablesAreInDependencyOrder() {
        assertEquals(
            listOf(
                "category", "attribute_definition", "category_attribute", "location", "article", "item",
                "stock_movement", "stock_review", "stock_review_line",
            ),
            SyncTable.entries.map { it.tableName },
        )
        assertEquals(SyncTable.ITEM, SyncTable.of("item"))
        assertNull(SyncTable.of("unknown_table"))
    }
}
