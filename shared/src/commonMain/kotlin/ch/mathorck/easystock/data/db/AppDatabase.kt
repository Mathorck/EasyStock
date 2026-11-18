package ch.mathorck.easystock.data.db

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import ch.mathorck.easystock.data.dao.alerts.AlertDao
import ch.mathorck.easystock.data.dao.alerts.DateAlertDao
import ch.mathorck.easystock.data.dao.alerts.DateAlertNoticeDao
import ch.mathorck.easystock.data.dao.appinfo.AppInfoDao
import ch.mathorck.easystock.data.dao.article.ArticleDao
import ch.mathorck.easystock.data.dao.article.ArticleDetailDao
import ch.mathorck.easystock.data.dao.article.ArticleFlagDao
import ch.mathorck.easystock.data.dao.article.ArticleFormDao
import ch.mathorck.easystock.data.dao.attributes.AttributeDao
import ch.mathorck.easystock.data.dao.backup.BackupDao
import ch.mathorck.easystock.data.dao.categories.CategoryDao
import ch.mathorck.easystock.data.dao.location.LocationDao
import ch.mathorck.easystock.data.dao.review.ReviewDao
import ch.mathorck.easystock.data.dao.stock.StockActionDao
import ch.mathorck.easystock.data.dao.stock.StockDao
import ch.mathorck.easystock.data.dao.sync.SessionDao
import ch.mathorck.easystock.data.dao.sync.SyncDao
import ch.mathorck.easystock.data.dao.tags.TagDao
import ch.mathorck.easystock.data.model.alerts.DateAlertNotice
import ch.mathorck.easystock.data.model.alerts.LowStockAlert
import ch.mathorck.easystock.data.model.article.Article
import ch.mathorck.easystock.data.model.article.ArticleFlag
import ch.mathorck.easystock.data.model.article.ImageHash
import ch.mathorck.easystock.data.model.article.Item
import ch.mathorck.easystock.data.model.attributes.AttributeDefinition
import ch.mathorck.easystock.data.model.attributes.CategoryAttribute
import ch.mathorck.easystock.data.model.categories.Category
import ch.mathorck.easystock.data.model.location.Location
import ch.mathorck.easystock.data.model.review.StockReview
import ch.mathorck.easystock.data.model.review.StockReviewLine
import ch.mathorck.easystock.data.model.server.ServerUser
import ch.mathorck.easystock.data.model.stock.StockMovement
import ch.mathorck.easystock.data.model.sync.OutboxEntry
import ch.mathorck.easystock.data.model.sync.PendingPush
import ch.mathorck.easystock.data.model.sync.RejectedChange
import ch.mathorck.easystock.data.model.sync.ServerSeq
import ch.mathorck.easystock.data.model.sync.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** Current schema version (Room). Backups and the server get [DATA_SCHEMA_VERSION] instead. */
const val DB_VERSION = 13

/**
 * Version of the shared data: the latest [DB_VERSION] whose synced tables (columns of the 10 tables in a
 * backup and in the sync) are the same as now. Sent to the server in `X-EasyStock-Schema-Version` (the
 * server answers 426 to a version it does not list) and written in backups (the server and older apps
 * refuse a newer one). A version that changes only indices or app-only tables keeps it, so it still
 * syncs with the servers of the previous version. Set it to [DB_VERSION] when a synced table changes
 * (the server must then list the new version).
 */
const val DATA_SCHEMA_VERSION = 13

@Database(
    entities = [
        Category::class,
        AttributeDefinition::class,
        CategoryAttribute::class,
        Article::class,
        ArticleFlag::class,
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
        // v13, app only
        DateAlertNotice::class,
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
        // article.order_url, attribute_definition.flag_when_past / notify_when_past, article_flag, date_alert_notice
        AutoMigration(from = 12, to = 13),
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

    abstract fun articleFlagDao(): ArticleFlagDao

    abstract fun dateAlertNoticeDao(): DateAlertNoticeDao

    abstract fun dateAlertDao(): DateAlertDao
}

/** Builds the database from a platform builder (see androidMain / iosMain). */
fun buildAppDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase =
    builder
        .withAppSchema()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
