package ch.mathorck.easystock.backup

import ch.mathorck.easystock.data.db.DB_VERSION
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.AttributeDefinition
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.CategoryAttribute
import ch.mathorck.easystock.data.model.Item
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.StockMovement
import ch.mathorck.easystock.images.isValidImagePath
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
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

/** Table names, also the JSON file names in [TABLES_DIR]. Parent tables first (foreign keys). */
enum class BackupTable(val tableName: String) {
    CATEGORY("category"),
    ATTRIBUTE_DEFINITION("attribute_definition"),
    CATEGORY_ATTRIBUTE("category_attribute"),
    LOCATION("location"),
    ARTICLE("article"),
    ITEM("item"),
    STOCK_MOVEMENT("stock_movement"),
    ;

    val entryName: String get() = "$TABLES_DIR/$tableName.json"
}

@Serializable
data class BackupManifest(
    val format: String = BACKUP_FORMAT,
    @SerialName("format_version") val formatVersion: Int = BACKUP_FORMAT_VERSION,
    @SerialName("app_version") val appVersion: String,
    @SerialName("schema_version") val schemaVersion: Int = DB_VERSION,
    /** ISO 8601, UTC ("2026-11-10T14:03:00Z"). */
    @SerialName("created_at") val createdAt: String,
    /** Rows per table name, plus "images". */
    val counts: Map<String, Int> = emptyMap(),
)

/** Every row of every table, soft-deleted rows included. */
@Serializable
data class BackupData(
    @SerialName("category") val categories: List<Category> = emptyList(),
    @SerialName("attribute_definition") val attributeDefinitions: List<AttributeDefinition> = emptyList(),
    @SerialName("category_attribute") val categoryAttributes: List<CategoryAttribute> = emptyList(),
    @SerialName("location") val locations: List<Location> = emptyList(),
    @SerialName("article") val articles: List<Article> = emptyList(),
    @SerialName("item") val items: List<Item> = emptyList(),
    @SerialName("stock_movement") val movements: List<StockMovement> = emptyList(),
) {
    fun counts(): Map<String, Int> = mapOf(
        BackupTable.CATEGORY.tableName to categories.size,
        BackupTable.ATTRIBUTE_DEFINITION.tableName to attributeDefinitions.size,
        BackupTable.CATEGORY_ATTRIBUTE.tableName to categoryAttributes.size,
        BackupTable.LOCATION.tableName to locations.size,
        BackupTable.ARTICLE.tableName to articles.size,
        BackupTable.ITEM.tableName to items.size,
        BackupTable.STOCK_MOVEMENT.tableName to movements.size,
    )

    /** Photo files referenced by the articles, deleted articles included (photos are kept on soft delete). */
    fun imagePaths(): Set<String> = articles.mapNotNull { it.imagePath }.toSet()

    val isEmpty: Boolean get() = counts().values.all { it == 0 }
}

/** Manifest and tables in one document, for a QR code (no photos). */
@Serializable
data class BackupBundle(val manifest: BackupManifest, val data: BackupData)

/** Why a backup can't be read. Shown to the user, never contains row data. */
enum class BackupError {
    /** Not an EasyStock backup (unknown file or QR code). */
    NOT_A_BACKUP,

    /** Made by a newer app version: update the app first. */
    NEWER_VERSION,

    /** Unreadable, truncated, modified or inconsistent. */
    DAMAGED,

    /** A password is needed to open it. */
    PASSWORD_REQUIRED,

    /** The password does not open it. */
    WRONG_PASSWORD,

    /** Too large for the device (or a forged size). */
    TOO_LARGE,

    /** Wi-Fi transfer: this phone is not on a Wi-Fi (or Ethernet) network. */
    NOT_ON_WIFI,

    /** Wi-Fi transfer: the other phone can't be reached (other network, transfer stopped or expired). */
    UNREACHABLE,

    /** Wi-Fi transfer: the other phone stopped answering. */
    TIMEOUT,

    /** Reading or writing a file failed (storage full, file moved, ...). */
    FAILED,
}

class BackupException(val error: BackupError, message: String = error.name, cause: Throwable? = null) :
    Exception(message, cause)

val backupJson: Json = Json {
    encodeDefaults = true
    explicitNulls = true
    // Tolerates columns dropped in a later schema, older backups still decode
    ignoreUnknownKeys = true
    prettyPrint = false
}

private fun <T> decodeOrDamaged(serializer: KSerializer<T>, bytes: ByteArray): T = try {
    backupJson.decodeFromString(serializer, bytes.decodeToString(throwOnInvalidSequence = true))
} catch (e: SerializationException) {
    throw BackupException(BackupError.DAMAGED, "Invalid JSON", e)
} catch (e: IllegalArgumentException) {
    throw BackupException(BackupError.DAMAGED, "Invalid JSON", e)
} catch (e: CharacterCodingException) {
    throw BackupException(BackupError.DAMAGED, "Invalid UTF-8", e)
}

object BackupCodec {
    fun encodeManifest(manifest: BackupManifest): ByteArray =
        backupJson.encodeToString(BackupManifest.serializer(), manifest).encodeToByteArray()

    /** Decodes and checks the manifest: throws [BackupException] when this app can't import it. */
    fun decodeManifest(bytes: ByteArray): BackupManifest {
        val manifest = try {
            decodeOrDamaged(BackupManifest.serializer(), bytes)
        } catch (e: BackupException) {
            throw BackupException(BackupError.NOT_A_BACKUP, "Invalid manifest", e)
        }
        checkManifest(manifest)
        return manifest
    }

    /**
     * Same or older format and schema: accepted. Newer: [BackupError.NEWER_VERSION].
     * Older schemas decode with the defaults of the current entities (columns added later).
     */
    fun checkManifest(manifest: BackupManifest, maxFormat: Int = BACKUP_FORMAT_VERSION, maxSchema: Int = DB_VERSION) {
        if (manifest.format != BACKUP_FORMAT) throw BackupException(BackupError.NOT_A_BACKUP, "Unknown format")
        if (manifest.formatVersion < 1 || manifest.schemaVersion < 1) throw BackupException(BackupError.DAMAGED, "Invalid version")
        if (manifest.formatVersion > maxFormat) throw BackupException(BackupError.NEWER_VERSION, "Format ${manifest.formatVersion}")
        if (manifest.schemaVersion > maxSchema) throw BackupException(BackupError.NEWER_VERSION, "Schema ${manifest.schemaVersion}")
    }

    fun encodeTable(data: BackupData, table: BackupTable): ByteArray = when (table) {
        BackupTable.CATEGORY -> encode(Category.serializer(), data.categories)
        BackupTable.ATTRIBUTE_DEFINITION -> encode(AttributeDefinition.serializer(), data.attributeDefinitions)
        BackupTable.CATEGORY_ATTRIBUTE -> encode(CategoryAttribute.serializer(), data.categoryAttributes)
        BackupTable.LOCATION -> encode(Location.serializer(), data.locations)
        BackupTable.ARTICLE -> encode(Article.serializer(), data.articles)
        BackupTable.ITEM -> encode(Item.serializer(), data.items)
        BackupTable.STOCK_MOVEMENT -> encode(StockMovement.serializer(), data.movements)
    }

    /** [read] returns the JSON of a table, or null when the archive has no file for it (empty table). */
    fun decodeTables(read: (BackupTable) -> ByteArray?): BackupData {
        fun <T> table(table: BackupTable, serializer: KSerializer<T>): List<T> =
            read(table)?.let { decodeOrDamaged(ListSerializer(serializer), it) } ?: emptyList()
        return BackupData(
            categories = table(BackupTable.CATEGORY, Category.serializer()),
            attributeDefinitions = table(BackupTable.ATTRIBUTE_DEFINITION, AttributeDefinition.serializer()),
            categoryAttributes = table(BackupTable.CATEGORY_ATTRIBUTE, CategoryAttribute.serializer()),
            locations = table(BackupTable.LOCATION, Location.serializer()),
            articles = table(BackupTable.ARTICLE, Article.serializer()),
            items = table(BackupTable.ITEM, Item.serializer()),
            movements = table(BackupTable.STOCK_MOVEMENT, StockMovement.serializer()),
        )
    }

    fun encodeBundle(bundle: BackupBundle): ByteArray =
        backupJson.encodeToString(BackupBundle.serializer(), bundle).encodeToByteArray()

    fun decodeBundle(bytes: ByteArray): BackupBundle {
        val bundle = decodeOrDamaged(BackupBundle.serializer(), bytes)
        checkManifest(bundle.manifest)
        return bundle
    }

    private fun <T> encode(serializer: KSerializer<T>, rows: List<T>): ByteArray =
        backupJson.encodeToString(ListSerializer(serializer), rows).encodeToByteArray()
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
    val items = ids(data.items, { it.id }, "item")
    ids(data.movements, { it.id }, "stock_movement")

    // Database unique indexes are case-sensitive; these are the exact values
    unique(data.categories, { it.name }, "category name")
    unique(data.attributeDefinitions, { it.key }, "attribute key")
    unique(data.categoryAttributes, { "${it.categoryId}/${it.attributeId}" }, "category attribute")
    unique(data.articles, { it.sku }, "sku")
    unique(data.articles, { it.barcode }, "barcode")
    unique(data.items, { it.serialNumber }, "serial number")

    data.categoryAttributes.forEach {
        if (it.categoryId !in categories || it.attributeId !in attributes) damaged("Unknown category attribute reference")
    }
    data.locations.forEach { if (it.parentId != null && it.parentId !in locations) damaged("Unknown parent location") }
    data.articles.forEach {
        if (it.categoryId != null && it.categoryId !in categories) damaged("Unknown category")
        if (it.imagePath != null && !isValidImagePath(it.imagePath)) damaged("Invalid image path")
    }
    data.items.forEach {
        if (it.articleId !in articles) damaged("Unknown item article")
        if (it.locationId != null && it.locationId !in locations) damaged("Unknown item location")
    }
    data.movements.forEach {
        if (it.articleId !in articles || it.locationId !in locations) damaged("Unknown movement reference")
        if (it.itemId != null && it.itemId !in items) damaged("Unknown movement item")
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

/** [data] with every `item.location_id` recomputed from the movements. */
fun BackupData.withItemLocations(): BackupData {
    val locations = itemLocations(items, movements)
    return copy(items = items.map { it.copy(locationId = locations[it.id]) })
}
