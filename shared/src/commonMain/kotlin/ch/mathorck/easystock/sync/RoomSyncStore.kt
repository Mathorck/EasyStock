package ch.mathorck.easystock.sync

import ch.mathorck.easystock.data.db.AppDatabase
import ch.mathorck.easystock.data.model.ImageHash
import ch.mathorck.easystock.data.model.OutboxEntry
import ch.mathorck.easystock.data.model.PendingPush
import ch.mathorck.easystock.data.model.ServerUser
import ch.mathorck.easystock.data.model.SyncState
import ch.mathorck.easystock.images.isValidImagePath
import kotlinx.serialization.json.JsonObject

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

/** [SyncStore] on the Room database (data/db/SyncDao) and the photo files. */
class RoomSyncStore(private val db: AppDatabase, private val files: SyncImageFiles) : SyncStore {
    private val dao = db.syncDao()

    override suspend fun syncState(): SyncState? = dao.syncState()

    override suspend fun updateRole(role: String) = dao.updateRole(role)

    override suspend fun upsertUser(user: ServerUser) = dao.upsertUser(user)

    override suspend fun pendingPush(): PendingPush? = dao.pendingPush()

    override suspend fun savePendingPush(push: PendingPush) = dao.savePendingPush(push)

    override suspend fun clearPendingPush() = dao.clearPendingPush()

    override suspend fun outbox(limit: Int): List<OutboxEntry> = dao.outbox(limit)

    override suspend fun wireRow(table: SyncTable, id: String, hashNewImages: Boolean): JsonObject? {
        if (hashNewImages && table == SyncTable.ARTICLE) {
            val path = dao.article(id)?.imagePath
            if (path != null && isValidImagePath(path) && dao.hashOf(path) == null) {
                files.read(path)?.let { dao.saveImageHash(ImageHash(path, sha256Hex(it))) }
            }
        }
        return dao.wireRow(table, id)
    }

    override suspend fun imageBytes(sha256: String): ByteArray? =
        dao.pathsOf(sha256).firstNotNullOfOrNull { path -> if (isValidImagePath(path)) files.read(path) else null }

    override suspend fun applyPush(sent: PushRequest, answer: PushAnswer, now: Long): List<PushDecision> =
        dao.applyPush(sent, answer, now)

    override suspend fun existingIds(table: SyncTable, ids: Set<String>): Set<String> = dao.existingIds(table, ids)

    override suspend fun applyPull(buffer: PullBuffer, next: Long, fullPull: Boolean, now: Long): Boolean =
        dao.applyPull(buffer, next, fullPull, now)

    override suspend fun wipeServerData(): Boolean {
        if (!db.backupDao().wipeServerData()) return false
        // After the rows: an article never points at a deleted photo
        files.deleteAll()
        return true
    }

    override suspend fun missingImages(): List<ImageHash> =
        dao.articleImages().filter { isValidImagePath(it.imagePath) && !files.exists(it.imagePath) }

    override suspend fun saveImage(image: ImageHash, jpeg: ByteArray) {
        // Left the server meanwhile, or the article got another photo: nothing to store
        if (dao.syncState() == null || dao.hashOf(image.imagePath) != image.sha256) return
        if (isValidImagePath(image.imagePath)) files.write(image.imagePath, jpeg)
    }

    override suspend fun removeOrphanPhotos() {
        if (dao.syncState() == null) return
        val orphans = orphanedPhotos(dao.allImageHashes(), dao.referencedImagePaths().toSet(), dao.pendingPush()?.body)
        for (photo in orphans) {
            // File first: if this stops in between, the row stays and the next cycle finishes
            files.delete(photo.imagePath)
            dao.deleteImageHash(photo.imagePath)
        }
    }
}
