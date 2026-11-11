package io.github.mateuxlucax.caparao

import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.data.watch.WatchTransport
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.ScaleReading
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.ui.FakeScaleSource
import io.github.mateuxlucax.caparao.ui.brew.BrewStep
import io.github.mateuxlucax.caparao.ui.brew.BrewUiState
import io.github.mateuxlucax.caparao.ui.home.ScaleTimer
import io.github.mateuxlucax.caparao.watch.WatchPaths
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Following watches that connect after the app opened and dropping the ones that leave (#324). */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchLinkTest {
    /** A capability source and message log the test drives by hand; counts the live listeners. */
    private class FakeWatchTransport : WatchTransport {
        private val nodes = MutableStateFlow<List<String>>(emptyList())
        private val incoming = MutableSharedFlow<String>(extraBufferCapacity = 8)
        val sent = mutableListOf<Pair<String, String>>()
        val opened = mutableListOf<String>()
        var watching = 0
            private set
        var commanding = 0
            private set

        override val watches: Flow<List<String>> = nodes.onStart { watching++ }.onCompletion { watching-- }
        override val commands: Flow<String> = incoming.onStart { commanding++ }.onCompletion { commanding-- }

        /** Makes [ids] the reachable watches, as a capability change does. */
        fun reachable(vararg ids: String) {
            nodes.value = ids.toList()
        }

        /** Sends a path from the watch, as its tare or timer button does. */
        fun press(path: String) {
            check(incoming.tryEmit(path))
        }

        override fun send(node: String, path: String, payload: ByteArray) {
            sent += node to path
        }

        override fun open(node: String) {
            opened += node
        }
    }

    private val connected = ScaleState.Connected(battery = null)

    private fun TestScope.link(transport: FakeWatchTransport, scale: FakeScaleSource) =
        WatchLink(transport, backgroundScope, ScaleTimer(scale), scale) { currentTime }

    private fun connectedScale() = FakeScaleSource().apply {
        state.value = connected
        reading.value = ScaleReading(weightGrams = 18.2, flowGramsPerSecond = 0.0, timerSeconds = 0, overload = false)
    }

    @Test
    fun aWatchThatBecomesReachableLaterStartsMirroringAndLeavingStopsSends() = runTest {
        val transport = FakeWatchTransport()
        val scale = connectedScale()
        val link = link(transport, scale)
        link.start()
        runCurrent()

        assertEquals("no watch at start: nothing is sent", emptyList<Pair<String, String>>(), transport.sent)

        transport.reachable("watch")
        runCurrent()
        assertEquals("the watch gets the current snapshot at once", listOf("watch" to WatchPaths.SNAPSHOT), transport.sent)

        advanceTimeBy(201)
        scale.send(ScaleReading(weightGrams = 20.0, flowGramsPerSecond = 1.0, timerSeconds = 5, overload = false))
        runCurrent()
        assertEquals(2, transport.sent.size)

        transport.reachable()
        runCurrent()
        val sentWhileWatched = transport.sent.size
        advanceTimeBy(201)
        scale.send(ScaleReading(weightGrams = 21.0, flowGramsPerSecond = 1.1, timerSeconds = 6, overload = false))
        runCurrent()
        assertEquals("a watch that left gets no more snapshots", sentWhileWatched, transport.sent.size)
    }

    @Test
    fun aWatchThatAppearsDuringAReadyBrewGetsTheSnapshotAndOpensTheWatchApp() = runTest {
        val transport = FakeWatchTransport()
        val scale = connectedScale()
        val link = link(transport, scale)
        link.brew.value = BrewUiState(
            BrewMode.ESPRESSO,
            step = BrewStep.LIVE,
            phase = BrewPhase.READY,
            weightGrams = 30.0,
            elapsedMs = 20_000,
        )
        link.start()
        runCurrent()

        assertEquals("with no watch there is nothing to open", emptyList<String>(), transport.opened)

        transport.reachable("watch")
        runCurrent()

        assertEquals(listOf("watch" to WatchPaths.SNAPSHOT), transport.sent)
        assertEquals(listOf("watch"), transport.opened)
    }

    @Test
    fun watchButtonsStillReachTheScaleAndTimer() = runTest {
        val transport = FakeWatchTransport()
        val scale = connectedScale()
        val link = link(transport, scale)
        link.start()
        runCurrent()

        transport.press(WatchPaths.TARE)
        transport.press(WatchPaths.TIMER_TOGGLE)
        transport.press(WatchPaths.TIMER_RESET)
        runCurrent()

        assertEquals(listOf("tare", "timer start", "timer reset"), scale.commands)
    }

    @Test
    fun stopRemovesBothListeners() = runTest {
        val transport = FakeWatchTransport()
        val link = link(transport, connectedScale())
        link.start()
        runCurrent()
        assertEquals(1, transport.watching)
        assertEquals(1, transport.commanding)

        link.stop()
        runCurrent()

        assertEquals("the capability listener is gone", 0, transport.watching)
        assertEquals("the message listener is gone", 0, transport.commanding)
    }
}
