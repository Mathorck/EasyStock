package ch.mathorck.easystock.services.backup

import ch.mathorck.easystock.data.db.DB_VERSION
import ch.mathorck.easystock.data.dto.backup.BackupBundle
import ch.mathorck.easystock.data.dto.backup.BackupData
import ch.mathorck.easystock.data.dto.backup.BackupManifest
import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.data.model.article.ArticleFlag
import ch.mathorck.easystock.data.model.article.Item
import ch.mathorck.easystock.data.model.attributes.AttributeDefinition
import ch.mathorck.easystock.data.model.attributes.CategoryAttribute
import ch.mathorck.easystock.data.model.categories.Category
import ch.mathorck.easystock.data.model.location.Location
import ch.mathorck.easystock.data.model.review.StockReview
import ch.mathorck.easystock.data.model.review.StockReviewLine
import ch.mathorck.easystock.data.model.stock.StockMovement
import ch.mathorck.easystock.enums.backup.BackupError
import ch.mathorck.easystock.enums.backup.BackupTable
import ch.mathorck.easystock.exceptions.backup.BackupException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

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
        BackupTable.ARTICLE_FLAG -> encode(ArticleFlag.serializer(), data.articleFlags)
        BackupTable.ITEM -> encode(Item.serializer(), data.items)
        BackupTable.STOCK_MOVEMENT -> encode(StockMovement.serializer(), data.movements)
        BackupTable.STOCK_REVIEW -> encode(StockReview.serializer(), data.reviews)
        BackupTable.STOCK_REVIEW_LINE -> encode(StockReviewLine.serializer(), data.reviewLines)
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
            articleFlags = table(BackupTable.ARTICLE_FLAG, ArticleFlag.serializer()),
            items = table(BackupTable.ITEM, Item.serializer()),
            movements = table(BackupTable.STOCK_MOVEMENT, StockMovement.serializer()),
            reviews = table(BackupTable.STOCK_REVIEW, StockReview.serializer()),
            reviewLines = table(BackupTable.STOCK_REVIEW_LINE, StockReviewLine.serializer()),
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
