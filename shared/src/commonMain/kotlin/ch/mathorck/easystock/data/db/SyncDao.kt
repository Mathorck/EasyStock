package ch.mathorck.easystock.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import ch.mathorck.easystock.backup.MAX_CATEGORY_NAME_LENGTH
import ch.mathorck.easystock.backup.MAX_SKU_LENGTH
import ch.mathorck.easystock.backup.parentsFirst
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.AttributeDefinition
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.CategoryAttribute
import ch.mathorck.easystock.data.model.ImageHash
import ch.mathorck.easystock.data.model.Item
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.LowStockAlert
import ch.mathorck.easystock.data.model.OutboxEntry
import ch.mathorck.easystock.data.model.PendingPush
import ch.mathorck.easystock.data.model.RejectedChange
import ch.mathorck.easystock.data.model.RejectedChangeRow
import ch.mathorck.easystock.data.model.ServerUser
import ch.mathorck.easystock.data.model.StockMovement
import ch.mathorck.easystock.data.model.StockReview
import ch.mathorck.easystock.data.model.StockReviewLine
import ch.mathorck.easystock.data.model.SyncState
import ch.mathorck.easystock.images.newImagePath
import ch.mathorck.easystock.sync.KeepMineBlock
import ch.mathorck.easystock.sync.MovementKept
import ch.mathorck.easystock.sync.MovementKeptDetails
import ch.mathorck.easystock.sync.PullBuffer
import ch.mathorck.easystock.sync.PushAnswer
import ch.mathorck.easystock.sync.PushDecision
import ch.mathorck.easystock.sync.PushRequest
import ch.mathorck.easystock.sync.PushStatus
import ch.mathorck.easystock.sync.SyncTable
import ch.mathorck.easystock.sync.decidePush
import ch.mathorck.easystock.sync.column
import ch.mathorck.easystock.sync.fromWire
import ch.mathorck.easystock.sync.keepMineBlock
import ch.mathorck.easystock.sync.keepMineMovement
import ch.mathorck.easystock.sync.imageSha256
import ch.mathorck.easystock.sync.rowId
import ch.mathorck.easystock.sync.suffixedValue
import ch.mathorck.easystock.sync.toWire
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/**
 * The database side of the sync engine (sync/SyncEngine): reads the outbox and the rows to push,
 * writes the server's rows. Server rows are written with `sync_state.applying` on, so the outbox
 * triggers skip them; local rows changed to make room for them (a name or a tag taken) are written
 * with it off, so the change is pushed. Every write method checks `sync_state` first: a phone that
 * left the server meanwhile gets nothing written.
 */
@Dao
@OptIn(ExperimentalUuidApi::class)
abstract class SyncDao {
    // ---------- Session ----------

    @Query("SELECT * FROM sync_state LIMIT 1")
    abstract suspend fun syncState(): SyncState?

    @Query("UPDATE sync_state SET role = :role")
    abstract suspend fun updateRole(role: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertServerUser(user: ServerUser)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertServerUsers(users: List<ServerUser>)

    @Query("UPDATE sync_state SET applying = :applying")
    protected abstract suspend fun setApplying(applying: Boolean)

    @Query("UPDATE sync_state SET since = :since, last_sync_at = :now")
    protected abstract suspend fun setSince(since: Long, now: Long)

    @Transaction
    open suspend fun upsertUser(user: ServerUser) {
        if (syncState() != null) insertServerUser(user)
    }

    // ---------- Pending push ----------

    @Query("SELECT * FROM pending_push LIMIT 1")
    abstract suspend fun pendingPush(): PendingPush?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertPendingPush(push: PendingPush)

    @Query("DELETE FROM pending_push")
    abstract suspend fun clearPendingPush()

    @Transaction
    open suspend fun savePendingPush(push: PendingPush) {
        if (syncState() != null) insertPendingPush(push)
    }

    // ---------- Outbox ----------

    /** Oldest first; the table order of a batch is set by the engine. */
    @Query("SELECT * FROM outbox ORDER BY changed_at, table_name, row_id LIMIT :limit")
    abstract suspend fun outbox(limit: Int): List<OutboxEntry>

    /** Emits at each outbox change: the debounced sync trigger. */
    @Query("SELECT COUNT(*) FROM outbox")
    abstract fun observeOutboxCount(): Flow<Int>

    @Query("SELECT row_id FROM outbox WHERE table_name = :table")
    protected abstract suspend fun outboxIds(table: String): List<String>

    @Query("DELETE FROM outbox WHERE table_name = :table AND row_id = :id")
    protected abstract suspend fun deleteOutbox(table: String, id: String)

    // ---------- Rejected changes ----------

    @Insert
    protected abstract suspend fun insertRejectedChange(change: RejectedChange)

    @Query("SELECT * FROM rejected_change ORDER BY rejected_at DESC")
    abstract fun observeRejectedChanges(): Flow<List<RejectedChange>>

    /** The rejected changes, newest first, with the name of the local row (null when it is gone). */
    @Query(
        """
        SELECT r.*, CASE r.table_name
            WHEN 'category' THEN (SELECT name FROM category WHERE id = r.row_id)
            WHEN 'attribute_definition' THEN (SELECT label FROM attribute_definition WHERE id = r.row_id)
            WHEN 'category_attribute' THEN (SELECT c.name || ' · ' || d.label FROM category_attribute ca
                JOIN category c ON c.id = ca.category_id JOIN attribute_definition d ON d.id = ca.attribute_id
                WHERE ca.id = r.row_id)
            WHEN 'location' THEN (SELECT name FROM location WHERE id = r.row_id)
            WHEN 'article' THEN (SELECT name FROM article WHERE id = r.row_id)
            WHEN 'item' THEN (SELECT a.name FROM item i JOIN article a ON a.id = i.article_id WHERE i.id = r.row_id)
            WHEN 'stock_movement' THEN (SELECT a.name FROM stock_movement m JOIN article a ON a.id = m.article_id
                WHERE m.id = r.row_id)
            WHEN 'stock_review' THEN (SELECT l.name FROM stock_review s JOIN location l ON l.id = s.location_id
                WHERE s.id = r.row_id)
            WHEN 'stock_review_line' THEN (SELECT a.name FROM stock_review_line sl JOIN article a ON a.id = sl.article_id
                WHERE sl.id = r.row_id)
        END AS label
        FROM rejected_change r ORDER BY r.rejected_at DESC
        """,
    )
    abstract fun observeRejectedChangeRows(): Flow<List<RejectedChangeRow>>

    @Query("SELECT COUNT(*) FROM rejected_change WHERE seen_at IS NULL")
    abstract fun observeUnseenRejectedCount(): Flow<Int>

    @Query("UPDATE rejected_change SET seen_at = :now WHERE seen_at IS NULL")
    abstract suspend fun markRejectedSeen(now: Long)

    /** App-only table, never synced: dismissing an entry deletes it. */
    @Query("DELETE FROM rejected_change WHERE id = :id")
    abstract suspend fun dismissRejectedChange(id: String)

    // ---------- Photos ----------

    @Query("SELECT sha256 FROM image_hash WHERE image_path = :path")
    abstract suspend fun hashOf(path: String): String?

    @Query("SELECT image_path FROM image_hash WHERE sha256 = :sha256")
    abstract suspend fun pathsOf(sha256: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertImageHash(hash: ImageHash)

    @Transaction
    open suspend fun saveImageHash(hash: ImageHash) {
        if (syncState() != null) insertImageHash(hash)
    }

    @Query("SELECT * FROM image_hash")
    abstract suspend fun allImageHashes(): List<ImageHash>

    /** Every photo path an article row holds, soft-deleted rows included. */
    @Query("SELECT image_path FROM article WHERE image_path IS NOT NULL")
    abstract suspend fun referencedImagePaths(): List<String>

    @Query("DELETE FROM image_hash WHERE image_path = :path")
    abstract suspend fun deleteImageHash(path: String)

    /** Photos of the articles still shown (not deleted), with their hash. The store keeps those without a file. */
    @Query(
        """SELECT h.* FROM image_hash h JOIN article a ON a.image_path = h.image_path
        WHERE a.deleted_at IS NULL ORDER BY a.updated_at DESC""",
    )
    abstract suspend fun articleImages(): List<ImageHash>

    // ---------- Rows by id ----------

    @Query("SELECT * FROM category WHERE id = :id") protected abstract suspend fun category(id: String): Category?
    @Query("SELECT * FROM attribute_definition WHERE id = :id") protected abstract suspend fun attribute(id: String): AttributeDefinition?
    @Query("SELECT * FROM category_attribute WHERE id = :id") protected abstract suspend fun categoryAttribute(id: String): CategoryAttribute?
    @Query("SELECT * FROM location WHERE id = :id") protected abstract suspend fun location(id: String): Location?
    @Query("SELECT * FROM article WHERE id = :id") abstract suspend fun article(id: String): Article?
    @Query("SELECT * FROM item WHERE id = :id") protected abstract suspend fun item(id: String): Item?
    @Query("SELECT * FROM stock_movement WHERE id = :id") protected abstract suspend fun movement(id: String): StockMovement?
    @Query("SELECT * FROM stock_review WHERE id = :id") protected abstract suspend fun review(id: String): StockReview?
    @Query("SELECT * FROM stock_review_line WHERE id = :id") protected abstract suspend fun reviewLine(id: String): StockReviewLine?

    @Query("SELECT id FROM category WHERE id IN (:ids)") protected abstract suspend fun categoryIds(ids: List<String>): List<String>
    @Query("SELECT id FROM attribute_definition WHERE id IN (:ids)") protected abstract suspend fun attributeIds(ids: List<String>): List<String>
    @Query("SELECT id FROM category_attribute WHERE id IN (:ids)") protected abstract suspend fun categoryAttributeIds(ids: List<String>): List<String>
    @Query("SELECT id FROM location WHERE id IN (:ids)") protected abstract suspend fun locationIds(ids: List<String>): List<String>
    @Query("SELECT id FROM article WHERE id IN (:ids)") protected abstract suspend fun articleIds(ids: List<String>): List<String>
    @Query("SELECT id FROM item WHERE id IN (:ids)") protected abstract suspend fun itemIds(ids: List<String>): List<String>
    @Query("SELECT id FROM stock_movement WHERE id IN (:ids)") protected abstract suspend fun movementIds(ids: List<String>): List<String>
    @Query("SELECT id FROM stock_review WHERE id IN (:ids)") protected abstract suspend fun reviewIds(ids: List<String>): List<String>
    @Query("SELECT id FROM stock_review_line WHERE id IN (:ids)") protected abstract suspend fun reviewLineIds(ids: List<String>): List<String>

    /** Which of [ids] exist in [table], soft-deleted rows included. */
    open suspend fun existingIds(table: SyncTable, ids: Set<String>): Set<String> =
        ids.chunked(IN_CHUNK).flatMap { chunk ->
            when (table) {
                SyncTable.CATEGORY -> categoryIds(chunk)
                SyncTable.ATTRIBUTE_DEFINITION -> attributeIds(chunk)
                SyncTable.CATEGORY_ATTRIBUTE -> categoryAttributeIds(chunk)
                SyncTable.LOCATION -> locationIds(chunk)
                SyncTable.ARTICLE -> articleIds(chunk)
                SyncTable.ITEM -> itemIds(chunk)
                SyncTable.STOCK_MOVEMENT -> movementIds(chunk)
                SyncTable.STOCK_REVIEW -> reviewIds(chunk)
                SyncTable.STOCK_REVIEW_LINE -> reviewLineIds(chunk)
            }
        }.toSet()

    /** The row as a wire row, its photo given by the recorded hash (null when none is recorded). */
    open suspend fun wireRow(table: SyncTable, id: String): JsonObject? = when (table) {
        SyncTable.CATEGORY -> category(id)?.let { toWire(table, it) }
        SyncTable.ATTRIBUTE_DEFINITION -> attribute(id)?.let { toWire(table, it) }
        SyncTable.CATEGORY_ATTRIBUTE -> categoryAttribute(id)?.let { toWire(table, it) }
        SyncTable.LOCATION -> location(id)?.let { toWire(table, it) }
        SyncTable.ARTICLE -> article(id)?.let { toWire(table, it, it.imagePath?.let { path -> hashOf(path) }) }
        SyncTable.ITEM -> item(id)?.let { toWire(table, it) }
        SyncTable.STOCK_MOVEMENT -> movement(id)?.let { toWire(table, it) }
        SyncTable.STOCK_REVIEW -> review(id)?.let { toWire(table, it) }
        SyncTable.STOCK_REVIEW_LINE -> reviewLine(id)?.let { toWire(table, it) }
    }

    // ---------- Writes of server rows ----------

    @Upsert protected abstract suspend fun upsertCategory(row: Category)
    @Upsert protected abstract suspend fun upsertAttribute(row: AttributeDefinition)
    @Upsert protected abstract suspend fun upsertCategoryAttribute(row: CategoryAttribute)
    @Upsert protected abstract suspend fun upsertLocation(row: Location)
    @Upsert protected abstract suspend fun upsertArticle(row: Article)
    @Upsert protected abstract suspend fun upsertItem(row: Item)
    @Upsert protected abstract suspend fun upsertMovement(row: StockMovement)
    @Upsert protected abstract suspend fun upsertReview(row: StockReview)
    @Upsert protected abstract suspend fun upsertReviewLine(row: StockReviewLine)

    /** Append-only: a movement already on the phone is never changed. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertMovement(row: StockMovement)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertAlerts(alerts: List<LowStockAlert>)

    // Unique values held by another row
    @Query("SELECT id FROM category WHERE name = :name AND id <> :id") protected abstract suspend fun categoryWithName(name: String, id: String): String?
    @Query("SELECT COUNT(*) > 0 FROM category WHERE name = :name") protected abstract suspend fun categoryNameTaken(name: String): Boolean
    @Query("UPDATE category SET name = :name, updated_at = :now WHERE id = :id") protected abstract suspend fun renameCategory(id: String, name: String, now: Long)

    @Query("SELECT id FROM attribute_definition WHERE `key` = :key AND id <> :id") protected abstract suspend fun attributeWithKey(key: String, id: String): String?
    @Query("UPDATE attribute_definition SET `key` = :key WHERE id = :id") protected abstract suspend fun setAttributeKey(id: String, key: String)
    @Query("DELETE FROM attribute_definition WHERE id = :id") protected abstract suspend fun hardDeleteAttribute(id: String)

    @Query("SELECT * FROM category_attribute WHERE attribute_id = :attributeId") protected abstract suspend fun linksOfAttribute(attributeId: String): List<CategoryAttribute>
    @Query("SELECT id FROM category_attribute WHERE category_id = :categoryId AND attribute_id = :attributeId AND id <> :id")
    protected abstract suspend fun linkWithPair(categoryId: String, attributeId: String, id: String): String?
    @Query("UPDATE category_attribute SET attribute_id = :attributeId, updated_at = :now WHERE id = :id")
    protected abstract suspend fun setLinkAttribute(id: String, attributeId: String, now: Long)
    @Query("DELETE FROM category_attribute WHERE id = :id") protected abstract suspend fun hardDeleteLink(id: String)

    @Query("SELECT id FROM location WHERE barcode = :barcode AND id <> :id") protected abstract suspend fun locationWithTag(barcode: String, id: String): String?
    @Query("UPDATE location SET barcode = NULL, updated_at = :now WHERE id = :id") protected abstract suspend fun clearLocationTag(id: String, now: Long)

    @Query("SELECT id FROM article WHERE sku = :sku AND id <> :id") protected abstract suspend fun articleWithSku(sku: String, id: String): String?
    @Query("SELECT COUNT(*) > 0 FROM article WHERE sku = :sku") protected abstract suspend fun skuTaken(sku: String): Boolean
    @Query("UPDATE article SET sku = :sku, updated_at = :now WHERE id = :id") protected abstract suspend fun setSku(id: String, sku: String, now: Long)
    @Query("SELECT id FROM article WHERE barcode = :barcode AND id <> :id") protected abstract suspend fun articleWithTag(barcode: String, id: String): String?
    @Query("UPDATE article SET barcode = NULL, updated_at = :now WHERE id = :id") protected abstract suspend fun clearArticleTag(id: String, now: Long)

    @Query("SELECT id FROM item WHERE serial_number = :serial AND id <> :id") protected abstract suspend fun itemWithSerial(serial: String, id: String): String?
    @Query("SELECT COUNT(*) > 0 FROM item WHERE serial_number = :serial") protected abstract suspend fun serialTaken(serial: String): Boolean
    @Query("UPDATE item SET serial_number = :serial, updated_at = :now WHERE id = :id") protected abstract suspend fun setSerial(id: String, serial: String, now: Long)

    /** `item.location_id` from the movements: the location where its sum is positive, lowest id first (as backup/itemLocations). */
    @Query(
        """UPDATE item SET location_id = (SELECT location_id FROM stock_movement WHERE item_id = item.id
        GROUP BY location_id HAVING SUM(quantity) > 0 ORDER BY location_id LIMIT 1) WHERE id IN (:ids)""",
    )
    protected abstract suspend fun recomputeItemLocations(ids: List<String>)

    /**
     * Applies the server's [answer] to the push [sent] (see [decidePush]), records the refused rows and
     * forgets the pending push, in one transaction. Empty when the phone left the server.
     */
    @Transaction
    open suspend fun applyPush(sent: PushRequest, answer: PushAnswer, now: Long): List<PushDecision> {
        if (syncState() == null) return emptyList()
        val decisions = answer.results.map { result ->
            val table = SyncTable.of(result.table) ?: return@map PushDecision.Keep
            val sentRow = sent.rows[table.tableName]?.firstOrNull { it.rowId == result.id }
            val decision = decidePush(result, sentRow, wireRow(table, result.id))
            when (decision) {
                PushDecision.Keep -> Unit
                PushDecision.Done, PushDecision.Gone -> deleteOutbox(table.tableName, result.id)
                is PushDecision.Write -> {
                    applying { writeServerRow(table, decision.row, updateMovements = true, now = now) }
                    deleteOutbox(table.tableName, result.id)
                }
                is PushDecision.MergeAttribute -> {
                    applying { mergeAttribute(decision.fromId, decision.row, now) }
                    deleteOutbox(table.tableName, result.id)
                }
                PushDecision.DropLocal -> {
                    hardDeleteLink(result.id)
                    deleteOutbox(table.tableName, result.id)
                }
                is PushDecision.MovementKept -> {
                    applying {
                        writeServerRow(table, decision.server, updateMovements = true, now = now)
                        listOfNotNull(decision.server.column("item_id"), decision.phone.column("item_id")).distinct()
                            .takeIf { it.isNotEmpty() }?.let { recomputeItemLocations(it) }
                    }
                    deleteOutbox(table.tableName, result.id)
                    insertRejectedChange(
                        RejectedChange(
                            id = Uuid.random().toString(),
                            tableName = table.tableName,
                            rowId = result.id,
                            reason = MovementKept.reasonOf(decision.phone),
                            rejectedAt = now,
                        ),
                    )
                }
            }
            if (result.status == PushStatus.REJECTED && decision != PushDecision.Keep) {
                insertRejectedChange(
                    RejectedChange(
                        id = Uuid.random().toString(),
                        tableName = table.tableName,
                        rowId = result.id,
                        reason = result.reason ?: "invalid",
                        rejectedAt = now,
                    ),
                )
            }
            decision
        }
        clearPendingPush()
        return decisions
    }

    /**
     * Writes the [buffer] (rows in table order, then users and alerts), recomputes the location of
     * the touched units, and saves [next] as the pull cursor, in one transaction. A row waiting in
     * the outbox is skipped: the local change wins until it is pushed, and the push answer brings the
     * server's version. [fullPull]: movements already on the phone are updated (re-pull after a
     * promotion), otherwise they are never touched. False when the phone left the server.
     */
    @Transaction
    open suspend fun applyPull(buffer: PullBuffer, next: Long, fullPull: Boolean, now: Long): Boolean {
        if (syncState() == null) return false
        val touchedItems = HashSet<String>()
        SyncTable.entries.forEach { table ->
            val waiting = outboxIds(table.tableName).toSet()
            val rows = buffer.rows(table).filter { it.rowId !in waiting }.let { if (table == SyncTable.LOCATION) locationsParentsFirst(it) else it }
            rows.forEach { row ->
                applying { writeServerRow(table, row, updateMovements = fullPull, now = now) }
                // A local row renamed to make room for an earlier row of this pull: this row is the server's
                deleteOutbox(table.tableName, row.rowId!!)
                when (table) {
                    SyncTable.ITEM -> touchedItems += row.rowId!!
                    SyncTable.STOCK_MOVEMENT -> fromWire<StockMovement>(table, row).itemId?.let { touchedItems += it }
                    else -> Unit
                }
            }
        }
        applying {
            touchedItems.toList().chunked(IN_CHUNK).forEach { recomputeItemLocations(it) }
        }
        if (buffer.users.isNotEmpty()) insertServerUsers(buffer.users.values.map { ServerUser(it.id, it.displayName) })
        if (buffer.alerts.isNotEmpty()) {
            insertAlerts(buffer.alerts.values.map { LowStockAlert(it.id, it.articleId, it.crossedAt, it.stock) })
        }
        setSince(next, now)
        return true
    }

    private fun locationsParentsFirst(rows: List<JsonObject>): List<JsonObject> {
        val byId = rows.associateBy { it.rowId }
        return parentsFirst(rows.map { fromWire<Location>(SyncTable.LOCATION, it) }).mapNotNull { byId[it.id] }
    }

    private suspend inline fun applying(block: () -> Unit) {
        setApplying(true)
        try {
            block()
        } finally {
            setApplying(false)
        }
    }

    /** With `applying` off: a local row changed to make room for a server row is pushed. */
    private suspend inline fun local(block: () -> Unit) {
        setApplying(false)
        try {
            block()
        } finally {
            setApplying(true)
        }
    }

    /** Writes one server row, after freeing the unique values local rows hold. Called with `applying` on. */
    private suspend fun writeServerRow(table: SyncTable, wire: JsonObject, updateMovements: Boolean, now: Long) {
        when (table) {
            SyncTable.CATEGORY -> {
                val row = fromWire<Category>(table, wire)
                categoryWithName(row.name, row.id)?.let { other ->
                    local { renameCategory(other, suffixedValue(row.name, MAX_CATEGORY_NAME_LENGTH) { !categoryNameTaken(it) }, now) }
                }
                upsertCategory(row)
            }
            SyncTable.ATTRIBUTE_DEFINITION -> {
                val row = fromWire<AttributeDefinition>(table, wire)
                val other = attributeWithKey(row.key, row.id)
                if (other != null) mergeAttribute(other, wire, now) else upsertAttribute(row)
            }
            SyncTable.CATEGORY_ATTRIBUTE -> {
                val row = fromWire<CategoryAttribute>(table, wire)
                // The category already has this attribute under another link, never accepted by the server
                linkWithPair(row.categoryId, row.attributeId, row.id)?.let { other ->
                    hardDeleteLink(other)
                    deleteOutbox(table.tableName, other)
                }
                upsertCategoryAttribute(row)
            }
            SyncTable.LOCATION -> {
                val row = fromWire<Location>(table, wire)
                row.barcode?.let { tag -> locationWithTag(tag, row.id)?.let { local { clearLocationTag(it, now) } } }
                upsertLocation(row)
            }
            SyncTable.ARTICLE -> {
                val id = wire.rowId!!
                val row = fromWire<Article>(table, wire, imagePathFor(id, wire.imageSha256))
                row.sku?.let { sku ->
                    articleWithSku(sku, row.id)?.let { other -> local { setSku(other, suffixedValue(sku, MAX_SKU_LENGTH) { !skuTaken(it) }, now) } }
                }
                row.barcode?.let { tag -> articleWithTag(tag, row.id)?.let { local { clearArticleTag(it, now) } } }
                upsertArticle(row)
            }
            SyncTable.ITEM -> {
                val row = fromWire<Item>(table, wire)
                row.serialNumber?.let { serial ->
                    itemWithSerial(serial, row.id)?.let { other ->
                        local { setSerial(other, suffixedValue(serial, MAX_SERIAL_LENGTH) { !serialTaken(it) }, now) }
                    }
                }
                upsertItem(row)
            }
            SyncTable.STOCK_MOVEMENT -> {
                val row = fromWire<StockMovement>(table, wire)
                if (updateMovements) upsertMovement(row) else insertMovement(row)
            }
            SyncTable.STOCK_REVIEW -> upsertReview(fromWire(table, wire))
            SyncTable.STOCK_REVIEW_LINE -> upsertReviewLine(fromWire(table, wire))
        }
    }

    /**
     * The local path of a server photo: the article's current path when it holds that photo already
     * (its own photo pulled back), otherwise a new path recorded with the hash, to download.
     */
    private suspend fun imagePathFor(articleId: String, sha256: String?): String? {
        if (sha256 == null) return null
        val current = article(articleId)?.imagePath
        if (current != null && hashOf(current) == sha256) return current
        val path = newImagePath()
        insertImageHash(ImageHash(path, sha256))
        return path
    }

    /**
     * The server has the key of the local definition [localId] under another id ([serverRow]): writes
     * the server's definition, points the local links to it (with `applying` off, so they are pushed),
     * drops a link the category then has twice, and deletes [localId], which the server never accepted.
     */
    private suspend fun mergeAttribute(localId: String, serverRow: JsonObject, now: Long) {
        val server = fromWire<AttributeDefinition>(SyncTable.ATTRIBUTE_DEFINITION, serverRow)
        // Frees the key first (immutable rule: the local definition is deleted right after)
        setAttributeKey(localId, "\u0000merge:$localId")
        upsertAttribute(server)
        linksOfAttribute(localId).forEach { link ->
            if (linkWithPair(link.categoryId, server.id, link.id) != null) {
                hardDeleteLink(link.id)
                deleteOutbox(SyncTable.CATEGORY_ATTRIBUTE.tableName, link.id)
            } else {
                local { setLinkAttribute(link.id, server.id, now) }
            }
        }
        hardDeleteAttribute(localId)
        deleteOutbox(SyncTable.ATTRIBUTE_DEFINITION.tableName, localId)
    }

    // ---------- Movement kept by the server (sync/MovementKept) ----------

    @Query("SELECT * FROM rejected_change WHERE id = :id")
    protected abstract suspend fun rejectedChange(id: String): RejectedChange?

    @Query("SELECT COUNT(*) > 0 FROM article WHERE id = :id AND deleted_at IS NULL")
    protected abstract suspend fun isArticleActive(id: String): Boolean

    @Query("SELECT COUNT(*) > 0 FROM location WHERE id = :id AND deleted_at IS NULL")
    protected abstract suspend fun isLocationActive(id: String): Boolean

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM stock_movement WHERE article_id = :articleId AND location_id = :locationId")
    protected abstract suspend fun stockAt(articleId: String, locationId: String): Long

    @Insert
    protected abstract suspend fun insertLocalMovement(row: StockMovement)

    /** Both versions of a `movement-kept` change, and why "Keep mine" is not offered. Null for another change. */
    @Transaction
    open suspend fun movementKeptDetails(changeId: String, canStockAction: Boolean): MovementKeptDetails? {
        val change = rejectedChange(changeId) ?: return null
        val phone = MovementKept.phoneMovementOf(change.reason) ?: return null
        val block = keepMineBlock(
            phone,
            canStockAction,
            articleActive = isArticleActive(phone.articleId),
            locationActive = isLocationActive(phone.locationId),
            stockAtLocation = stockAt(phone.articleId, phone.locationId),
        )
        return MovementKeptDetails(phone, movement(change.rowId), block)
    }

    /**
     * "Use the server's version": the local movement already is the server's (written with the
     * answer), so only the choice is forgotten. False when the phone left the server.
     */
    @Transaction
    open suspend fun useServerMovement(changeId: String): Boolean {
        if (syncState() == null) return false
        dismissRejectedChange(changeId)
        return true
    }

    /**
     * "Keep mine": adds the phone's movement again as a new movement [newId] dated [now], with
     * `applying` off so it is pushed, and forgets the choice. The server's movement stays: movements
     * are append-only. Returns null when done, otherwise why not (nothing written then).
     */
    @Transaction
    open suspend fun keepMyMovement(changeId: String, newId: String, canStockAction: Boolean, now: Long): KeepMineBlock? {
        if (syncState() == null) return KeepMineBlock.INVALID
        val details = movementKeptDetails(changeId, canStockAction) ?: return KeepMineBlock.INVALID
        details.keepMineBlock?.let { return it }
        val movement = keepMineMovement(details.phone, newId, now)
        insertLocalMovement(movement)
        movement.itemId?.let { applying { recomputeItemLocations(listOf(it)) } }
        dismissRejectedChange(changeId)
        return null
    }

    companion object {
        /** Ids per `IN` list, below the SQLite variable limit. */
        private const val IN_CHUNK = 500
    }
}
