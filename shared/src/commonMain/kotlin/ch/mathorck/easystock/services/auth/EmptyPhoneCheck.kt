package ch.mathorck.easystock.services.auth

import ch.mathorck.easystock.data.dao.sync.SessionDao

/**
 * The empty-phone rule (docs/server-mode/02-app.md): a phone joins a server only when every synced table
 * is empty, soft-deleted rows included ([SessionDao.syncedRowCount]), and the image folder is empty.
 */
class EmptyPhoneCheck(
    private val syncedRowCount: suspend () -> Long,
    /** True when the photo folder holds at least one file. */
    private val hasPhotos: suspend () -> Boolean,
) {
    constructor(dao: SessionDao, hasPhotos: suspend () -> Boolean) : this(dao::syncedRowCount, hasPhotos)

    suspend fun isEmpty(): Boolean = syncedRowCount() == 0L && !hasPhotos()
}
