package ch.mathorck.easystock.auth

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import ch.mathorck.easystock.backup.BackupData
import ch.mathorck.easystock.backup.DeviceFixtures.PHOTO
import ch.mathorck.easystock.backup.DeviceFixtures.article
import ch.mathorck.easystock.backup.DeviceFixtures.sample
import ch.mathorck.easystock.data.db.AppDatabase
import ch.mathorck.easystock.server.ServerConnection
import ch.mathorck.easystock.server.SessionUser
import ch.mathorck.easystock.server.TokenAnswer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The empty-phone rule and the join, on an in-memory database and a temporary folder. */
class ServerJoinTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val filesDir = File(context.cacheDir, "join-test-${System.nanoTime()}")
    private lateinit var db: AppDatabase
    private lateinit var check: EmptyPhoneCheck
    private val session = InMemorySessionStore()
    private val connection = ServerConnection("https://nas.example:8443", null)
    private val answer = TokenAnswer(
        accessToken = "access",
        refreshToken = "refresh",
        user = SessionUser(id = "u1", displayName = "Ann", role = "member", kind = "human"),
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context).setDriver(BundledSQLiteDriver()).build()
        check = EmptyPhoneCheck(db.sessionDao()) { hasPhotoFiles(filesDir) }
    }

    @After
    fun tearDown() {
        db.close()
        filesDir.deleteRecursively()
    }

    @Test
    fun aFreshPhoneIsEmpty() = runBlocking<Unit> {
        assertTrue(check.isEmpty())
    }

    @Test
    fun softDeletedRowsCount() = runBlocking<Unit> {
        val deleted = article("a1").copy(deletedAt = 1L)
        db.backupDao().replaceAll(BackupData(articles = listOf(deleted)))
        assertFalse(check.isEmpty())
    }

    @Test
    fun everySyncedTableCounts() = runBlocking<Unit> {
        db.backupDao().replaceAll(sample())
        assertTrue(db.sessionDao().syncedRowCount() > 0)
        assertFalse(check.isEmpty())
    }

    @Test
    fun aPhotoAloneCounts() = runBlocking<Unit> {
        File(filesDir, PHOTO).apply { parentFile!!.mkdirs(); writeText("x") }
        assertFalse(check.isEmpty())
    }

    @Test
    fun anEmptyPhotoFolderIsEmpty() = runBlocking<Unit> {
        File(filesDir, "images/sub").mkdirs()
        assertTrue(check.isEmpty())
    }

    @Test
    fun theJoinWritesTheServerAndTheTokens() = runBlocking<Unit> {
        assertTrue(ServerJoin(db.sessionDao(), session).commit(connection, answer))

        val state = db.sessionDao().syncState()!!
        assertEquals("https://nas.example:8443", state.serverUrl)
        assertEquals("u1", state.userId)
        // The server's "member" is stored as the app's editor
        assertEquals("editor", state.role)
        assertEquals(0L, state.since)
        assertFalse(state.applying)
        assertEquals("refresh", session.refreshToken())
        assertEquals("access", session.accessToken)
    }

    @Test
    fun noJoinWhenRowsAppearedMeanwhile() = runBlocking<Unit> {
        db.backupDao().replaceAll(BackupData(articles = listOf(article("a1"))))

        assertFalse(ServerJoin(db.sessionDao(), session).commit(connection, answer))

        assertNull(db.sessionDao().syncState())
        assertNull(session.refreshToken())
        assertNull(session.accessToken)
    }

    @Test
    fun anOrphanTokenIsForgottenAtStart() = runBlocking<Unit> {
        session.saveRefreshToken("left over")
        ServerJoin(db.sessionDao(), session).forgetOrphanSession()
        assertNull(session.refreshToken())
    }
}
