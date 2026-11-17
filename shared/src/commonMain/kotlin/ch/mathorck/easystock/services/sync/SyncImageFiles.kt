package ch.mathorck.easystock.services.sync

/** The article photo files, by `article.image_path`. */
interface SyncImageFiles {
    suspend fun read(path: String): ByteArray?

    suspend fun exists(path: String): Boolean

    /** Writes the whole file or nothing (temporary file, then rename). */
    suspend fun write(path: String, bytes: ByteArray)

    /** Deletes the file. Does nothing for an invalid path or a missing file. */
    suspend fun delete(path: String)

    /** Deletes every article photo (the server replaced its data set). */
    suspend fun deleteAll()
}
