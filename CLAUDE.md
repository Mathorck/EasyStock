# CLAUDE.md — EasyStock

Read `README.md` first: purpose, scope, setup, data model.

## Stack
- Kotlin Multiplatform, Compose Multiplatform, Material3
- Only Android is delivered and tested. Keep code in `shared/src/commonMain` unless it needs a platform API (NFC, notifications, files), then use `expect`/`actual`.
- Building from WSL fails (no Android SDK). Build through the Android Studio MCP (`build_project`).
- Run Windows git (`git.exe`) from WSL, the repo lives on the Windows drive.

## Product rules
- Standalone first: the app must work fully offline, no account, data stays on the phone.
- Server mode is optional and last. Don't add server dependencies to the standalone path.
- Personal data (future movement history, users) must follow Swiss nFADP and GDPR: minimal data, local by default.

## Data model (`docs/schema.dbml`)
- Source of truth for tables and columns. Change the DBML first, then the code.
- UUID primary keys generated on the device.
- Timestamps set by the app, always UTC.
- Soft delete with `deleted_at`. Never hard-delete synced rows.
- `stock_movement` is append-only: never update or delete. Stock = `SUM(quantity)`. A transfer = two rows.
- `item.location_id` is a cache. The movements are the source of truth.
- Enum-like columns are `varchar` validated in the app (SQLite has no enums).
- `attribute_definition.key` is immutable once created.
- `article.barcode` holds the NFC tag ID. A scan looks up the article by `barcode`. Always soft-delete with `Article.softDeleted(now)`: it clears `barcode` so the tag can be reused.
- Room entities: `data/model`, database + converters: `data/db`. Quantities are `Long` thousandths, never `Double`.
- Any entity change = a new DB version: follow `.claude/rules/database.md`.

## UI
- Pages: `enums/Page.kt` (Stock, Scan, Settings). Navbar in `composables/NavBarComposable.kt`.
- Every user-facing string goes in `composeResources/values*/strings.xml`, in en, fr and de.
- Icons: Material Icons vector drawables in `composeResources/drawable`.

## Commits
Conventional Commits, see `.claude/rules/commits.md`. Overrides the org `AB#<ticket>` format for this repo.
