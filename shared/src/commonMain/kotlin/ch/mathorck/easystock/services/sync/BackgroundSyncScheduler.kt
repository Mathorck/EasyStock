package ch.mathorck.easystock.services.sync

/** The platform job that runs one sync cycle every 15 minutes while connected. */
interface BackgroundSyncScheduler {
    fun schedule()
    fun cancel()
}
