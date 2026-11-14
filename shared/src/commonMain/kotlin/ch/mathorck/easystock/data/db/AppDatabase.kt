package ch.mathorck.easystock.data.db

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.AttributeDefinition
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.data.model.CategoryAttribute
import ch.mathorck.easystock.data.model.Item
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.StockMovement
import ch.mathorck.easystock.data.model.StockReview
import ch.mathorck.easystock.data.model.StockReviewLine
import ch.mathorck.easystock.data.model.SyncState
import ch.mathorck.easystock.data.model.OutboxEntry
import ch.mathorck.easystock.data.model.ServerUser
import ch.mathorck.easystock.data.model.LowStockAlert
import ch.mathorck.easystock.data.model.RejectedChange
import ch.mathorck.easystock.data.model.ImageHash
import ch.mathorck.easystock.data.model.PendingPush
import ch.mathorck.easystock.data.model.ServerSeq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

const val DATABASE_NAME = "easystock.db"

/** Current schema version (Room). Backups and the server get [DATA_SCHEMA_VERSION] instead. */
const val DB_VERSION = 12

/**
 * Version of the shared data: the latest [DB_VERSION] whose synced tables (columns of the 9 tables in a
 * backup and in the sync) are the same as now. Sent to the server in `X-EasyStock-Schema-Version` (the
 * server answers 426 to a version it does not list) and written in backups (the server and older apps
 * refuse a newer one). A version that changes only indices or app-only tables keeps it, so it still
 * syncs with the servers of the previous version. Set it to [DB_VERSION] when a synced table changes
 * (the server must then list the new version).
 */
const val DATA_SCHEMA_VERSION = 11

@Database(
    entities = [
        Category::class,
        AttributeDefinition::class,
        CategoryAttribute::class,
        Article::class,
        Location::class,
        Item::class,
        StockMovement::class,
        StockReview::class,
        StockReviewLine::class,
        // Server mode, app only (never synced, never in a backup)
        SyncState::class,
        OutboxEntry::class,
        ServerUser::class,
        LowStockAlert::class,
        RejectedChange::class,
        ImageHash::class,
        PendingPush::class,
        ServerSeq::class,
    ],
    version = DB_VERSION,
    autoMigrations = [
        // sku becomes nullable
        AutoMigration(from = 1, to = 2),
        // category.name_template, article.name_auto
        AutoMigration(from = 2, to = 3),
        // location.icon, location.color
        AutoMigration(from = 3, to = 4),
        // stock_review, stock_review_line
        AutoMigration(from = 4, to = 5),
        // location.barcode (NFC tag)
        AutoMigration(from = 5, to = 6),
        // stock_review_line.pending_item
        AutoMigration(from = 6, to = 7),
        // stock_review_line.info_fixed_at
        AutoMigration(from = 7, to = 8),
        // 8 to 9 (server mode tables, movement author, outbox triggers): manual, see MIGRATION_8_9
        // image_hash, pending_push (sync engine, app only)
        AutoMigration(from = 9, to = 10),
        // 10 to 11 (server_seq, then a full re-pull): manual, see MIGRATION_10_11
        // stock_movement indices (covering stock sums, movement history)
        AutoMigration(from = 11, to = 12),
    ],
    exportSchema = true,
)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun stockDao(): StockDao
    abstract fun locationDao(): LocationDao
    abstract fun categoryDao(): CategoryDao
    abstract fun articleFormDao(): ArticleFormDao
    abstract fun articleDetailDao(): ArticleDetailDao
    abstract fun stockActionDao(): StockActionDao
    abstract fun attributeDao(): AttributeDao
    abstract fun backupDao(): BackupDao
    abstract fun reviewDao(): ReviewDao
    abstract fun tagDao(): TagDao
    abstract fun appInfoDao(): AppInfoDao
    abstract fun sessionDao(): SessionDao

    abstract fun syncDao(): SyncDao

    abstract fun alertDao(): AlertDao
}

// Implemented by the Room compiler on each platform
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/** Builds the database from a platform builder (see androidMain / iosMain). */
fun buildAppDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase =
    builder
        .withAppSchema()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
