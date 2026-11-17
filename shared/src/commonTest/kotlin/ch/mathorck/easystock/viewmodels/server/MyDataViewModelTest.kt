package ch.mathorck.easystock.viewmodels.server

import ch.mathorck.easystock.enums.server.ApiError
import ch.mathorck.easystock.exceptions.server.ApiException
import ch.mathorck.easystock.services.server.FakeMyDataApi
import ch.mathorck.easystock.services.server.RecordingFileStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MyDataViewModelTest {
    private val api = FakeMyDataApi()
    private val files = RecordingFileStore()
    private val dispatcher = UnconfinedTestDispatcher()

    private fun vm() = MyDataViewModel(api, files, fileName = { "easystock-my-data-2026-11-14.json" }, cleanupScope = TestScope(dispatcher))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun theSummaryComesBeforeAnyDownload() {
        val vm = vm()
        vm.download()
        assertEquals(0, api.calls)
        vm.open()
        assertTrue(vm.state.value.summary)
        assertEquals(0, api.calls)
    }

    @Test
    fun theExportIsStreamedToAJsonFileInTheCache() {
        val vm = vm()
        vm.open()
        vm.download()
        val got = files.received.single()
        assertEquals("easystock-my-data-2026-11-14.json", got.name)
        assertEquals("application/json", got.mimeType)
        assertEquals(api.body, got.content)
        val state = vm.state.value
        assertFalse(state.downloading)
        assertEquals("/cache/server/share/easystock-my-data-2026-11-14.json", state.downloaded?.path)
        assertNull(state.error)
    }

    @Test
    fun aDownloadIsNotStartedTwice() {
        api.gate = CompletableDeferred()
        val vm = vm()
        vm.open()
        vm.download()
        assertTrue(vm.state.value.downloading)
        vm.download()
        vm.close()
        assertTrue(vm.state.value.downloading, "closing waits for the download")
        api.gate!!.complete(Unit)
        assertEquals(1, api.calls)
        assertEquals(1, files.received.size)
    }

    @Test
    fun savedTheCacheCopyIsDropped() {
        val vm = vm()
        vm.open()
        vm.download()
        vm.saved()
        assertEquals(1, files.clears)
        assertTrue(vm.state.value.saved)
        assertNull(vm.state.value.downloaded)
    }

    @Test
    fun closedTheCacheCopyIsDropped() {
        val vm = vm()
        vm.open()
        vm.download()
        vm.close()
        assertEquals(1, files.clears)
        assertFalse(vm.state.value.summary)
    }

    @Test
    fun closedWithoutAFileNothingToClear() {
        val vm = vm()
        vm.open()
        vm.close()
        assertEquals(0, files.clears)
    }

    @Test
    fun theScreenGoneTheCacheCopyIsDropped() {
        val vm = vm()
        vm.open()
        vm.download()
        // A share hands the file to another app: the copy stays until the screen goes
        val store = androidx.lifecycle.ViewModelStore()
        store.put("my-data", vm)
        store.clear()
        assertEquals(1, files.clears)
    }

    @Test
    fun tooManyExportsIsRateLimitedAndNothingIsKept() {
        api.error = ApiException(ApiError.RATE_LIMITED, 429, "rate-limited")
        val vm = vm()
        vm.open()
        vm.download()
        assertEquals(MyDataError.RATE_LIMITED, vm.state.value.error)
        assertNull(vm.state.value.downloaded)
        assertFalse(vm.state.value.downloading)
        assertEquals(1, files.clears)
        // The user can try again later from the same dialog
        assertTrue(vm.state.value.summary)
    }

    @Test
    fun offlineIsANetworkError() {
        api.error = ApiException(ApiError.NETWORK)
        val vm = vm()
        vm.open()
        vm.download()
        assertEquals(MyDataError.NETWORK, vm.state.value.error)
        vm.consumeError()
        assertNull(vm.state.value.error)
    }

    @Test
    fun aFileThatCannotBeWrittenIsAFileErrorAndIsDropped() {
        files.receiveError = IOException("disk full")
        val vm = vm()
        vm.open()
        vm.download()
        assertEquals(MyDataError.FILE, vm.state.value.error)
        assertEquals(1, files.clears)
    }

    @Test
    fun errorMapping() {
        assertEquals(MyDataError.RATE_LIMITED, myDataErrorOf(ApiException(ApiError.RATE_LIMITED, 429)))
        assertEquals(MyDataError.RATE_LIMITED, myDataErrorOf(ApiException(ApiError.INVALID, 400, "rate-limited")))
        assertEquals(MyDataError.SESSION_REVOKED, myDataErrorOf(ApiException(ApiError.SESSION_REVOKED, 401)))
        assertEquals(MyDataError.APP_TOO_OLD, myDataErrorOf(ApiException(ApiError.APP_TOO_OLD, 426)))
        assertEquals(MyDataError.SERVER, myDataErrorOf(ApiException(ApiError.SERVER, 500)))
        assertEquals(MyDataError.FILE, myDataErrorOf(IOException("x")))
    }

    @Test
    fun theFileIsNamedWithTheUtcDate() {
        assertEquals("easystock-my-data-2026-11-14.json", myDataFileName(Instant.parse("2026-11-14T23:30:00Z")))
        assertEquals("easystock-my-data-2026-01-02.json", myDataFileName(Instant.parse("2026-01-02T00:00:00Z")))
    }
}
