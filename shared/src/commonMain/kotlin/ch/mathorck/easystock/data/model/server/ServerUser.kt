package ch.mathorck.easystock.data.model.server

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A user of the server, to show who made each movement. */
@Entity(tableName = "server_user")
data class ServerUser(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "display_name") val displayName: String,
)
