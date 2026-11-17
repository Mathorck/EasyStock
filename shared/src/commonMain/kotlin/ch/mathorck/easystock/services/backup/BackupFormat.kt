package ch.mathorck.easystock.services.backup

import ch.mathorck.easystock.data.dao.article.MAX_FLAG_NOTE_LENGTH
import ch.mathorck.easystock.data.dto.backup.BackupData
import ch.mathorck.easystock.data.model.article.Item
import ch.mathorck.easystock.data.model.location.Location
import ch.mathorck.easystock.data.model.stock.StockMovement
import ch.mathorck.easystock.enums.backup.BackupError
import ch.mathorck.easystock.exceptions.backup.BackupException
import ch.mathorck.easystock.services.images.isValidImagePath
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

// Backup archive (zip):
//   manifest.json         BackupManifest
//   tables/<table>.json   JSON array of rows, columns named as in docs/schema.dbml
//   images/<uuid>.jpg     article photos, same relative path as article.image_path
// A QR code holds a smaller variant without photos: "ESQ1" + deflate(BackupBundle JSON).
// Both can be wrapped in the encrypted envelope (BackupCipher).

/** Value of [BackupManifest.format]. */
const val BACKUP_FORMAT = "easystock-backup"

/** Current archive layout version. Raised when the layout changes in a way older apps can't read. */
const val BACKUP_FORMAT_VERSION = 1

/** Suggested file name extension. */
const val BACKUP_FILE_EXTENSION = "easystock"

const val MANIFEST_ENTRY = "manifest.json"

const val TABLES_DIR = "tables"

/** Upper bounds when reading an untrusted archive. */
const val MAX_MANIFEST_BYTES = 64L * 1024

const val MAX_TABLE_BYTES = 64L * 1024 * 1024

const val MAX_IMAGE_BYTES = 32L * 1024 * 1024

/** All the photos of one archive together, decompressed. */
const val MAX_PHOTOS_TOTAL_BYTES = 2L * 1024 * 1024 * 1024

val backupJson: Json = Json {
    encodeDefaults = true
    explicitNulls = true
    // Tolerates columns dropped in a later schema, older backups still decode
    ignoreUnknownKeys = true
    prettyPrint = false
}

internal fun <T> decodeOrDamaged(serializer: KSerializer<T>, bytes: ByteArray): T = try {
    backupJson.decodeFromString(serializer, bytes.decodeToString(throwOnInvalidSequence = true))
} catch (e: SerializationException) {
    throw BackupException(BackupError.DAMAGED, "Invalid JSON", e)
} catch (e: IllegalArgumentException) {
    throw BackupException(BackupError.DAMAGED, "Invalid JSON", e)
} catch (e: CharacterCodingException) {
    throw BackupException(BackupError.DAMAGED, "Invalid UTF-8", e)
}

/**
 * Checks that a backup is consistent before anything is written: unique ids and unique columns,
 * every reference points to a row of the backup, no location cycle, valid photo paths.
 * Throws [BackupException] ([BackupError.DAMAGED]). The same rules as the database, so an import
 * never fails half-way on a constraint.
 */
fun validateBackup(data: BackupData) {
    fun damaged(what: String): Nothing = throw BackupException(BackupError.DAMAGED, what)
    fun <T> ids(rows: List<T>, id: (T) -> String, table: String): Set<String> {
        val set = HashSet<String>(rows.size * 2)
        rows.forEach { if (!set.add(id(it))) damaged("Duplicate id in $table") }
        return set
    }
    fun <T> unique(rows: List<T>, value: (T) -> String?, what: String) {
        val seen = HashSet<String>()
        rows.forEach { row -> value(row)?.let { if (!seen.add(it)) damaged("Duplicate $what") } }
    }

    val categories = ids(data.categories, { it.id }, "category")
    val attributes = ids(data.attributeDefinitions, { it.id }, "attribute_definition")
    ids(data.categoryAttributes, { it.id }, "category_attribute")
    val locations = ids(data.locations, { it.id }, "location")
    val articles = ids(data.articles, { it.id }, "article")
    ids(data.articleFlags, { it.id }, "article_flag")
    val items = ids(data.items, { it.id }, "item")
    ids(data.movements, { it.id }, "stock_movement")
    val reviews = ids(data.reviews, { it.id }, "stock_review")
    ids(data.reviewLines, { it.id }, "stock_review_line")

    // Database unique indexes are case-sensitive; these are the exact values
    unique(data.categories, { it.name }, "category name")
    unique(data.attributeDefinitions, { it.key }, "attribute key")
    unique(data.categoryAttributes, { "${it.categoryId}/${it.attributeId}" }, "category attribute")
    unique(data.articles, { it.sku }, "sku")
    unique(data.locations, { it.barcode }, "location tag")
    // One tag = one thing: an NFC tag is on one article or one location, never both
    unique(data.articles.map { it.barcode } + data.locations.map { it.barcode }, { it }, "NFC tag")
    unique(data.items, { it.serialNumber }, "serial number")

    data.categoryAttributes.forEach {
        if (it.categoryId !in categories || it.attributeId !in attributes) damaged("Unknown category attribute reference")
    }
    data.locations.forEach { if (it.parentId != null && it.parentId !in locations) damaged("Unknown parent location") }
    data.articles.forEach {
        if (it.categoryId != null && it.categoryId !in categories) damaged("Unknown category")
        if (it.imagePath != null && !isValidImagePath(it.imagePath)) damaged("Invalid image path")
    }
    data.articleFlags.forEach {
        if (it.articleId !in articles) damaged("Unknown flag article")
        if (it.note.length > MAX_FLAG_NOTE_LENGTH) damaged("Flag note too long")
    }
    data.items.forEach {
        if (it.articleId !in articles) damaged("Unknown item article")
        if (it.locationId != null && it.locationId !in locations) damaged("Unknown item location")
    }
    data.movements.forEach {
        if (it.articleId !in articles || it.locationId !in locations) damaged("Unknown movement reference")
        if (it.itemId != null && it.itemId !in items) damaged("Unknown movement item")
    }
    data.reviews.forEach { if (it.locationId !in locations) damaged("Unknown review location") }
    data.reviewLines.forEach {
        if (it.reviewId !in reviews || it.articleId !in articles || it.locationId !in locations) damaged("Unknown review line reference")
        if (it.itemId != null && it.itemId !in items) damaged("Unknown review line item")
        if (it.foundLocationId != null && it.foundLocationId !in locations) damaged("Unknown review line location")
    }
    if (locationCycle(data.locations) != null) damaged("Location cycle")
}

/** A location id that is its own ancestor, or null. */
internal fun locationCycle(locations: Collection<Location>): String? {
    val parent = locations.associate { it.id to it.parentId }
    val safe = HashSet<String>()
    for (start in parent.keys.sorted()) {
        val path = LinkedHashSet<String>()
        var current: String? = start
        while (current != null && current !in safe) {
            if (!path.add(current)) return current
            current = parent[current]
        }
        safe += path
    }
    return null
}

/** Locations with each parent before its children (foreign key on parent_id). Assumes no cycle. */
internal fun parentsFirst(locations: List<Location>): List<Location> {
    val byId = locations.associateBy { it.id }
    val done = HashSet<String>()
    val result = ArrayList<Location>(locations.size)
    fun visit(location: Location, depth: Int) {
        if (location.id in done || depth > locations.size) return
        location.parentId?.let { byId[it] }?.let { visit(it, depth + 1) }
        if (done.add(location.id)) result += location
    }
    locations.sortedBy { it.id }.forEach { visit(it, 0) }
    return result
}

/**
 * Current location of each serial unit, from its movements: the location where its sum is positive
 * (lowest id when several, as in the database query), null when it is out of stock (lent, lost).
 */
fun itemLocations(items: Collection<Item>, movements: Collection<StockMovement>): Map<String, String?> {
    val sums = HashMap<String, HashMap<String, Long>>()
    movements.forEach { m ->
        val itemId = m.itemId ?: return@forEach
        val perLocation = sums.getOrPut(itemId) { HashMap() }
        perLocation[m.locationId] = (perLocation[m.locationId] ?: 0) + m.quantityMilli
    }
    return items.associate { item ->
        item.id to sums[item.id]?.filterValues { it > 0 }?.keys?.minOrNull()
    }
}

/**
 * [data] as imported into a standalone phone: `stock_movement.created_by` and `device_id`, `article_flag.created_by` (server
 * mode, schema 9+) are exported but cleared here. They name users and devices of a server this phone
 * is not connected to: personal data kept to a minimum (nFADP, GDPR), and a merge compares the
 * movements without them.
 */
fun BackupData.withoutServerAuthors(): BackupData {
    val movementsKnowAuthors = movements.any { it.createdBy != null || it.deviceId != null }
    val flagsKnowAuthors = articleFlags.any { it.createdBy != null }
    if (!movementsKnowAuthors && !flagsKnowAuthors) return this
    return copy(
        movements = if (movementsKnowAuthors) movements.map { if (it.createdBy == null && it.deviceId == null) it else it.copy(createdBy = null, deviceId = null) } else movements,
        articleFlags = if (flagsKnowAuthors) articleFlags.map { if (it.createdBy == null) it else it.copy(createdBy = null) } else articleFlags,
    )
}

/** [data] with every `item.location_id` recomputed from the movements. */
fun BackupData.withItemLocations(): BackupData {
    val locations = itemLocations(items, movements)
    return copy(items = items.map { it.copy(locationId = locations[it.id]) })
}
