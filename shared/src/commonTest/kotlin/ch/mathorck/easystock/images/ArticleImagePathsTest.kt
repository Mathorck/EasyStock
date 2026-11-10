package ch.mathorck.easystock.images

import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.viewmodels.toForm
import ch.mathorck.easystock.viewmodels.toNewArticle
import ch.mathorck.easystock.viewmodels.toUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ArticleImagePathsTest {
    private val id = Uuid.parse("0f8fad5b-d9cb-469f-a165-70867728950e")
    private val path = "images/0f8fad5b-d9cb-469f-a165-70867728950e.jpg"

    @Test
    fun newPathIsRelativeToTheImageFolder() {
        assertEquals(path, newImagePath(id))
        assertTrue(isValidImagePath(newImagePath()))
        assertNotEquals(newImagePath(), newImagePath())
    }

    @Test
    fun onlyGeneratedPathsAreValid() {
        assertTrue(isValidImagePath(path))
        listOf(
            "",
            "/data/data/app/files/$path",
            "images/../easystock.db",
            "../images/0f8fad5b-d9cb-469f-a165-70867728950e.jpg",
            "images/0f8fad5b-d9cb-469f-a165-70867728950e.jpg/..",
            "images/0f8fad5b-d9cb-469f-a165-70867728950e.png",
            "images/0F8FAD5B-D9CB-469F-A165-70867728950E.jpg",
            "other/0f8fad5b-d9cb-469f-a165-70867728950e.jpg",
            "images/sub/0f8fad5b-d9cb-469f-a165-70867728950e.jpg",
            "0f8fad5b-d9cb-469f-a165-70867728950e.jpg",
            "images/photo.jpg",
            "$path\n",
        ).forEach { assertFalse(isValidImagePath(it), it) }
    }

    @Test
    fun sampleSizeKeepsTheRequiredSide() {
        assertEquals(1, sampleSizeFor(1600, 1600))
        assertEquals(1, sampleSizeFor(1000, 1600))
        assertEquals(1, sampleSizeFor(3199, 1600))
        assertEquals(2, sampleSizeFor(3200, 1600))
        assertEquals(2, sampleSizeFor(4000, 1600))
        assertEquals(8, sampleSizeFor(1200, 120))
        assertEquals(1, sampleSizeFor(0, 100))
        assertEquals(1, sampleSizeFor(100, 0))
    }

    @Test
    fun scaledSizeFitsTheLongestSide() {
        assertEquals(1600 to 1200, scaledSize(4000, 3000, 1600))
        assertEquals(900 to 1600, scaledSize(1800, 3200, 1600))
        assertEquals(1600 to 1600, scaledSize(2000, 2000, 1600))
        // Never enlarged
        assertEquals(800 to 600, scaledSize(800, 600, 1600))
        // Very thin images keep at least one pixel
        assertEquals(1600 to 1, scaledSize(10_000, 1, 1600))
    }

    // ---------- Staged files ----------

    private val stored = "images/00000000-0000-4000-8000-000000000001.jpg"
    private val first = "images/00000000-0000-4000-8000-000000000002.jpg"
    private val second = "images/00000000-0000-4000-8000-000000000003.jpg"

    @Test
    fun cancelDeletesOnlyTheNewFiles() {
        assertEquals(setOf(first, second), (StagedImages(stored) + first + second).toDeleteOnCancel())
        assertEquals(emptySet(), StagedImages(stored).toDeleteOnCancel())
        assertEquals(setOf(first), (StagedImages() + first).toDeleteOnCancel())
    }

    @Test
    fun saveKeepsTheSavedFileAndDeletesTheReplacedOnes() {
        val images = StagedImages(stored) + first + second
        // The last photo is saved: the stored one and the first try go
        assertEquals(setOf(stored, first), images.toDeleteOnSave(saved = second))
        // Photo removed: everything goes
        assertEquals(setOf(stored, first, second), images.toDeleteOnSave(saved = null))
        // Photos taken then removed again, the stored one kept
        assertEquals(setOf(first, second), images.toDeleteOnSave(saved = stored))
    }

    @Test
    fun saveDeletesThePathStoredAtSaveTime() {
        val images = StagedImages(stored) + first
        // The database had another photo by then: that one is replaced, not the one seen on open
        assertEquals(setOf(second), images.toDeleteOnSave(saved = first, previous = second))
        assertEquals(emptySet(), StagedImages().toDeleteOnSave(saved = null, previous = null))
    }

    // ---------- Form ----------

    private val article = Article(id = "a1", name = "Drill", imagePath = stored, createdAt = 0, updatedAt = 0)

    @Test
    fun formKeepsThePhotoPath() {
        val form = article.toForm()
        assertEquals(stored, form.imagePath)
        assertEquals(stored, form.toUpdate(emptyList(), now = 1, newId = { "x" }).changes.imagePath)
        assertEquals(null, form.copy(imagePath = null).toUpdate(emptyList(), now = 1, newId = { "x" }).changes.imagePath)
        assertEquals(first, form.copy(imagePath = first).toNewArticle(emptyList(), now = 1, newId = { "x" }).article.imagePath)
    }
}
