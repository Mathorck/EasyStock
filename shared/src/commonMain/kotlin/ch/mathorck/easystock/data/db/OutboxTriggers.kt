package ch.mathorck.easystock.data.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Server mode outbox triggers (schema v9). Room has no trigger annotation, so they are created by
 * [MIGRATION_8_9] and, for fresh installs, by [OutboxTriggersCallback] on every open (idempotent).
 *
 * Each synced table gets an AFTER INSERT and an AFTER UPDATE trigger that adds (table, row id) to
 * `outbox`, or does nothing when that row is already waiting (primary key + INSERT OR IGNORE).
 * They fire only when `sync_state` has a row with `applying = 0`: a standalone phone has no
 * `sync_state` row, so its outbox stays empty; the sync engine sets `applying` while it writes pulled
 * rows, so they are not sent back.
 */
object OutboxTriggers {
    /** The synced tables, in dependency order (parents first). */
    val SYNCED_TABLES: List<String> = listOf(
        "category",
        "attribute_definition",
        "category_attribute",
        "location",
        "article",
        "item",
        "stock_movement",
        "stock_review",
        "stock_review_line",
    )

    /** Prefix of every trigger name, also used by the tests. */
    const val NAME_PREFIX = "outbox_"

    /** Epoch milliseconds UTC in SQLite. */
    private const val NOW_MILLIS = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)"

    /** `outbox_<table>_insert` or `outbox_<table>_update`. */
    fun triggerName(table: String, insert: Boolean): String = "$NAME_PREFIX${table}_${if (insert) "insert" else "update"}"

    /** One CREATE TRIGGER IF NOT EXISTS statement per synced table and event. */
    val statements: List<String> = SYNCED_TABLES.flatMap { table ->
        listOf(true, false).map { insert ->
            "CREATE TRIGGER IF NOT EXISTS `${triggerName(table, insert)}` AFTER ${if (insert) "INSERT" else "UPDATE"} ON `$table` " +
                "WHEN EXISTS (SELECT 1 FROM `sync_state` WHERE `applying` = 0) BEGIN " +
                "INSERT OR IGNORE INTO `outbox` (`table_name`, `row_id`, `changed_at`) " +
                "VALUES ('$table', NEW.`id`, $NOW_MILLIS); END"
        }
    }

    fun create(connection: SQLiteConnection) {
        statements.forEach { connection.execSQL(it) }
    }
}
