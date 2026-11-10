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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

const val DATABASE_NAME = "easystock.db"

/** Current schema version. Also written in backups (see backup/BackupManifest). */
const val DB_VERSION = 4

@Database(
    entities = [
        Category::class,
        AttributeDefinition::class,
        CategoryAttribute::class,
        Article::class,
        Location::class,
        Item::class,
        StockMovement::class,
    ],
    version = DB_VERSION,
    autoMigrations = [
        // sku becomes nullable
        AutoMigration(from = 1, to = 2),
        // category.name_template, article.name_auto
        AutoMigration(from = 2, to = 3),
        // location.icon, location.color
        AutoMigration(from = 3, to = 4),
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
}

// Implemented by the Room compiler on each platform
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/** Builds the database from a platform builder (see androidMain / iosMain). */
fun buildAppDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
