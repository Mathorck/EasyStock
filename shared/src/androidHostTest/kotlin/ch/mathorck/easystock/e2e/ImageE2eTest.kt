package ch.mathorck.easystock.e2e

import ch.mathorck.easystock.images.newImagePath
import ch.mathorck.easystock.sync.KtorSyncApi
import ch.mathorck.easystock.sync.sha256Hex
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** Scenario 8: an article photo goes up with the push and comes down on the other phone. */
class ImageE2eTest {
    @BeforeTest
    fun server() = E2eServer.assume()

    @Test
    fun photoIsUploadedAndDownloaded() = runBlocking {
        val fixture = E2eServer.fixture
        Phone("A", fixture.alice).use { a ->
            Phone("B", fixture.bob).use { b ->
                a.join()
                val jpeg = jpeg()
                val path = newImagePath()
                File(a.filesDir, path).apply { parentFile.mkdirs() }.writeBytes(jpeg)
                val article = a.createArticle(E2eServer.unique("Photo"), imagePath = path)
                a.syncOk()
                assertTrue(fixture.alice.client().use { KtorSyncApi(it).hasImage(sha256Hex(jpeg)) })

                b.join().syncOk()
                val local = assertNotNull(b.snapshot().articles.single { it.id == article.id }.imagePath)
                assertContentEquals(jpeg, File(b.filesDir, local).readBytes())
            }
        }
    }

    private fun jpeg(): ByteArray {
        val image = BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            color = Color(Math.random().times(0xFFFFFF).toInt())
            fillRect(0, 0, 64, 48)
            dispose()
        }
        return ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
    }
}
