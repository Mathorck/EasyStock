package ch.mathorck.easystock.e2e

import ch.mathorck.easystock.auth.Role
import ch.mathorck.easystock.backup.BackupArchive
import ch.mathorck.easystock.server.ApiError
import ch.mathorck.easystock.server.ApiException
import ch.mathorck.easystock.server.ServerImportMode
import ch.mathorck.easystock.sync.SyncError
import ch.mathorck.easystock.sync.SyncResult
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.toByteArray
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** Scenarios 6, 7 and 8 (backup): the user list, disabling a user, backups and the dataset reset. */
class AdminE2eTest {
    @BeforeTest
    fun server() = E2eServer.assume()

    @Test
    fun userListAndDisabledUserLosesTheSession() = runBlocking {
        val fixture = E2eServer.fixture
        val victim = fixture.invite(Role.EDITOR)
        Phone("victim", victim).use { phone ->
            phone.join().syncOk()

            val users = fixture.admin.admin { it.users() }
            val alice = users.single { it.id == fixture.alice.user.id }
            assertEquals(fixture.alice.username, alice.username)
            assertFalse(alice.disabled)
            assertEquals(victim.username, users.single { it.id == victim.user.id }.username)

            val disabled = fixture.admin.admin { it.setDisabled(victim.user.id, true) }
            assertTrue(disabled.disabled)
            assertTrue(fixture.admin.admin { it.users() }.single { it.id == victim.user.id }.disabled)

            assertEquals(SyncResult.FAILED, phone.sync())
            assertEquals(SyncError.SESSION_REVOKED, phone.engine.status.value.error)
            assertNull(victim.store.refreshToken(), "tokens forgotten")
            val error = victim.client().use { client ->
                assertFailsWith<ApiException> { ch.mathorck.easystock.server.KtorAccountApi(client).me() }
            }
            assertEquals(ApiError.SESSION_REVOKED, error.error)
        }
    }

    /**
     * The admin exports the server backup (read with the app's backup code), then imports it again in
     * replace mode. The other phone sees the new data set from server-info before it pushes, drops its
     * unsent change and pulls everything again.
     */
    @Test
    fun backupRoundTripAndDatasetReset() = runBlocking {
        val fixture = E2eServer.fixture
        Phone("admin", fixture.admin).use { admin ->
            Phone("B", fixture.bob).use { b ->
                val tag = E2eServer.unique("reset")
                admin.join()
                val kept = admin.createCategory("Kept $tag")
                val shelf = admin.createLocation("Shelf $tag")
                val article = admin.createArticle("Glue $tag", kept.id)
                admin.stockIn(article.id, shelf.id, 3)
                admin.syncOk()
                b.join().syncOk()

                val bytes = fixture.admin.admin { api ->
                    var body = ByteArray(0)
                    api.downloadBackup { body = it.toByteArray() }
                    body
                }
                val file = File(admin.filesDir, "server-backup.zip").apply { writeBytes(bytes) }
                val archive = BackupArchive.read(file)
                assertEquals(admin.snapshot().comparable().copy(movements = emptyList()), archive.data.comparable().copy(movements = emptyList()))
                assertEquals(admin.snapshot().movements.map { it.id }.toSet(), archive.data.movements.map { it.id }.toSet())

                // B edits offline, then the admin replaces the data set
                val lost = b.createCategory("Lost $tag")
                assertEquals(1, b.outboxSize())
                fixture.admin.admin { it.importBackup(ServerImportMode.REPLACE, bytes.size.toLong()) { ByteReadChannel(bytes) } }

                b.calls.clear()
                b.syncOk()
                assertTrue("server-info" in b.calls, "calls: ${b.calls}")
                assertTrue("push" !in b.calls, "the reset is seen before any push: ${b.calls}")
                assertEquals(1, assertNotNull(b.engine.resetNotice.value).lostChanges)
                assertEquals(0, b.outboxSize())
                val after = b.snapshot()
                assertTrue(after.categories.none { it.id == lost.id })
                assertTrue(after.categories.any { it.id == kept.id })
                assertEquals(3, b.db.stockActionDao().stockAt(article.id, shelf.id) / 1000)

                // A fresh phone sees the same data set
                Phone("fresh", fixture.viewer).use { fresh ->
                    fresh.join().syncOk()
                    assertSameData(fresh.snapshot(), after)
                }
            }
        }
    }
}
