package ch.mathorck.easystock.sync

import ch.mathorck.easystock.data.model.ImageHash
import ch.mathorck.easystock.images.isValidImagePath

/**
 * The recorded photos (`image_hash`) whose file can go: no article row references the path any more
 * (a replaced or removed photo), and the hash is not in the batch still waiting to be sent
 * ([pendingBody], whose photo upload reads the file by hash).
 *
 * A path counts as referenced for every article row, soft-deleted ones included. An invalid path is
 * never returned. When in doubt the photo is kept.
 */
fun orphanedPhotos(recorded: List<ImageHash>, referencedPaths: Set<String>, pendingBody: String?): List<ImageHash> =
    recorded.filter { photo ->
        isValidImagePath(photo.imagePath) &&
            photo.imagePath !in referencedPaths &&
            (pendingBody == null || !pendingBody.contains(photo.sha256))
    }
