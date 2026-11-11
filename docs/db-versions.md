# Database versions

Source of truth for the schema: `docs/schema.dbml`. Exported Room schemas: `shared/schemas/ch.mathorck.easystock.data.db.AppDatabase/<version>.json`. Checklist for a new version: `.claude/rules/database.md`.

| Version | Changes | Migration | Commit |
|---|---|---|---|
| 1 | Initial schema: `category`, `attribute_definition`, `category_attribute`, `article`, `location`, `item`, `stock_movement` | none (first version) | `c5cf130` |
| 2 | `article.sku` becomes nullable (was `NOT NULL`) | `AutoMigration(1, 2)` | `76d8f32` |
| 3 | Adds `category.name_template` (text, nullable) and `article.name_auto` (integer, not null, default 0) | `AutoMigration(2, 3)` | `4705f7f` |
| 4 | Adds `location.icon` and `location.color` (varchar(32), nullable; keys of the app icon list and color palette, null = default) | `AutoMigration(3, 4)` | `56fb008` |
| 5 | Adds the tables `stock_review` (a stock review of a location and its sub-locations: status, started_at, finished_at) and `stock_review_line` (expected and counted quantity or unit result per article/unit and location, info flag and note) | `AutoMigration(4, 5)` | `9ddfaa3` |
| 6 | Adds `location.barcode` (varchar(64), nullable, unique index): optional NFC tag ID of a location, same format as `article.barcode`. A tag is on an article or a location, never both (checked by the app) | `AutoMigration(5, 6)` | `afa695a` |
| 7 | Adds `stock_review_line.pending_item` (JSON text, nullable): a new unit found during a review, kept on the line until the review is applied | `AutoMigration(6, 7)` | `2f76ec8` |
| 8 | Adds `stock_review_line.info_fixed_at` (timestamp, nullable): when an "info to fix" flag of a finished review was marked fixed from the article page. Null = still open | `AutoMigration(7, 8)` | `ac244c8` |
| 9 | Server mode (optional, app only). Adds `stock_movement.created_by` and `device_id` (uuid, nullable, set by the server, null in standalone mode). Adds the app-only tables `sync_state`, `outbox`, `server_user`, `low_stock_alert`, `rejected_change` (never synced, never in a backup, empty in standalone mode). Adds AFTER INSERT / AFTER UPDATE triggers `outbox_<table>_insert/update` on the 9 synced tables, active only when a `sync_state` row exists with `applying = 0` (also created on open for fresh installs) | `Migration(8, 9)` (manual: triggers) | `7f39324` |
| 10 | Sync engine (server mode, app only). Adds the app-only tables `image_hash` (photo path to SHA-256: the server names photos by hash; a row whose file is missing is a photo still to download) and `pending_push` (the last push batch id and exact body, sent again after a network failure). Never synced, never in a backup, empty in standalone mode | `AutoMigration(9, 10)` | `fad02a1` |
