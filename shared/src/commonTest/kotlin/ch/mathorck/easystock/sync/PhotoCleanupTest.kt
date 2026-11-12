package ch.mathorck.easystock.sync

import ch.mathorck.easystock.data.model.ImageHash
import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoCleanupTest {
    private val a = "images/0f8fad5b-d9cb-469f-a165-70867728950e.jpg"
    private val b = "images/1f8fad5b-d9cb-469f-a165-70867728950e.jpg"
    private val sha = "a".repeat(64)
    private val other = "b".repeat(64)

    @Test
    fun aReferencedPhotoIsKept() {
        assertEquals(emptyList(), orphanedPhotos(listOf(ImageHash(a, sha)), setOf(a), null))
    }

    @Test
    fun aReplacedPhotoIsOrphaned() {
        val recorded = listOf(ImageHash(a, sha), ImageHash(b, other))
        assertEquals(listOf(ImageHash(a, sha)), orphanedPhotos(recorded, setOf(b), null))
    }

    @Test
    fun aPhotoOfThePendingBatchIsKept() {
        val body = """{"rows":{"article":[{"id":"x","image_sha256":"$sha"}]}}"""
        assertEquals(emptyList(), orphanedPhotos(listOf(ImageHash(a, sha)), emptySet(), body))
        assertEquals(listOf(ImageHash(a, other)), orphanedPhotos(listOf(ImageHash(a, other)), emptySet(), body))
    }

    @Test
    fun anInvalidPathIsNeverReturned() {
        val recorded = listOf(ImageHash("../secret.jpg", sha), ImageHash("/etc/passwd", sha), ImageHash("images/x.jpg", sha))
        assertEquals(emptyList(), orphanedPhotos(recorded, emptySet(), null))
    }
}
