package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.SQLiteConnection
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
 * - Version 8 to 9 adds the outbox triggers: checked after the migration and on a fresh database, with the
 *   outbox filled only when connected (a `sync_state` row) and not while `applying`.
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
            helper.runMigrationsAndValidate(to, MANUAL_MIGRATIONS.toList()).close()
        }
    }

    @Test
    fun everyVersionMigratesToTheLatest() {
        val versions = schemaVersions()
        versions.dropLast(1).forEach { from ->
            deleteHelperFiles()
            helper.createDatabase(from).close()
            helper.runMigrationsAndValidate(versions.last(), MANUAL_MIGRATIONS.toList()).close()
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
        return Room.databaseBuilder<AppDatabase>(context, file.path).withAppSchema().setDriver(BundledSQLiteDriver()).build()
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

    /** Version 3 to 4 adds location.icon and location.color: existing locations keep everything, default look. */
    @Test
    fun version2To4KeepsLocationsWithTheDefaultAppearance() = runBlocking<Unit> {
        writeVersion2(
            "INSERT INTO location (id, name, parent_id, created_at, updated_at) VALUES ('l1', 'Workshop', NULL, 1, 2)",
            "INSERT INTO location (id, name, parent_id, created_at, updated_at) VALUES ('l2', 'Shelf', 'l1', 3, 4)",
        )
        val db = open()
        try {
            val locations = db.locationDao().observeLocations().first().associateBy { it.id }
            assertEquals(setOf("l1", "l2"), locations.keys)
            assertEquals("Workshop", locations.getValue("l1").name)
            assertEquals(4, locations.getValue("l2").updatedAt)
            assertEquals("l1", locations.getValue("l2").parentId)
            locations.values.forEach {
                assertNull(it.icon)
                assertNull(it.color)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun version8To9KeepsTheMovementsAndAddsTheOutboxTriggers() {
        deleteHelperFiles()
        helper.createDatabase(8).use { v8 ->
            V8_ROWS.forEach { v8.execSQL(it) }
        }
        helper.runMigrationsAndValidate(9, MANUAL_MIGRATIONS.toList()).use { db ->
            assertEquals(OutboxTriggers.statements.size, db.outboxTriggerCount())
            assertEquals(18, db.outboxTriggerCount())
            db.prepare("SELECT quantity, created_by, device_id FROM stock_movement WHERE id = 'm1'").use {
                assertTrue(it.step())
                assertEquals(5000, it.getLong(0))
                assertTrue(it.isNull(1))
                assertTrue(it.isNull(2))
            }
            // The migration itself never fills the outbox, and app-only tables start empty
            listOf("outbox", "sync_state", "server_user", "low_stock_alert", "rejected_change").forEach {
                assertEquals(0, db.count(it), it)
            }
            assertOutboxOnlyWhenConnected(db)
        }
    }

    @Test
    fun freshDatabaseHasTheOutboxTriggers() = runBlocking<Unit> {
        val db = open()
        try {
            db.categoryDao().observeAll().first()
        } finally {
            db.close()
        }
        // A second open keeps exactly one copy of each trigger (CREATE TRIGGER IF NOT EXISTS)
        val again = open()
        try {
            again.categoryDao().observeAll().first()
        } finally {
            again.close()
        }
        BundledSQLiteDriver().open(file.path).use { connection ->
            assertEquals(18, connection.outboxTriggerCount())
            connection.execSQL(V8_ROWS.first())
            assertOutboxOnlyWhenConnected(connection)
        }
    }

    /** Standalone (no sync_state): nothing queued. Connected: one row per changed row. Applying: nothing. */
    private fun assertOutboxOnlyWhenConnected(db: SQLiteConnection) {
        db.execSQL("INSERT INTO category (id, name, created_at, updated_at) VALUES ('c-standalone', 'Standalone', 1, 1)")
        db.execSQL("UPDATE category SET name = 'Standalone 2' WHERE id = 'c-standalone'")
        assertEquals(0, db.count("outbox"), "Standalone writes must not fill the outbox")

        db.execSQL("INSERT INTO sync_state (id, server_url, user_id, role) VALUES (1, 'https://example.test', 'u1', 'editor')")
        db.execSQL("INSERT INTO category (id, name, created_at, updated_at) VALUES ('c-connected', 'Connected', 1, 1)")
        db.execSQL("UPDATE category SET name = 'Connected 2' WHERE id = 'c-connected'")
        db.execSQL("UPDATE category SET name = 'Connected 3' WHERE id = 'c-connected'")
        db.execSQL("INSERT INTO location (id, name, created_at, updated_at) VALUES ('l-connected', 'Shelf', 1, 1)")
        db.execSQL(
            "INSERT INTO stock_movement (id, article_id, location_id, quantity, reason, occurred_at, created_at) " +
                "VALUES ('m-connected', 'a1', 'l-connected', 1000, 'purchase', 1, 1)",
        )
        assertEquals(
            listOf("category/c-connected", "location/l-connected", "stock_movement/m-connected"),
            db.outboxRows(),
        )
        db.prepare("SELECT changed_at FROM outbox WHERE row_id = 'c-connected'").use {
            assertTrue(it.step())
            // Epoch milliseconds, around now
            assertTrue(kotlin.math.abs(it.getLong(0) - System.currentTimeMillis()) < 60_000, "changed_at ${it.getLong(0)}")
        }

        db.execSQL("UPDATE sync_state SET applying = 1")
        db.execSQL("INSERT INTO category (id, name, created_at, updated_at) VALUES ('c-pulled', 'Pulled', 1, 1)")
        db.execSQL("UPDATE location SET name = 'Shelf 2' WHERE id = 'l-connected'")
        db.execSQL("DELETE FROM outbox WHERE table_name = 'location'")
        db.execSQL("UPDATE location SET name = 'Shelf 3' WHERE id = 'l-connected'")
        assertEquals(listOf("category/c-connected", "stock_movement/m-connected"), db.outboxRows(), "applying blocks the triggers")
    }

    private fun SQLiteConnection.count(table: String): Int =
        prepare("SELECT count(*) FROM `$table`").use { it.step(); it.getLong(0).toInt() }

    private fun SQLiteConnection.outboxTriggerCount(): Int =
        prepare("SELECT count(*) FROM sqlite_master WHERE type = 'trigger' AND name LIKE '${OutboxTriggers.NAME_PREFIX}%'")
            .use { it.step(); it.getLong(0).toInt() }

    private fun SQLiteConnection.outboxRows(): List<String> =
        prepare("SELECT table_name, row_id FROM outbox ORDER BY table_name, row_id").use {
            buildList { while (it.step()) add("${it.getText(0)}/${it.getText(1)}") }
        }

    private companion object {
        /** Rows written with the version 8 schema (and valid on a fresh database). */
        val V8_ROWS = listOf(
            "INSERT INTO article (id, name, tracking, unit, archived, name_auto, created_at, updated_at) VALUES ('a1', 'Rope', 'quantity', 'm', 0, 0, 1, 1)",
            "INSERT INTO location (id, name, created_at, updated_at) VALUES ('l1', 'Workshop', 1, 1)",
            "INSERT INTO stock_movement (id, article_id, location_id, quantity, reason, occurred_at, created_at) VALUES ('m1', 'a1', 'l1', 5000, 'purchase', 1, 1)",
        )

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
