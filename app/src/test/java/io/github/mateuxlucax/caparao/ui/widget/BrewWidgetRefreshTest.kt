package io.github.mateuxlucax.caparao.ui.widget

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewWidgetRefreshTest {
    @Test
    fun aDelayedOlderReadCannotPublishAfterTheNewerSnapshot() = runBlocking {
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
        val newRefresh = launch {
            BrewWidgetRefresh.run(read = { latest }, publish = published::add)
        }
        yield()
        assertTrue(newRefresh.isActive)

        allowOldReadToFinish.complete(Unit)
        joinAll(oldRefresh, newRefresh)

        assertEquals(listOf("ESPRESSO", "FILTER"), published)
    }
}
