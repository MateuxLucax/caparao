package dev.mateux.caparao.wear

import dev.mateux.caparao.watch.WatchGuide
import dev.mateux.caparao.watch.WatchSnapshot
import dev.mateux.caparao.watch.WatchState
import dev.mateux.caparao.watch.WatchStepKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class WatchFormatTest {
    private val brazil = Locale.forLanguageTag("pt-BR")

    @Test
    fun weightAndFlowUseTheLocale() {
        assertEquals("36.4 g", WatchFormat.grams(36.43f, Locale.US))
        assertEquals("36,4 g", WatchFormat.grams(36.43f, brazil))
        assertEquals("36.4", WatchFormat.weight(36.43f, Locale.US))
        assertEquals("36,4", WatchFormat.weight(36.43f, brazil))
        assertEquals("1,8 g/s", WatchFormat.flow(1.8f, brazil))
        assertEquals("0.0 g", WatchFormat.grams(-0.04f, Locale.US))
        assertEquals("-0.3 g", WatchFormat.grams(-0.3f, Locale.US))
    }

    @Test
    fun timeIsMinutesAndSeconds() {
        assertEquals("0:00", WatchFormat.time(0))
        assertEquals("0:27", WatchFormat.time(27_900))
        assertEquals("3:05", WatchFormat.time(185_000))
    }

    @Test
    fun stepTargetsAndCountdowns() {
        assertEquals("150 g", WatchFormat.wholeGrams(149.6f))
        assertEquals(5, WatchFormat.seconds(4_200))
        assertEquals(1, WatchFormat.seconds(1))
        assertEquals(0, WatchFormat.seconds(0))
    }

    @Test
    fun nextStepDueOnlyWhileBrewingWithAStepToFollow() {
        assertEquals(4_200L, WatchFormat.nextStepDueMs(brewing(WatchStepKind.POUR, index = 1, count = 5, dueInMs = 4_200L)))

        // The last step's remaining time is its own, not a countdown to a next step.
        assertNull(WatchFormat.nextStepDueMs(brewing(WatchStepKind.WAIT, index = 4, count = 5, dueInMs = 4_200L)))
        assertNull(WatchFormat.nextStepDueMs(brewing(WatchStepKind.PRESS, index = 1, count = 2, dueInMs = 4_200L)))
        assertNull(WatchFormat.nextStepDueMs(brewing(WatchStepKind.STIR, index = 1, count = 2, dueInMs = 4_200L)))

        // Nothing to count down outside a brew, without time left, or without a guide.
        assertNull(
            WatchFormat.nextStepDueMs(
                brewing(WatchStepKind.POUR, index = 1, count = 5, dueInMs = 4_200L).copy(state = WatchState.SCALE),
            ),
        )
        assertNull(WatchFormat.nextStepDueMs(brewing(WatchStepKind.POUR, index = 1, count = 5, dueInMs = 0L)))
        assertNull(WatchFormat.nextStepDueMs(WatchSnapshot(WatchState.BREWING, 0f, 0f, 0L, timerRunning = false)))
    }

    private fun brewing(kind: WatchStepKind, index: Int, count: Int, dueInMs: Long?) = WatchSnapshot(
        WatchState.BREWING, 0f, 0f, 0L, timerRunning = false,
        guide = WatchGuide(index = index, count = count, kind = kind, targetGrams = null, dueInMs = dueInMs, paceMs = null),
    )
}
