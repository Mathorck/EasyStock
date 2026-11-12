package ch.mathorck.easystock.e2e

import ch.mathorck.easystock.auth.Role
import ch.mathorck.easystock.server.ApiError
import ch.mathorck.easystock.server.ApiException
import ch.mathorck.easystock.server.KtorAccountApi
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Account edits: password, own display name, rename by an Admin. */
class AccountE2eTest {
    @BeforeTest
    fun server() = E2eServer.assume()

    @Test
    fun changePasswordEndsTheOtherSessions() = runBlocking {
        val fixture = E2eServer.fixture
        val first = fixture.invite(Role.EDITOR)
        val second = fixture.login(first, "e2e second phone")
        val newPassword = "new-${first.password}"

        first.account { it.changePassword(first.password, newPassword) }
        first.password = newPassword

        // This session stays, also after a token refresh
        first.store.accessToken = "expired"
        assertEquals(first.user.id, first.account { it.me() }.id)
        // The other one is logged out
        val error = second.client().use { client -> assertFailsWith<ApiException> { KtorAccountApi(client).me() } }
        assertEquals(ApiError.SESSION_REVOKED, error.error)
        // The new password logs in
        assertEquals(first.user.id, fixture.login(first, "e2e new password").user.id)
    }

    @Test
    fun displayNameEditsAreSeenEverywhere() = runBlocking {
        val fixture = E2eServer.fixture
        val own = E2eServer.unique("Alice")
        assertEquals(own, fixture.alice.account { it.setDisplayName(own) }.displayName)
        assertEquals(own, fixture.admin.admin { it.users() }.single { it.id == fixture.alice.user.id }.displayName)

        // The Admin renames Bob: Bob's account and the phones see the new name
        val renamed = E2eServer.unique("Bob")
        assertEquals(renamed, fixture.admin.admin { it.rename(fixture.bob.user.id, renamed) }.displayName)
        assertEquals(renamed, fixture.bob.account { it.me() }.displayName)
        Phone("bob", fixture.bob).use { phone ->
            phone.join().syncOk()
            assertEquals(renamed, phone.db.sessionDao().observeDisplayName(fixture.bob.user.id).first())
        }
    }
}
