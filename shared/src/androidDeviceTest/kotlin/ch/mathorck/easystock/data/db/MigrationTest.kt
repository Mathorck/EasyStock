package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Migration tests.
 * - Every exported schema (shared/schemas/.../N.json) must migrate to the next one and to the latest, checked by
 *   Room's [MigrationTestHelper]. The versions come from the exported schemas, not from a list.
 * - A database file written with the version 2 schema is opened with the current [AppDatabase]: Room runs the
 *   migrations and checks the result against the current entities.
 */
class MigrationTest {
    private lateinit var file: File

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val helperFile = instrumentation.targetContext.getDatabasePath("migration-helper-test.db")

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = helperFile,
        driver = BundledSQLiteDriver(),
        databaseClass = AppDatabase::class,
    )

    /** Versions of the exported schemas, read from the schema assets of the test APK. */
    private fun schemaVersions(): List<Int> =
        (instrumentation.context.assets.list(SCHEMA_DIR) ?: emptyArray())
            .mapNotNull { it.removeSuffix(".json").toIntOrNull() }
            .sorted()

    private fun deleteHelperFiles() {
        listOf("", "-wal", "-shm", "-journal").forEach { File(helperFile.path + it).delete() }
    }

    @Test
    fun everyVersionHasAnExportedSchema() {
        val versions = schemaVersions()
        assertTrue(versions.isNotEmpty(), "No exported schema found in $SCHEMA_DIR")
        assertEquals((1..versions.last()).toList(), versions, "Missing exported schema: versions must have no gap")
    }

    @Test
    fun latestSchemaIsTheCurrentDatabaseVersion() = runBlocking<Unit> {
        // A new database is created at AppDatabase.version
        val db = open()
        try {
            db.categoryDao().observeAll().first()
        } finally {
            db.close()
        }
        val connection = BundledSQLiteDriver().open(file.path)
        try {
            val version = connection.prepare("PRAGMA user_version").use { it.step(); it.getLong(0).toInt() }
            assertEquals(schemaVersions().last(), version, "AppDatabase.version and the latest exported schema differ")
        } finally {
            connection.close()
        }
    }

    @Test
    fun everyVersionMigratesToTheNextOne() {
        schemaVersions().zipWithNext().forEach { (from, to) ->
            deleteHelperFiles()
            helper.createDatabase(from).close()
            helper.runMigrationsAndValidate(to).close()
        }
    }

    @Test
    fun everyVersionMigratesToTheLatest() {
        val versions = schemaVersions()
        versions.dropLast(1).forEach { from ->
            deleteHelperFiles()
            helper.createDatabase(from).close()
            helper.runMigrationsAndValidate(versions.last()).close()
        }
    }

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        file = context.getDatabasePath("migration-test.db")
        file.parentFile?.mkdirs()
        deleteFiles()
    }

    @After
    fun tearDown() = deleteFiles()

    private fun deleteFiles() {
        listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
    }

    private fun writeVersion2(vararg rows: String) {
        val connection = BundledSQLiteDriver().open(file.path)
        try {
            (VERSION_2_SCHEMA + rows).forEach { connection.execSQL(it) }
            connection.execSQL("PRAGMA user_version = 2")
        } finally {
            connection.close()
        }
    }

    private fun open(): AppDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return Room.databaseBuilder<AppDatabase>(context, file.path).setDriver(BundledSQLiteDriver()).build()
    }

    @Test
    fun version2To3KeepsTheDataWithoutTemplates() = runBlocking<Unit> {
        writeVersion2(
            "INSERT INTO category (id, name, created_at, updated_at) VALUES ('c1', 'Ropes', 1, 1)",
            "INSERT INTO article (id, name, category_id, attributes, created_at, updated_at) " +
                "VALUES ('a1', 'My rope', 'c1', '{\"length\":50}', 1, 2)",
        )
        val db = open()
        try {
            val category = db.categoryDao().observeAll().first().single()
            assertEquals("Ropes", category.name)
            assertNull(category.nameTemplate)

            val article = assertNotNull(db.articleDetailDao().observeArticle("a1").first()).article
            assertEquals("My rope", article.name)
            // Typed names stay typed
            assertFalse(article.nameAuto)
            assertEquals(2, article.updatedAt)
            assertEquals("50", article.attributes?.get("length").toString())
        } finally {
            db.close()
        }
    }

    private companion object {
        const val SCHEMA_DIR = "ch.mathorck.easystock.data.db.AppDatabase"

        val VERSION_2_SCHEMA = listOf(
            """CREATE TABLE IF NOT EXISTS `category` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, PRIMARY KEY(`id`))""",
            """CREATE UNIQUE INDEX IF NOT EXISTS `index_category_name` ON `category` (`name`)""",
            """CREATE INDEX IF NOT EXISTS `index_category_updated_at` ON `category` (`updated_at`)""",
            """CREATE TABLE IF NOT EXISTS `attribute_definition` (`id` TEXT NOT NULL, `key` TEXT NOT NULL, `label` TEXT NOT NULL, `data_type` TEXT NOT NULL, `unit` TEXT, `options` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, PRIMARY KEY(`id`))""",
            """CREATE UNIQUE INDEX IF NOT EXISTS `index_attribute_definition_key` ON `attribute_definition` (`key`)""",
            """CREATE INDEX IF NOT EXISTS `index_attribute_definition_updated_at` ON `attribute_definition` (`updated_at`)""",
            """CREATE TABLE IF NOT EXISTS `category_attribute` (`id` TEXT NOT NULL, `category_id` TEXT NOT NULL, `attribute_id` TEXT NOT NULL, `required` INTEGER NOT NULL DEFAULT 0, `sort_order` INTEGER NOT NULL DEFAULT 0, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`category_id`) REFERENCES `category`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`attribute_id`) REFERENCES `attribute_definition`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
            """CREATE UNIQUE INDEX IF NOT EXISTS `index_category_attribute_category_id_attribute_id` ON `category_attribute` (`category_id`, `attribute_id`)""",
            """CREATE INDEX IF NOT EXISTS `index_category_attribute_attribute_id` ON `category_attribute` (`attribute_id`)""",
            """CREATE INDEX IF NOT EXISTS `index_category_attribute_updated_at` ON `category_attribute` (`updated_at`)""",
            """CREATE TABLE IF NOT EXISTS `article` (`id` TEXT NOT NULL, `sku` TEXT, `name` TEXT NOT NULL, `description` TEXT, `category_id` TEXT, `tracking` TEXT NOT NULL DEFAULT 'quantity', `unit` TEXT NOT NULL DEFAULT 'pce', `barcode` TEXT, `threshold` INTEGER, `image_path` TEXT, `attributes` TEXT, `archived` INTEGER NOT NULL DEFAULT 0, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`category_id`) REFERENCES `category`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
            """CREATE UNIQUE INDEX IF NOT EXISTS `index_article_sku` ON `article` (`sku`)""",
            """CREATE UNIQUE INDEX IF NOT EXISTS `index_article_barcode` ON `article` (`barcode`)""",
            """CREATE INDEX IF NOT EXISTS `index_article_category_id` ON `article` (`category_id`)""",
            """CREATE INDEX IF NOT EXISTS `index_article_updated_at` ON `article` (`updated_at`)""",
            """CREATE TABLE IF NOT EXISTS `location` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `parent_id` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`parent_id`) REFERENCES `location`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
            """CREATE INDEX IF NOT EXISTS `index_location_parent_id` ON `location` (`parent_id`)""",
            """CREATE INDEX IF NOT EXISTS `index_location_updated_at` ON `location` (`updated_at`)""",
            """CREATE TABLE IF NOT EXISTS `item` (`id` TEXT NOT NULL, `article_id` TEXT NOT NULL, `serial_number` TEXT, `status` TEXT NOT NULL DEFAULT 'available', `location_id` TEXT, `attributes` TEXT, `archived` INTEGER NOT NULL DEFAULT 0, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `deleted_at` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`article_id`) REFERENCES `article`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`location_id`) REFERENCES `location`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
            """CREATE UNIQUE INDEX IF NOT EXISTS `index_item_serial_number` ON `item` (`serial_number`)""",
            """CREATE INDEX IF NOT EXISTS `index_item_article_id` ON `item` (`article_id`)""",
            """CREATE INDEX IF NOT EXISTS `index_item_location_id` ON `item` (`location_id`)""",
            """CREATE INDEX IF NOT EXISTS `index_item_updated_at` ON `item` (`updated_at`)""",
            """CREATE TABLE IF NOT EXISTS `stock_movement` (`id` TEXT NOT NULL, `article_id` TEXT NOT NULL, `item_id` TEXT, `location_id` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `reason` TEXT NOT NULL, `comment` TEXT, `occurred_at` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`article_id`) REFERENCES `article`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`item_id`) REFERENCES `item`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`location_id`) REFERENCES `location`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )""",
            """CREATE INDEX IF NOT EXISTS `index_stock_movement_article_id_location_id` ON `stock_movement` (`article_id`, `location_id`)""",
            """CREATE INDEX IF NOT EXISTS `index_stock_movement_item_id` ON `stock_movement` (`item_id`)""",
            """CREATE INDEX IF NOT EXISTS `index_stock_movement_location_id` ON `stock_movement` (`location_id`)""",
            """CREATE INDEX IF NOT EXISTS `index_stock_movement_created_at` ON `stock_movement` (`created_at`)""",
            """CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)""",
            """INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '01f862e825d7a09bcf759b64b5c1944e')""",
        )
    }
}
