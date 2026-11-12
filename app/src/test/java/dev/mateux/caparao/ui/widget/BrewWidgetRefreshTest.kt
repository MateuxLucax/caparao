package dev.mateux.caparao.ui.widget

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrewWidgetRefreshTest {
    @Test
    fun aReadThatNeverReturnsTimesOutWithoutPublishing() = runTest {
        val published = mutableListOf<String>()

        val outcome = BrewWidgetRefresh.run<String>(
            read = { awaitCancellation() },
            publish = published::add,
        )

        assertEquals(BrewWidgetRefresh.Outcome.TIMED_OUT, outcome)
        assertEquals(emptyList<String>(), published)
        assertEquals(BrewWidgetRefresh.TIMEOUT_MS, currentTime)
    }

    @Test
    fun aRequestDuringAnOlderReadIsCoalescedIntoOneReRunThatPublishesTheNewestSnapshot() = runTest {
        val oldReadStarted = CompletableDeferred<Unit>()
        val allowOldReadToFinish = CompletableDeferred<Unit>()
        val published = mutableListOf<String>()
        var latest = "ESPRESSO"

        val oldRefresh = launch {
            BrewWidgetRefresh.run(
                read = {
                    val snapshot = latest
                    oldReadStarted.complete(Unit)
                    allowOldReadToFinish.await()
                    snapshot
                },
                publish = published::add,
            )
        }
        oldReadStarted.await()

        latest = "FILTER"
        var newOutcome: BrewWidgetRefresh.Outcome? = null
        val newRefresh = launch {
            newOutcome = BrewWidgetRefresh.run(read = { latest }, publish = published::add)
        }
        runCurrent()
        assertEquals(BrewWidgetRefresh.Outcome.COALESCED, newOutcome)

        allowOldReadToFinish.complete(Unit)
        joinAll(oldRefresh, newRefresh)

        assertEquals(listOf("ESPRESSO", "FILTER"), published)
    }

    @Test
    fun aBurstOfRequestsReadsAtMostTwice() = runTest {
        val firstReadStarted = CompletableDeferred<Unit>()
        val allowFirstReadToFinish = CompletableDeferred<Unit>()
        var reads = 0
        var latest = "ESPRESSO"

        val leader = launch {
            BrewWidgetRefresh.run(
                read = {
                    reads += 1
                    if (reads == 1) {
                        firstReadStarted.complete(Unit)
                        allowFirstReadToFinish.await()
                    }
                    latest
                },
                publish = {},
            )
        }
        firstReadStarted.await()

        latest = "FILTER"
        val burst = List(5) {
            launch { BrewWidgetRefresh.run(read = { reads += 1; latest }, publish = {}) }
        }
        runCurrent()

        allowFirstReadToFinish.complete(Unit)
        joinAll(leader, *burst.toTypedArray())

        assertEquals(2, reads)
    }
}
