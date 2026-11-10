package ch.mathorck.easystock.backup

import ch.mathorck.easystock.data.db.MAX_SERIAL_LENGTH
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.AttributeDefinition
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.CategoryAttribute
import ch.mathorck.easystock.data.model.Item
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.StockMovement

/** Max lengths of the columns that may get a " (2)" suffix (docs/schema.dbml). */
const val MAX_CATEGORY_NAME_LENGTH = 100
const val MAX_SKU_LENGTH = 50

enum class ConflictType {
    /** Another category already has this name: the imported one was renamed. */
    CATEGORY_RENAMED,

    /** Another article already has this SKU: the imported one got a suffix. */
    SKU_RENAMED,

    /** The NFC tag is already on another article here: removed from the imported article. */
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
 * - unique columns are resolved deterministically (rows sorted by id) and reported in [MergePlan.conflicts];
 * - `item.location_id` is recomputed from the merged movements.
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

    /** Owner id of each value of a unique column, in the merged state. */
    private class UniqueIndex<T>(rows: Collection<T>, private val id: (T) -> String, private val value: (T) -> String?, private val normalize: (String) -> String) {
        private val owners = HashMap<String, String>().apply { rows.forEach { row -> value(row)?.let { put(normalize(it), id(row)) } } }

        /** Owner of [candidate] other than [selfId], or null when free. */
        fun takenBy(candidate: String, selfId: String): String? = owners[normalize(candidate)]?.takeIf { it != selfId }

        fun isFree(candidate: String, selfId: String): Boolean = takenBy(candidate, selfId) == null

        /** Moves [selfId] from its previous value to the new one. */
        fun replace(previous: T?, next: T) {
            previous?.let(value)?.let { if (owners[normalize(it)] == id(next)) owners.remove(normalize(it)) }
            value(next)?.let { owners[normalize(it)] = id(next) }
        }
    }

    /** "value (2)", "value (3)"... the first free one, cut to [maxLength]. */
    private fun suffixed(value: String, selfId: String, maxLength: Int, index: UniqueIndex<*>): String {
        var n = 2
        while (true) {
            val suffix = " ($n)"
            val candidate = value.take(maxLength - suffix.length).trimEnd() + suffix
            if (index.isFree(candidate, selfId)) return candidate
            n++
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
            ),
            updates = BackupData(
                categories = categories.updated.values.toList(),
                attributeDefinitions = attributes.updated.values.toList(),
                categoryAttributes = categoryAttributes.updated.values.toList(),
                locations = locations.updated.values.toList(),
                articles = articles.updated.values.toList(),
                items = items.updated.values.toList(),
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
        for (row in ordered(incoming.categories, table)) {
            val previous = table.final[row.id]
            if (!newer(row, previous, Category::updatedAt)) continue
            var candidate = row
            if (!names.isFree(row.name, row.id)) {
                candidate = row.copy(name = suffixed(row.name, row.id, MAX_CATEGORY_NAME_LENGTH, names))
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
                if (newer(row, previous, AttributeDefinition::updatedAt)) table.update(row.copy(key = previous.key, createdAt = previous.createdAt))
                continue
            }
            val owner = keys.takenBy(row.key, row.id)
            if (owner == null) {
                keys.replace(null, row)
                table.insert(row)
                continue
            }
            // Same key, other id: the same attribute created on both phones. Values in `attributes` JSON
            // are keyed by the key, so they stay valid. Links to it use the local id.
            remap[row.id] = owner
            conflicts += MergeConflict(ConflictType.ATTRIBUTE_MERGED, BackupTable.ATTRIBUTE_DEFINITION, row.id, row.key, row.key)
            val existing = table.final.getValue(owner)
            if (row.updatedAt > existing.updatedAt) {
                val merged = row.copy(id = existing.id, key = existing.key, createdAt = existing.createdAt)
                if (owner in table.inserted) table.insert(merged) else table.update(merged)
            }
        }
        return table to remap
    }

    private fun mergeCategoryAttributes(attributeIds: Map<String, String>): Table<CategoryAttribute> {
        val table = Table(local.categoryAttributes) { it.id }
        fun pair(row: CategoryAttribute) = "${row.categoryId}/${row.attributeId}"
        val pairs = UniqueIndex(table.final.values, { it.id }, ::pair) { it }
        val rows = incoming.categoryAttributes.map { row -> attributeIds[row.attributeId]?.let { row.copy(attributeId = it) } ?: row }
        for (row in ordered(rows, table)) {
            val previous = table.final[row.id]
            if (!newer(row, previous, CategoryAttribute::updatedAt)) continue
            if (!pairs.isFree(pair(row), row.id)) {
                conflicts += MergeConflict(ConflictType.CATEGORY_ATTRIBUTE_SKIPPED, BackupTable.CATEGORY_ATTRIBUTE, row.id)
                continue
            }
            pairs.replace(previous, row)
            if (previous == null) table.insert(row) else table.update(row)
        }
        return table
    }

    private fun mergeLocations(): Table<Location> {
        val table = Table(local.locations) { it.id }
        for (row in ordered(incoming.locations, table)) {
            val previous = table.final[row.id]
            if (!newer(row, previous, Location::updatedAt)) continue
            if (previous == null) table.insert(row) else table.update(row)
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
        val barcodes = UniqueIndex(table.final.values, { it.id }, { it.barcode }) { it }
        for (row in ordered(incoming.articles, table)) {
            val previous = table.final[row.id]
            if (!newer(row, previous, Article::updatedAt)) continue
            var candidate = row
            row.sku?.let { sku ->
                if (!skus.isFree(sku, row.id)) {
                    candidate = candidate.copy(sku = suffixed(sku, row.id, MAX_SKU_LENGTH, skus))
                    conflicts += MergeConflict(ConflictType.SKU_RENAMED, BackupTable.ARTICLE, row.id, sku, candidate.sku)
                }
            }
            row.barcode?.let { barcode ->
                if (!barcodes.isFree(barcode, row.id)) {
                    candidate = candidate.copy(barcode = null)
                    conflicts += MergeConflict(ConflictType.BARCODE_CLEARED, BackupTable.ARTICLE, row.id, barcode, row.name)
                }
            }
            skus.replace(previous, candidate)
            barcodes.replace(previous, candidate)
            if (previous == null) table.insert(candidate) else table.update(candidate)
        }
        return table
    }

    private fun mergeItems(): Table<Item> {
        val table = Table(local.items) { it.id }
        val serials = UniqueIndex(table.final.values, { it.id }, { it.serialNumber }, String::lowercase)
        for (row in ordered(incoming.items, table)) {
            val previous = table.final[row.id]
            if (!newer(row, previous, Item::updatedAt)) continue
            var candidate = row
            row.serialNumber?.let { serial ->
                if (!serials.isFree(serial, row.id)) {
                    candidate = row.copy(serialNumber = suffixed(serial, row.id, MAX_SERIAL_LENGTH, serials))
                    conflicts += MergeConflict(ConflictType.SERIAL_RENAMED, BackupTable.ITEM, row.id, serial, candidate.serialNumber)
                }
            }
            serials.replace(previous, candidate)
            if (previous == null) table.insert(candidate) else table.update(candidate)
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
