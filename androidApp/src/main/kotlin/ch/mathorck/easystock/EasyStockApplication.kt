package ch.mathorck.easystock

import android.app.Application
import ch.mathorck.easystock.services.profiles.installProfiles

/** Reads the active profile before any activity, push service or background job opens its data. */
class EasyStockApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        installProfiles(this)
    }
}
