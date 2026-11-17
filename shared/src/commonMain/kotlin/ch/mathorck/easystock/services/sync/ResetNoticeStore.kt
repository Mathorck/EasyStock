package ch.mathorck.easystock.services.sync

/**
 * Keeps the [DatasetResetNotice] across an app restart (a reset in a background cycle is seen later),
 * until the user dismisses it. Outside the database (no schema change).
 */
interface ResetNoticeStore {
    suspend fun load(): String?

    /** Null forgets it. */
    suspend fun save(text: String?)
}
