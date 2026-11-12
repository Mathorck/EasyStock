package ch.mathorck.easystock.e2e

import ch.mathorck.easystock.data.model.Tracking
import ch.mathorck.easystock.sync.RowConflict
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Scenarios 4 and 5: two phones share their edits, and the first change wins. */
class SyncE2eTest {
    @BeforeTest
    fun server() = E2eServer.assume()

    @Test
    fun twoPhonesShareTheirEdits() = runBlocking {
        val fixture = E2eServer.fixture
        Phone("A", fixture.alice).use { a ->
            Phone("B", fixture.bob).use { b ->
                a.join().syncOk()
                b.join().syncOk()

                val tag = E2eServer.unique("sync")
                val tools = a.createCategory("Tools $tag")
                val room = a.createLocation("Room $tag")
                val shelf = a.createLocation("Shelf", room.id)
                val bin = a.createLocation("Bin", shelf.id)
                val other = a.createLocation("Other $tag")
                val screws = a.createArticle("Screws $tag", tools.id)
                a.stockIn(screws.id, shelf.id, 10)
                a.transfer(screws.id, shelf.id, bin.id, 4)
                val drill = a.createArticle("Drill $tag", tools.id, Tracking.SERIAL)
                a.addUnit(drill.id, room.id, "SN-$tag")
                assertTrue(a.outboxSize() > 0)

                a.syncOk()
                assertEquals(0, a.outboxSize())
                b.syncOk()
                assertSameData(a.snapshot(), b.snapshot())
                assertEquals(6, b.db.stockActionDao().stockAt(screws.id, shelf.id) / 1000)
                // Both phones see who recorded the movements, the author's own phone included
                assertEquals(setOf<String?>(fixture.alice.user.id), a.snapshot().movements.filter { it.articleId == screws.id }.map { it.createdBy }.toSet())
                val authors = b.snapshot().movements.filter { it.articleId == screws.id }.map { it.createdBy }.toSet()
                assertEquals(setOf<String?>(fixture.alice.user.id), authors)

                // B moves the shelf to another room and deletes the empty location, A gets both
                b.db.locationDao().updateLocation(shelf.id, shelf.name, other.id, now())
                val empty = b.createLocation("Empty", other.id)
                b.syncOk()
                b.db.locationDao().softDeleteLocation(empty.id, now())
                b.db.articleDao().softDelete(drill.id, now()).let { assertTrue(it) }
                b.syncOk()
                a.syncOk()
                val seen = a.snapshot()
                assertEquals(other.id, seen.locations.single { it.id == shelf.id }.parentId)
                assertNotNull(seen.locations.single { it.id == empty.id }.deletedAt)
                assertNotNull(seen.articles.single { it.id == drill.id }.deletedAt)
                assertSameData(a.snapshot(), b.snapshot())
            }
        }
    }

    @Test
    fun firstChangeWinsAndTheLoserKeepsTheServerVersion() = runBlocking {
        val fixture = E2eServer.fixture
        Phone("A", fixture.alice).use { a ->
            Phone("B", fixture.bob).use { b ->
                a.join().syncOk()
                val tag = E2eServer.unique("conflict")
                val category = a.createCategory("Base $tag")
                a.syncOk()
                b.join().syncOk()

                // Both edit the same version
                a.db.categoryDao().rename(category.id, "From A $tag", now())
                b.db.categoryDao().rename(category.id, "From B $tag", now())
                a.syncOk()
                b.syncOk()

                // "Keep theirs": B now has the server row, and a rejected change says why
                val name = b.snapshot().categories.single { it.id == category.id }.name
                assertEquals("From A $tag", name)
                assertEquals(0, b.outboxSize())
                val rejected = b.db.syncDao().observeRejectedChanges().first().single { it.rowId == category.id }
                assertTrue(rejected.reason.startsWith("conflict:"), rejected.reason)
                assertNotNull(RowConflict.payloadOf(rejected.reason))
                assertSameData(a.snapshot(), b.snapshot())
            }
        }
    }
}
