package ch.mathorck.easystock.services.profiles

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Volatile
private var service: ProfileService? = null

/**
 * Reads the stored profiles. Called by the Application before any activity, service or job, so
 * every entry point of the process opens the data of the active profile.
 */
fun installProfiles(context: Context) {
    profileService(context)
}

/** The service of the process; the first call installs the stored registry. */
fun profileService(context: Context): ProfileService = service ?: synchronized(ProfileService::class) {
    service ?: run {
        val store = AndroidProfileRegistryStore(context)
        ProfileService.install(profileManager, store)
        ProfileService(profileManager, store, AndroidProfilePlatform(context)).also { service = it }
    }
}

/** Folder the photo paths of the active profile are relative to (`filesDir` for the legacy profile). */
fun profileFilesDir(context: Context, paths: ProfilePaths = activeProfilePaths): File {
    val filesDir = context.applicationContext.filesDir
    return if (paths.filesRoot.isEmpty()) filesDir else File(filesDir, paths.filesRoot)
}

@Composable
actual fun rememberProfileService(): ProfileService {
    val context = LocalContext.current.applicationContext
    return remember(context) { profileService(context) }
}
