package ch.mathorck.easystock.data.db

import androidx.room.RoomDatabaseConstructor

// Implemented by the Room compiler on each platform
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
