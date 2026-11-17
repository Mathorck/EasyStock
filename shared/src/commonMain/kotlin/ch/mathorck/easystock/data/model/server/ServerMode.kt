package ch.mathorck.easystock.data.model.server

// Server mode tables (schema v9). App only: never synced, never in a backup, empty in standalone mode.
// See docs/schema.dbml and docs/server-mode/02-app.md.

/** Id of the single [SyncState] row. */
const val SYNC_STATE_ID = 1

/** Id of the single [PendingPush] row. */
const val PENDING_PUSH_ID = 1
