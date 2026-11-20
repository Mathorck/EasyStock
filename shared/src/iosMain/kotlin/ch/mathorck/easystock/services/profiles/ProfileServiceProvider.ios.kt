package ch.mathorck.easystock.services.profiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** iOS is not delivered: the legacy profile only, nothing stored or wiped. */
@Composable
actual fun rememberProfileService(): ProfileService = remember {
    ProfileService(
        manager = profileManager,
        store = object : ProfileRegistryStore {
            private var json: String? = null
            override fun load(): String? = json
            override fun save(json: String) {
                this.json = json
            }
        },
        platform = object : ProfilePlatform {
            override suspend fun wipe(paths: ProfilePaths) = Unit
            override fun stopBackgroundWork(paths: ProfilePaths) = Unit
            override fun restart() = Unit
        },
    )
}
