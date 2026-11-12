package ch.mathorck.easystock.sync

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The background job: its request and the schedule/cancel calls. The worker itself runs the engine of the process on the app database: not run here. */
class BackgroundSyncWorkerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        val config = Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun requestIsEvery15MinutesWithNetwork() {
        val spec = backgroundSyncRequest().workSpec
        assertEquals(TimeUnit.MINUTES.toMillis(15), spec.intervalDuration)
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
    }

    @Test
    fun scheduleKeepsOneJobAndCancelStopsIt() {
        val scheduler = WorkManagerBackgroundSync(context)
        val manager = WorkManager.getInstance(context)
        scheduler.schedule()
        scheduler.schedule()
        val infos = manager.getWorkInfosForUniqueWork(BACKGROUND_SYNC_WORK).get()
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)
        scheduler.cancel()
        assertTrue(manager.getWorkInfosForUniqueWork(BACKGROUND_SYNC_WORK).get().all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun pushesInABurstEnqueueOneExpeditedUniqueJob() {
        val manager = WorkManager.getInstance(context)
        repeat(3) { enqueuePushSync(context) }
        val infos = manager.getWorkInfosForUniqueWork(PUSH_SYNC_WORK).get()
        assertEquals(1, infos.size)
        assertTrue(pushSyncRequest().workSpec.expedited)
        assertEquals(NetworkType.CONNECTED, pushSyncRequest().workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun cancelStopsThePushJobToo() {
        val manager = WorkManager.getInstance(context)
        enqueuePushSync(context)
        WorkManagerBackgroundSync(context).cancel()
        assertTrue(manager.getWorkInfosForUniqueWork(PUSH_SYNC_WORK).get().all { it.state == WorkInfo.State.CANCELLED })
    }
}
