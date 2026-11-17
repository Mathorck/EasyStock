package ch.mathorck.easystock.services.server

/** A file in the app cache (`cache/server/share`), made for the user to save or share. */
data class LocalFile(val path: String, val name: String, val size: Long, val mimeType: String)
