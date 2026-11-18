package ch.mathorck.easystock.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import ch.mathorck.easystock.services.profiles.activeProfilePaths

fun databaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath(activeProfilePaths.databaseName)
    return Room.databaseBuilder<AppDatabase>(context = appContext, name = dbFile.absolutePath)
}
