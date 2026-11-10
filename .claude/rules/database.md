# Database version changes (Room)

Do all of this for every `AppDatabase.version` change, in this order:

1. Change `docs/schema.dbml` first, then the Room entity.
2. Bump `AppDatabase.version` by exactly 1.
3. Add a migration for the new version, with no gap in the chain. Use `AutoMigration` by default, an `AutoMigrationSpec` for renames and deletes, a manual `Migration` when needed (data changes).
4. Never use `fallbackToDestructiveMigration`.
5. Commit the new exported schema JSON in `shared/schemas`. Never edit the JSON of a released version.
6. Add a row to `docs/db-versions.md`.
7. `MigrationTest` reads the schemas folder: check that the new version passes (step and chain to the latest). Add a data test when a migration moves or rewrites data.
8. The backup/transfer format must accept the new version and still import older backups.
9. Data migrations keep the CLAUDE.md rules: never hard-delete rows (soft delete), never update or delete `stock_movement` rows.
