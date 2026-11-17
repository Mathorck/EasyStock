package ch.mathorck.easystock.viewmodels.settings

import ch.mathorck.easystock.data.model.sync.SyncState

/** The `sync_state` row once read: null while the database has not answered yet. */
internal class SyncLoad(val state: SyncState?)
