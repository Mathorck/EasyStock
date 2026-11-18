package ch.mathorck.easystock.services.profiles

/**
 * Every file, database, preference and job name of one profile. [LEGACY] holds the exact names used
 * before profiles existed: an existing install must keep opening the same data.
 */
class ProfilePaths private constructor(
    val databaseName: String,
    /** File of the session, in `filesDir/datastore`. */
    val sessionFile: String,
    val appPrefsName: String,
    val localNetworkPrefsName: String,
    val resetNoticePrefsName: String,
    val datasetEpochPrefsName: String,
    /** File of the push address, in `noBackupFilesDir`. */
    val pushFile: String,
    /** Folder of the automatic backups, in `filesDir`. */
    val autoBackupDir: String,
    val backgroundSyncWork: String,
    val pushSyncWork: String,
    val dateAlertWork: String,
) {
    companion object {
        val LEGACY = ProfilePaths(
            databaseName = "easystock.db",
            sessionFile = "session.preferences_pb",
            appPrefsName = "easystock_prefs",
            localNetworkPrefsName = "local_network",
            resetNoticePrefsName = "sync_reset_notice",
            datasetEpochPrefsName = "sync_dataset",
            pushFile = "unifiedpush.json",
            autoBackupDir = "auto-backups",
            backgroundSyncWork = "easystock-background-sync",
            pushSyncWork = "easystock-push-sync",
            dateAlertWork = "easystock-date-alerts",
        )
    }
}
