package ch.mathorck.easystock.e2e

import ch.mathorck.easystock.auth.Role
import ch.mathorck.easystock.auth.storedRoleOf
import ch.mathorck.easystock.data.model.Category
import ch.mathorck.easystock.server.ApiError
import ch.mathorck.easystock.server.ApiException
import ch.mathorck.easystock.sync.KtorSyncApi
import ch.mathorck.easystock.sync.PushRequest
import ch.mathorck.easystock.sync.SyncTable
import ch.mathorck.easystock.sync.encodePush
import ch.mathorck.easystock.sync.toWire
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** Scenario 3: invites and roles. */
class RolesE2eTest {
    @BeforeTest
    fun server() = E2eServer.assume()

    @Test
    fun invitedMemberLogsInAndPushes() = runBlocking {
        val fixture = E2eServer.fixture
        val invited = fixture.invite(Role.EDITOR)
        assertEquals(Role.EDITOR.dbValue, storedRoleOf(invited.user.role))
        val member = fixture.login(invited)
        assertEquals(invited.user.id, member.user.id)

        Phone("member", member).use { phone ->
            phone.join()
            val category = phone.createCategory(E2eServer.unique("member"))
            phone.syncOk()
            assertEquals(0, phone.outboxSize())
            Phone("reader", fixture.viewer).use { other ->
                other.join().syncOk()
                assertTrue(other.snapshot().categories.any { it.id == category.id && it.name == category.name })
            }
        }
    }

    @Test
    fun viewerCannotPush() = runBlocking {
        val fixture = E2eServer.fixture
        Phone("viewer", fixture.viewer).use { phone ->
            phone.join()
            // The app keeps a Viewer's edits local: the cycle pulls only
            val category = phone.createCategory(E2eServer.unique("viewer"))
            phone.calls.clear()
            phone.syncOk()
            assertTrue("push" !in phone.calls)
            assertEquals(1, phone.outboxSize())

            // And the server refuses a push of the Viewer
            val row = Category(newId(), E2eServer.unique("forbidden"), createdAt = now(), updatedAt = now())
            val body = encodePush(PushRequest(newId(), mapOf(SyncTable.CATEGORY.tableName to listOf(toWire(SyncTable.CATEGORY, row)))))
            val error = fixture.viewer.client().use { client -> assertFailsWith<ApiException> { KtorSyncApi(client).push(body) } }
            assertEquals(ApiError.FORBIDDEN, error.error)

            Phone("check", fixture.alice).use { other ->
                other.join().syncOk()
                val names = other.snapshot().categories.map { it.name }
                assertTrue(category.name !in names && row.name !in names)
            }
        }
    }
}
