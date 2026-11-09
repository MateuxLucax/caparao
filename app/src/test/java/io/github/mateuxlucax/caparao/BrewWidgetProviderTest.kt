package io.github.mateuxlucax.caparao

import io.github.mateuxlucax.caparao.ui.widget.BrewWidgetRefresh
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A broadcast that hangs on its data read must still finish before Android's background ANR window (#326). */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BrewWidgetProviderTest {
    @Test
    fun finishRunsWithinTheTimeoutWhenTheReadNeverReturns() = runTest {
        var finished = false
        var published = false

        BrewWidgetProvider().refreshBounded<String>(
            read = { awaitCancellation() },
            publish = { published = true },
            finish = { finished = true },
        )

        assertTrue("the broadcast must finish", finished)
        assertFalse("the previous widget content stays", published)
        assertTrue(
            "finished after ${currentTime}ms, expected at most ${BrewWidgetRefresh.TIMEOUT_MS}ms",
            currentTime <= BrewWidgetRefresh.TIMEOUT_MS,
        )
    }
}
