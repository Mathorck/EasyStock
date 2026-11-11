package ch.mathorck.easystock.data.db

import androidx.room.Room
import androidx.room.execSQL
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder<AppDatabase>(context)
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun noSyncStateMeansNoRole() = runBlocking<Unit> {
        assertNull(db.sessionDao().observeRole().first())
    }

    @Test
    fun roleOfTheSyncStateRow() = runBlocking<Unit> {
        db.useWriterConnection {
            it.execSQL("INSERT INTO sync_state (id, server_url, user_id, role) VALUES (1, 'http://nas:8080', 'u1', 'viewer')")
        }
        assertEquals("viewer", db.sessionDao().observeRole().first())
    }

    @Test
    fun noSyncStateRowInStandalone() = runBlocking<Unit> {
        assertNull(db.sessionDao().observeSyncState().first())
    }

    @Test
    fun syncStateRow() = runBlocking<Unit> {
        val fp = "ab".repeat(32)
        db.useWriterConnection {
            it.execSQL(
                "INSERT INTO sync_state (id, server_url, cert_fingerprint, user_id, role) " +
                    "VALUES (1, 'https://nas:8443', '$fp', 'u1', 'admin')",
            )
        }
        val state = db.sessionDao().observeSyncState().first()!!
        assertEquals("https://nas:8443", state.serverUrl)
        assertEquals(fp, state.certFingerprint)
        assertEquals("admin", state.role)
    }
}
