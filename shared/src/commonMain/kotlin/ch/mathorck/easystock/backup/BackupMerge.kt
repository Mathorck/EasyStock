package ch.mathorck.easystock.backup

import ch.mathorck.easystock.data.db.MAX_SERIAL_LENGTH
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.AttributeDataType
import ch.mathorck.easystock.data.model.AttributeDefinition
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.CategoryAttribute
import ch.mathorck.easystock.data.model.Item
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.StockMovement
import ch.mathorck.easystock.data.model.StockReview
import ch.mathorck.easystock.data.model.StockReviewLine

/** Max lengths of the columns that may get a " (2)" suffix (docs/schema.dbml). */
const val MAX_CATEGORY_NAME_LENGTH = 100
const val MAX_SKU_LENGTH = 50

enum class ConflictType {
    /** Another category already has this name: the imported one was renamed. */
    CATEGORY_RENAMED,

    /** Another article already has this SKU: the imported one got a suffix. */
    SKU_RENAMED,

    /** The NFC tag is already on another article or location here: removed from the imported row (one tag = one thing). */
    BARCODE_CLEARED,

    /** Another unit already has this serial number: the imported one got a suffix. */
    SERIAL_RENAMED,

    /** An attribute with the same key exists: both are the same attribute, the newer label/options are kept. */
    ATTRIBUTE_MERGED,

    /** The imported attribute has another key for the same id: the key can't change, the local one is kept. */
    ATTRIBUTE_KEY_KEPT,

    /** The category already has this attribute: the imported link was skipped. */
    CATEGORY_ATTRIBUTE_SKIPPED,

    /** The imported parent would make a location its own ancestor: the local parent is kept. */
    LOCATION_PARENT_KEPT,

    /** A movement with the same id differs: movements are never changed, the local one is kept. */
    MOVEMENT_KEPT,
}

/** One conflict resolved during a merge. [value]: what was imported, [resolved]: what was saved instead. */
data class MergeConflict(
    val type: ConflictType,
    val table: BackupTable,
    val rowId: String,
    val value: String? = null,
    val resolved: String? = null,
)

/**
 * What a merge writes. [inserts] and [updates] hold whole rows (updates never contain movements).
 * [itemLocationFixes]: other local items whose cached location changes (movements are the source of truth).
 * [result]: the database content after the merge.
 */
data class MergePlan(
    val inserts: BackupData,
    val updates: BackupData,
    val itemLocationFixes: Map<String, String?>,
    val conflicts: List<MergeConflict>,
    val result: BackupData,
    /** Photos referenced by inserted or updated articles: copy them from the archive if missing here. */
    val photosNeeded: Set<String>,
    /** Local photos no longer referenced by any article: delete them once the merge is saved. */
    val photosReleased: Set<String>,
) {
    val isEmpty: Boolean get() = inserts.isEmpty && updates.isEmpty && itemLocationFixes.isEmpty()
}

/**
 * Plans the merge of [incoming] into [local] (every row of both, soft-deleted ones included), by id:
 * - a row on one side only is added;
 * - a row on both sides: the newer `updated_at` wins (a soft delete is an update), the local one on a tie;
 * - `stock_movement` is append-only: missing ids are added, existing ones never changed;
 * - unique columns are resolved deterministically (values of replaced local rows released first, then rows
 *   sorted by id) and reported in [MergePlan.conflicts];
 * - `item.location_id` is recomputed from the merged movements;
 * - stock reviews and their lines merge like the other rows (newer wins). A finished review never adds
 *   movements here: its corrections are movements already, merged with the others.
 * [incoming] must be valid ([validateBackup]). Pure: nothing is written.
 */
fun planMerge(local: BackupData, incoming: BackupData): MergePlan = Merger(local, incoming).plan()

private class Merger(private val local: BackupData, private val incoming: BackupData) {
    private val conflicts = ArrayList<MergeConflict>()

    private class Table<T>(rows: List<T>, val id: (T) -> String) {
        val local: Map<String, T> = rows.associateBy(id)
        val final = LinkedHashMap<String, T>().apply { rows.forEach { put(id(it), it) } }
        val inserted = LinkedHashMap<String, T>()
        val updated = LinkedHashMap<String, T>()

        fun insert(row: T) {
            inserted[id(row)] = row
            final[id(row)] = row
        }

        /** A row back to its local content is no update. */
        fun update(row: T) {
            if (row == local[id(row)]) updated.remove(id(row)) else updated[id(row)] = row
            final[id(row)] = row
        }
    }

    /**
     * Owner ids of each value of a unique column, in the merged state. Values are compared after [normalize] (names,
     * SKUs and serial numbers are unique ignoring case, like the forms and the server push). Several owners per value:
     * rows that differ only in case can already exist (the database indexes are case-sensitive).
     */
    private class UniqueIndex<T>(rows: Collection<T>, private val id: (T) -> String, private val value: (T) -> String?, private val normalize: (String) -> String) {
        private val owners = HashMap<String, MutableSet<String>>()
        private val nextSuffix = HashMap<String, Int>()

        init {
            rows.forEach(::claim)
        }

        /** Owner of [candidate] other than [selfId] (lowest id when several), or null when free. */
        fun takenBy(candidate: String, selfId: String): String? = owners[normalize(candidate)]?.filter { it != selfId }?.minOrNull()

        fun isFree(candidate: String, selfId: String): Boolean = takenBy(candidate, selfId) == null

        fun claim(row: T) {
            value(row)?.let { owners.getOrPut(normalize(it)) { HashSet() }.add(id(row)) }
        }

        fun release(row: T) {
            val key = value(row)?.let(normalize) ?: return
            val ids = owners[key] ?: return
            ids.remove(id(row))
            if (ids.isEmpty()) owners.remove(key)
        }

        /** Moves the row from its previous value to the new one. */
        fun replace(previous: T?, next: T) {
            previous?.let(::release)
            claim(next)
        }

        /**
         * "value (2)", "value (3)"... the first free one, cut to [maxLength]. The next suffix to try is remembered per
         * value: once replaced rows are released up front, claiming only takes values, never frees one, so a suffix
         * found taken stays taken.
         */
        fun suffixed(value: String, selfId: String, maxLength: Int): String {
            val key = normalize(value)
            var n = nextSuffix[key] ?: 2
            while (true) {
                val suffix = " ($n)"
                val candidate = value.take(maxLength - suffix.length).trimEnd() + suffix
                if (isFree(candidate, selfId)) {
                    nextSuffix[key] = n + 1
                    return candidate
                }
                n++
            }
        }
    }

    /**
     * One tag = one thing, across articles and locations: owner key ("article:id", "location:id") of each
     * NFC tag in the merged state. Tags of local rows that an incoming row replaces are released up front,
     * so a tag moved from an article to a location on the other device follows it.
     */
    private val tagOwners = HashMap<String, String>().apply {
        local.locations.forEach { row -> row.barcode?.let { put(it, locationKey(row.id)) } }
        local.articles.forEach { row -> row.barcode?.let { put(it, articleKey(row.id)) } }
        val localLocations = local.locations.associateBy { it.id }
        incoming.locations.forEach { row ->
            val previous = localLocations[row.id] ?: return@forEach
            if (newer(row, previous, Location::updatedAt)) previous.barcode?.let { if (get(it) == locationKey(row.id)) remove(it) }
        }
        val localArticles = local.articles.associateBy { it.id }
        incoming.articles.forEach { row ->
            val previous = localArticles[row.id] ?: return@forEach
            if (newer(row, previous, Article::updatedAt)) previous.barcode?.let { if (get(it) == articleKey(row.id)) remove(it) }
        }
    }

    private fun locationKey(id: String) = "location:$id"
    private fun articleKey(id: String) = "article:$id"

    /** Takes [tag] for [key]; false (nothing changed) when another row holds it. */
    private fun claimTag(tag: String, key: String): Boolean {
        val owner = tagOwners[tag]
        if (owner != null && owner != key) return false
        tagOwners[tag] = key
        return true
    }

    /**
     * Releases the unique values of local rows that a newer incoming row replaces, before any is claimed (like
     * [tagOwners]): rows swapping values ("A: X, B: Y" to "A: Y, B: X") don't collide.
     */
    private fun <T> releaseReplaced(incomingRows: List<T>, table: Table<T>, index: UniqueIndex<T>, updatedAt: (T) -> Long) {
        for (row in incomingRows) {
            val previous = table.local[table.id(row)] ?: continue
            if (newer(row, previous, updatedAt)) index.release(previous)
        }
    }

    /** Ids on both sides first (they may free unique values), then new ids, each sorted by id. */
    private fun <T> ordered(rows: List<T>, table: Table<T>): List<T> {
        val (existing, added) = rows.sortedBy(table.id).partition { table.id(it) in table.local }
        return existing + added
    }

    private fun <T> newer(incoming: T, local: T?, updatedAt: (T) -> Long): Boolean = local == null || updatedAt(incoming) > updatedAt(local)

    fun plan(): MergePlan {
        val categories = mergeCategories()
        val (attributes, attributeIds) = mergeAttributes()
        val categoryAttributes = mergeCategoryAttributes(attributeIds)
        val locations = mergeLocations()
        val articles = mergeArticles()
        val items = mergeItems()
        val movements = mergeMovements()
        val reviews = mergeRows(local.reviews, incoming.reviews, StockReview::id, StockReview::updatedAt)
        val reviewLines = mergeRows(local.reviewLines, incoming.reviewLines, StockReviewLine::id, StockReviewLine::updatedAt)

        // Cached item location, from the merged movements
        val fixes = LinkedHashMap<String, String?>()
        itemLocations(items.final.values, movements.final.values).forEach { (id, location) ->
            val item = items.final.getValue(id)
            if (item.locationId == location) return@forEach
            val fixed = item.copy(locationId = location)
            when (id) {
                in items.inserted -> items.insert(fixed)
                in items.updated -> items.update(fixed)
                else -> {
                    fixes[id] = location
                    items.final[id] = fixed
                }
            }
        }

        val result = BackupData(
            categories = categories.final.values.toList(),
            attributeDefinitions = attributes.final.values.toList(),
            categoryAttributes = categoryAttributes.final.values.toList(),
            locations = locations.final.values.toList(),
            articles = articles.final.values.toList(),
            items = items.final.values.toList(),
            movements = movements.final.values.toList(),
            reviews = reviews.final.values.toList(),
            reviewLines = reviewLines.final.values.toList(),
        )
        // Defensive: the plan must satisfy every database constraint before anything is written
        validateBackup(result)

        val referenced = result.imagePaths()
        val changedArticles = articles.inserted.values + articles.updated.values
        val released = articles.updated.keys.mapNotNull { articles.local[it]?.imagePath }.filter { it !in referenced }.toSet()

        return MergePlan(
            inserts = BackupData(
                categories = categories.inserted.values.toList(),
                attributeDefinitions = attributes.inserted.values.toList(),
                categoryAttributes = categoryAttributes.inserted.values.toList(),
                locations = parentsFirst(locations.inserted.values.toList()),
                articles = articles.inserted.values.toList(),
                items = items.inserted.values.toList(),
                movements = movements.inserted.values.toList(),
                reviews = reviews.inserted.values.toList(),
                reviewLines = reviewLines.inserted.values.toList(),
            ),
            updates = BackupData(
                categories = categories.updated.values.toList(),
                attributeDefinitions = attributes.updated.values.toList(),
                categoryAttributes = categoryAttributes.updated.values.toList(),
                locations = locations.updated.values.toList(),
                articles = articles.updated.values.toList(),
                items = items.updated.values.toList(),
                reviews = reviews.updated.values.toList(),
                reviewLines = reviewLines.updated.values.toList(),
            ),
            itemLocationFixes = fixes,
            conflicts = conflicts.toList(),
            result = result,
            photosNeeded = changedArticles.mapNotNull { it.imagePath }.toSet(),
            photosReleased = released,
        )
    }

    private fun mergeCategories(): Table<Category> {
        val table = Table(local.categories) { it.id }
        val names = UniqueIndex(table.final.values, { it.id }, { it.name }, String::lowercase)
        releaseReplaced(incoming.categories, table, names, Category::updatedAt)
        for (row in ordered(incoming.categories, table)) {
            val previous = table.final[row.id]
            if (!newer(row, previous, Category::updatedAt)) continue
            var candidate = row
            if (!names.isFree(row.name, row.id)) {
                candidate = row.copy(name = names.suffixed(row.name, row.id, MAX_CATEGORY_NAME_LENGTH))
                conflicts += MergeConflict(ConflictType.CATEGORY_RENAMED, BackupTable.CATEGORY, row.id, row.name, candidate.name)
            }
            names.replace(previous, candidate)
            if (previous == null) table.insert(candidate) else table.update(candidate)
        }
        return table
    }

    /** Returns the table and the incoming attribute ids mapped to a local attribute with the same key. */
    private fun mergeAttributes(): Pair<Table<AttributeDefinition>, Map<String, String>> {
        val table = Table(local.attributeDefinitions) { it.id }
        val keys = UniqueIndex(table.final.values, { it.id }, { it.key }) { it }
        val remap = HashMap<String, String>()
        for (row in ordered(incoming.attributeDefinitions, table)) {
            val previous = table.final[row.id]
            if (previous != null) {
                // Same id: the key is immutable
                if (row.key != previous.key) {
                    conflicts += MergeConflict(ConflictType.ATTRIBUTE_KEY_KEPT, BackupTable.ATTRIBUTE_DEFINITION, row.id, row.key, previous.key)
                }
                if (newer(row, previous, AttributeDefinition::updatedAt)) {
                    table.update(keepMeasure(row.copy(key = previous.key, createdAt = previous.createdAt), previous))
                } else if (keepMeasure(previous, row) != previous) {
                    table.update(keepMeasure(previous, row))
                }
                continue
            }
            val owner = keys.takenBy(row.key, row.id)
            if (owner == null) {
                keys.claim(row)
                table.insert(row)
                continue
            }
            // Same key, other id: the same attribute created on both phones. Values in `attributes` JSON
            // are keyed by the key, so they stay valid. Links to it use the local id.
            remap[row.id] = owner
            conflicts += MergeConflict(ConflictType.ATTRIBUTE_MERGED, BackupTable.ATTRIBUTE_DEFINITION, row.id, row.key, row.key)
            val existing = table.final.getValue(owner)
            val merged = if (row.updatedAt > existing.updatedAt) {
                keepMeasure(row.copy(id = existing.id, key = existing.key, createdAt = existing.createdAt), existing)
            } else {
                keepMeasure(existing, row)
            }
            if (merged != existing) {
                if (owner in table.inserted) table.insert(merged) else table.update(merged)
            }
        }
        return table to remap
    }

    /**
     * [winner] of two versions of one attribute, still a measure when [other] is one and [winner] a number:
     * number -> measure is one way (measure values don't read as numbers), plain numbers read in the default unit.
     */
    private fun keepMeasure(winner: AttributeDefinition, other: AttributeDefinition): AttributeDefinition =
        if (winner.dataType == AttributeDataType.NUMBER && other.dataType == AttributeDataType.MEASURE) {
            winner.copy(dataType = AttributeDataType.MEASURE, unit = other.unit, updatedAt = maxOf(winner.updatedAt, other.updatedAt))
        } else {
            winner
        }

    /**
     * A link whose new pair is taken is skipped and keeps its local pair. Pairs of replaced links are released up
     * front (swaps don't collide); when a skipped link's local pair was meanwhile taken by another, the merge is
     * redone with that link's pair held from the start.
     */
    private fun mergeCategoryAttributes(attributeIds: Map<String, String>): Table<CategoryAttribute> {
        fun pair(row: CategoryAttribute) = "${row.categoryId}/${row.attributeId}"
        val rows = incoming.categoryAttributes.map { row -> attributeIds[row.attributeId]?.let { row.copy(attributeId = it) } ?: row }
        val held = HashSet<String>()
        while (true) {
            val table = Table(local.categoryAttributes) { it.id }
            val pairs = UniqueIndex(table.final.values, { it.id }, ::pair) { it }
            releaseReplaced(rows.filter { it.id !in held }, table, pairs, CategoryAttribute::updatedAt)
            val skipped = ArrayList<MergeConflict>()
            var redo = false
            for (row in ordered(rows, table)) {
                val previous = table.final[row.id]
                if (!newer(row, previous, CategoryAttribute::updatedAt)) continue
                if (!pairs.isFree(pair(row), row.id)) {
                    skipped += MergeConflict(ConflictType.CATEGORY_ATTRIBUTE_SKIPPED, BackupTable.CATEGORY_ATTRIBUTE, row.id)
                    if (previous != null && row.id !in held) {
                        if (!pairs.isFree(pair(previous), row.id)) {
                            held += row.id
                            redo = true
                            break
                        }
                        pairs.claim(previous)
                    }
                    continue
                }
                pairs.replace(previous, row)
                if (previous == null) table.insert(row) else table.update(row)
            }
            if (redo) continue
            conflicts += skipped
            return table
        }
    }

    private fun mergeLocations(): Table<Location> {
        val table = Table(local.locations) { it.id }
        for (row in ordered(incoming.locations, table)) {
            val previous = table.final[row.id]
            if (!newer(row, previous, Location::updatedAt)) continue
            var candidate = row
            row.barcode?.let { barcode ->
                if (!claimTag(barcode, locationKey(row.id))) {
                    candidate = candidate.copy(barcode = null)
                    conflicts += MergeConflict(ConflictType.BARCODE_CLEARED, BackupTable.LOCATION, row.id, barcode, row.name)
                }
            }
            if (previous == null) table.insert(candidate) else table.update(candidate)
        }
        // A newer parent on each side can make a cycle: keep the local parent of one updated row until none is left
        while (true) {
            val inCycle = locationCycle(table.final.values) ?: break
            val cycle = generateSequence(inCycle) { table.final[it]?.parentId }.take(table.final.size + 1)
                .takeWhileDistinct().toList()
            val revert = cycle.filter { it in table.updated }.minOrNull()
                ?: throw BackupException(BackupError.DAMAGED, "Location cycle")
            val kept = table.final.getValue(revert).copy(parentId = table.local.getValue(revert).parentId)
            conflicts += MergeConflict(ConflictType.LOCATION_PARENT_KEPT, BackupTable.LOCATION, revert, table.final.getValue(revert).parentId, kept.parentId)
            table.update(kept)
        }
        return table
    }

    private fun mergeArticles(): Table<Article> {
        val table = Table(local.articles) { it.id }
        val skus = UniqueIndex(table.final.values, { it.id }, { it.sku }, String::lowercase)
        releaseReplaced(incoming.articles, table, skus, Article::updatedAt)
        for (row in ordered(incoming.articles, table)) {
            val previous = table.final[row.id]
            if (!newer(row, previous, Article::updatedAt)) continue
            var candidate = row
            row.sku?.let { sku ->
                if (!skus.isFree(sku, row.id)) {
                    candidate = candidate.copy(sku = skus.suffixed(sku, row.id, MAX_SKU_LENGTH))
                    conflicts += MergeConflict(ConflictType.SKU_RENAMED, BackupTable.ARTICLE, row.id, sku, candidate.sku)
                }
            }
            row.barcode?.let { barcode ->
                if (!claimTag(barcode, articleKey(row.id))) {
                    candidate = candidate.copy(barcode = null)
                    conflicts += MergeConflict(ConflictType.BARCODE_CLEARED, BackupTable.ARTICLE, row.id, barcode, row.name)
                }
            }
            skus.replace(previous, candidate)
            if (previous == null) table.insert(candidate) else table.update(candidate)
        }
        return table
    }

    private fun mergeItems(): Table<Item> {
        val table = Table(local.items) { it.id }
        val serials = UniqueIndex(table.final.values, { it.id }, { it.serialNumber }, String::lowercase)
        releaseReplaced(incoming.items, table, serials, Item::updatedAt)
        for (row in ordered(incoming.items, table)) {
            val previous = table.final[row.id]
            if (!newer(row, previous, Item::updatedAt)) continue
            var candidate = row
            row.serialNumber?.let { serial ->
                if (!serials.isFree(serial, row.id)) {
                    candidate = row.copy(serialNumber = serials.suffixed(serial, row.id, MAX_SERIAL_LENGTH))
                    conflicts += MergeConflict(ConflictType.SERIAL_RENAMED, BackupTable.ITEM, row.id, serial, candidate.serialNumber)
                }
            }
            serials.replace(previous, candidate)
            if (previous == null) table.insert(candidate) else table.update(candidate)
        }
        return table
    }

    /** Rows without unique columns: added when new, replaced when the incoming one is newer. */
    private fun <T> mergeRows(localRows: List<T>, incomingRows: List<T>, id: (T) -> String, updatedAt: (T) -> Long): Table<T> {
        val table = Table(localRows, id)
        for (row in incomingRows.sortedBy(id)) {
            val previous = table.final[id(row)]
            if (!newer(row, previous, updatedAt)) continue
            if (previous == null) table.insert(row) else table.update(row)
        }
        return table
    }

    /** Append-only: never updated, never deleted. */
    private fun mergeMovements(): Table<StockMovement> {
        val table = Table(local.movements) { it.id }
        for (row in incoming.movements.sortedBy { it.id }) {
            val existing = table.local[row.id]
            if (existing == null) {
                table.insert(row)
            } else if (existing != row) {
                conflicts += MergeConflict(ConflictType.MOVEMENT_KEPT, BackupTable.STOCK_MOVEMENT, row.id)
            }
        }
        return table
    }
}

private fun <T> Sequence<T>.takeWhileDistinct(): Sequence<T> {
    val seen = HashSet<T>()
    return takeWhile { seen.add(it) }
}
