package ch.mathorck.easystock.e2e

import ch.mathorck.easystock.data.dto.server.LoginRequest
import ch.mathorck.easystock.data.dto.server.RedeemInviteRequest
import ch.mathorck.easystock.data.dto.server.SetupRequest
import ch.mathorck.easystock.enums.auth.Role
import ch.mathorck.easystock.enums.server.JoinKind
import ch.mathorck.easystock.services.server.JoinCode
import ch.mathorck.easystock.services.server.KtorAdminApi

class Fixture private constructor(val admin: Session, val alice: Session, val bob: Session, val viewer: Session) {
    /** Admin makes an invite, the app reads its QR code and redeems it: a new account and its first session. */
    suspend fun invite(role: Role, name: String = E2eServer.unique(role.dbValue)): Session {
        val invite = admin.client().use { KtorAdminApi(it).createInvite(role) }
        val qr = checkNotNull(invite.grant.qrText(E2eServer.connection, JoinKind.INVITE)) { "Invite without QR code" }
        val code = JoinCode.parse(qr)
        check(code.kind == JoinKind.INVITE)
        val password = "pw-$name-0123456789"
        val answer = E2eServer.joinApi().use { api ->
            E2eServer.authCall { api.redeemInvite(RedeemInviteRequest(code.token, name, "Name $name", password, "e2e $name")) }
        }
        return Session(checkNotNull(answer.user), name, password, answer)
    }

    /** A new device session of an existing account. */
    suspend fun login(of: Session, device: String = "e2e login"): Session {
        val answer = E2eServer.joinApi().use { api ->
            E2eServer.authCall { api.login(LoginRequest(of.username, of.password, device)) }
        }
        return Session(checkNotNull(answer.user), of.username, of.password, answer)
    }

    companion object {
        private const val ADMIN_PASSWORD = "admin-password-0123456789"

        /**
         * Sets the server up, or logs in as its Admin when an earlier run did (to run a few tests again on the
         * same server). The other accounts are new for every run.
         */
        suspend fun create(): Fixture {
            val info = E2eServer.joinApi().use { it.serverInfo() }
            val answer = E2eServer.joinApi().use { api ->
                if (info.setupDone) {
                    E2eServer.authCall { api.login(LoginRequest("admin", ADMIN_PASSWORD, "e2e admin")) }
                } else {
                    val token = checkNotNull(
                        System.getProperty("easystock.e2e.setupToken") ?: System.getenv("EASYSTOCK_E2E_SETUP_TOKEN"),
                    ) { "No setup token (-Peasystock.e2e.setupToken)" }
                    E2eServer.authCall { api.setup(SetupRequest(token, "admin", "Admin", ADMIN_PASSWORD, "e2e admin")) }
                }
            }
            val admin = Session(checkNotNull(answer.user), "admin", ADMIN_PASSWORD, answer)
            val partial = Fixture(admin, admin, admin, admin)
            return Fixture(
                admin,
                partial.invite(Role.EDITOR, E2eServer.unique("alice")),
                partial.invite(Role.EDITOR, E2eServer.unique("bob")),
                partial.invite(Role.VIEWER, E2eServer.unique("victor")),
            )
        }
    }
}
