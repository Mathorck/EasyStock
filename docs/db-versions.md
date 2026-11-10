# Database versions

Source of truth for the schema: `docs/schema.dbml`. Exported Room schemas: `shared/schemas/ch.mathorck.easystock.data.db.AppDatabase/<version>.json`. Checklist for a new version: `.claude/rules/database.md`.

| Version | Changes | Migration | Commit |
|---|---|---|---|
| 1 | Initial schema: `category`, `attribute_definition`, `category_attribute`, `article`, `location`, `item`, `stock_movement` | none (first version) | `c5cf130` |
| 2 | `article.sku` becomes nullable (was `NOT NULL`) | `AutoMigration(1, 2)` | `76d8f32` |
| 3 | Adds `category.name_template` (text, nullable) and `article.name_auto` (integer, not null, default 0) | `AutoMigration(2, 3)` | `4705f7f` |
| 4 | Adds `location.icon` and `location.color` (varchar(32), nullable; keys of the app icon list and color palette, null = default) | `AutoMigration(3, 4)` | `56fb008` |
| 5 | Adds the tables `stock_review` (a stock review of a location and its sub-locations: status, started_at, finished_at) and `stock_review_line` (expected and counted quantity or unit result per article/unit and location, info flag and note) | `AutoMigration(4, 5)` | (this commit) |
