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
    /**
     * Folder in `filesDir` the photo paths of the database are relative to (`images/<uuid>.jpg`).
     * Empty for [LEGACY]: `filesDir` itself, which also holds the folders of the other profiles.
     */
    val filesRoot: String,
    /** UnifiedPush instance: each profile has its own push address. */
    val pushInstance: String,
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
            filesRoot = "",
            // The connector's default instance, used before profiles existed
            pushInstance = "default",
        )

        /** Folder of the profiles other than [LEGACY], in `filesDir`. */
        const val PROFILES_DIR = "profiles"

        private val ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

        /** True for an id [forProfile] accepts: a lowercase UUID, so a name never leaves its folder. */
        fun isValidId(id: String): Boolean = ID.matches(id)

        /** The names of an [ch.mathorck.easystock.enums.profiles.ProfileLayout.ISOLATED] profile. */
        fun forProfile(id: String): ProfilePaths {
            require(isValidId(id)) { "Invalid profile id" }
            val root = "$PROFILES_DIR/$id"
            return ProfilePaths(
                databaseName = "easystock-$id.db",
                sessionFile = "session-$id.preferences_pb",
                appPrefsName = "easystock_prefs_$id",
                localNetworkPrefsName = "local_network_$id",
                resetNoticePrefsName = "sync_reset_notice_$id",
                datasetEpochPrefsName = "sync_dataset_$id",
                pushFile = "unifiedpush-$id.json",
                autoBackupDir = "$root/auto-backups",
                backgroundSyncWork = "easystock-background-sync-$id",
                pushSyncWork = "easystock-push-sync-$id",
                dateAlertWork = "easystock-date-alerts-$id",
                filesRoot = root,
                pushInstance = id,
            )
        }
    }
}
