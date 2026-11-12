package ch.mathorck.easystock.data.db

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v8 to v9: server mode tables (app only), `stock_movement.created_by` and `device_id`, outbox triggers.
 * Manual: Room can't create triggers. The table SQL is the one of the exported schema 9.json, so the
 * result matches the entities exactly. No row is changed.
 */
val MIGRATION_8_9: Migration = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        listOf(
            "ALTER TABLE `stock_movement` ADD COLUMN `created_by` TEXT",
            "ALTER TABLE `stock_movement` ADD COLUMN `device_id` TEXT",
            "CREATE TABLE IF NOT EXISTS `sync_state` (`id` INTEGER NOT NULL, `server_url` TEXT NOT NULL, `cert_fingerprint` TEXT, `user_id` TEXT NOT NULL, `role` TEXT NOT NULL, `since` INTEGER NOT NULL DEFAULT 0, `last_sync_at` INTEGER, `applying` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `outbox` (`table_name` TEXT NOT NULL, `row_id` TEXT NOT NULL, `changed_at` INTEGER NOT NULL, PRIMARY KEY(`table_name`, `row_id`))",
            "CREATE INDEX IF NOT EXISTS `index_outbox_changed_at` ON `outbox` (`changed_at`)",
            "CREATE TABLE IF NOT EXISTS `server_user` (`user_id` TEXT NOT NULL, `display_name` TEXT NOT NULL, PRIMARY KEY(`user_id`))",
            "CREATE TABLE IF NOT EXISTS `low_stock_alert` (`id` TEXT NOT NULL, `article_id` TEXT NOT NULL, `crossed_at` INTEGER NOT NULL, `stock` INTEGER NOT NULL, `notified` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_low_stock_alert_article_id` ON `low_stock_alert` (`article_id`)",
            "CREATE INDEX IF NOT EXISTS `index_low_stock_alert_notified` ON `low_stock_alert` (`notified`)",
            "CREATE TABLE IF NOT EXISTS `rejected_change` (`id` TEXT NOT NULL, `table_name` TEXT NOT NULL, `row_id` TEXT NOT NULL, `reason` TEXT NOT NULL, `rejected_at` INTEGER NOT NULL, `seen_at` INTEGER, PRIMARY KEY(`id`))",
        ).forEach { connection.execSQL(it) }
        // After the tables: the triggers read sync_state and write outbox
        OutboxTriggers.create(connection)
    }
}

/**
 * v10 to v11: the app-only table `server_seq` (first change wins: the `baseSeq` of each pushed edit).
 * Manual because it also changes data: a connected phone has no seq for the rows it already holds,
 * so `sync_state.since` goes back to 0 and the next cycle pulls everything again, which stores the seq
 * of every row. Standalone phones have no `sync_state` row: nothing changes for them.
 */
val MIGRATION_10_11: Migration = object : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `server_seq` (`table_name` TEXT NOT NULL, `row_id` TEXT NOT NULL, `seq` INTEGER NOT NULL, PRIMARY KEY(`table_name`, `row_id`))",
        )
        connection.execSQL("UPDATE sync_state SET since = 0")
    }
}

/** Manual migrations, added to every database builder (app and tests). */
val MANUAL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_8_9, MIGRATION_10_11)

/** Creates the outbox triggers on a fresh install, and on every open (a no-op when they exist). */
object OutboxTriggersCallback : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) = OutboxTriggers.create(connection)

    override fun onOpen(connection: SQLiteConnection) = OutboxTriggers.create(connection)
}

/** Migrations and callbacks every [AppDatabase] needs, whatever the driver or file. */
fun RoomDatabase.Builder<AppDatabase>.withAppSchema(): RoomDatabase.Builder<AppDatabase> =
    addMigrations(*MANUAL_MIGRATIONS).addCallback(OutboxTriggersCallback)
