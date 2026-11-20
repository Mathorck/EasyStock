package ch.mathorck.easystock.services.profiles

/** What a profile change needs from the platform. */
interface ProfilePlatform {
    /** Deletes every file, preference and job of a profile that is not in use. */
    suspend fun wipe(paths: ProfilePaths)

    /** Stops the background jobs and notifications of the profile being left. */
    fun stopBackgroundWork(paths: ProfilePaths)

    /** Starts the app again, so every service opens the data of the new active profile. */
    fun restart()
}
