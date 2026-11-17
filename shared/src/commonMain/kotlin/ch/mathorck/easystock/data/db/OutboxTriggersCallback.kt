package ch.mathorck.easystock.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection

/** Creates the outbox triggers on a fresh install, and on every open (a no-op when they exist). */
object OutboxTriggersCallback : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) = OutboxTriggers.create(connection)

    override fun onOpen(connection: SQLiteConnection) = OutboxTriggers.create(connection)
}
