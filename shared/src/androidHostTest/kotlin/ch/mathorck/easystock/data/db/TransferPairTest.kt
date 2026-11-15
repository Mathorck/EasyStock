package ch.mathorck.easystock.data.db

import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.RoomDatabase
import ch.mathorck.easystock.data.model.Article
import ch.mathorck.easystock.data.model.Location
import ch.mathorck.easystock.data.model.MovementReason
import ch.mathorck.easystock.data.model.StockMovement
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * A transfer is two rows with one occurredAt, on an in-memory database. The E2E helper once read the clock
 * per row, so the pair failed whenever the millisecond changed between the two reads.
 */
class TransferPairTest {
    private lateinit var db: AppDatabase
    private var movementCount = 0

    @BeforeTest
    fun setUp() = runBlocking {
        db = buildAppDatabase(
            // TRUNCATE: no WAL, so Room never asks the (absent) Android context for a service
            Room.inMemoryDatabaseBuilder<AppDatabase>(ContextWrapper(null)).setJournalMode(RoomDatabase.JournalMode.TRUNCATE),
        )
        db.stockDao().insertLocation(Location(id = "shelf", name = "Shelf", createdAt = 0, updatedAt = 0))
        db.stockDao().insertLocation(Location(id = "bin", name = "Bin", createdAt = 0, updatedAt = 0))
        db.articleDao().insert(Article(id = "screws", name = "Screws", createdAt = 0, updatedAt = 0))
        db.stockActionDao().adjust(movement("shelf", 10_000, MovementReason.PURCHASE, at = 1))
    }

    @AfterTest
    fun tearDown() = db.close()

    private fun movement(location: String, quantityMilli: Long, reason: MovementReason, at: Long) = StockMovement(
        id = "m${movementCount++}",
        articleId = "screws",
        locationId = location,
        quantityMilli = quantityMilli,
        reason = reason,
        occurredAt = at,
        createdAt = at,
    )

    private suspend fun transferRows() = db.backupDao().snapshot().movements.filter { it.reason == MovementReason.TRANSFER }

    @Test
    fun pairWithOneTimestampIsSaved() = runBlocking {
        db.stockActionDao().transfer(
            out = movement("shelf", -4_000, MovementReason.TRANSFER, at = 1_000),
            into = movement("bin", 4_000, MovementReason.TRANSFER, at = 1_000),
        )

        val rows = transferRows()
        assertEquals(2, rows.size)
        assertEquals(setOf(1_000L), rows.map { it.occurredAt }.toSet())
        assertEquals(6_000, db.stockActionDao().stockAt("screws", "shelf"))
        assertEquals(4_000, db.stockActionDao().stockAt("screws", "bin"))
    }

    @Test
    fun pairOneMillisecondApartIsRejectedAndSavesNothing() = runBlocking {
        val error = assertFailsWith<IllegalArgumentException> {
            db.stockActionDao().transfer(
                out = movement("shelf", -4_000, MovementReason.TRANSFER, at = 1_000),
                into = movement("bin", 4_000, MovementReason.TRANSFER, at = 1_001),
            )
        }

        assertEquals("Both rows must have the same occurredAt", error.message)
        assertEquals(emptyList(), transferRows())
        assertEquals(10_000, db.stockActionDao().stockAt("screws", "shelf"))
    }
}
