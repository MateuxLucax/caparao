package io.github.mateuxlucax.caparao.ui.widget

import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewWidgetStatsTest {
    @Test
    fun usesSevenLocalCalendarDatesAndIncludesBothWindowEdgesOnlyByEndTime() {
        val zone = ZoneId.of("America/Los_Angeles")
        val today = LocalDate.of(2026, 11, 2)
        val window = brewWidgetWeekWindow(today, zone)
        val summary = summarizeBrewWidgetWeek(
            listOf(
                brewEndingAt("before", window.startInclusiveMs - 1),
                brewEndingAt("at-start", window.startInclusiveMs),
                brewEndingAt("last-ms", window.endExclusiveMs - 1),
                brewEndingAt("at-end", window.endExclusiveMs),
            ),
            window,
            zone,
        )

        assertEquals((0L..6L).map { today.minusDays(6 - it) }, window.days)
        assertEquals(7, summary.buckets.size)
        assertEquals(2, summary.brewCount)
        assertEquals(1, summary.buckets.first().brewCount)
        assertEquals(1, summary.buckets.last().brewCount)
        assertTrue(summary.buckets.subList(1, 6).all { it.brewCount == 0 })
    }

    @Test
    fun localMidnightBucketsRemainSevenAcrossTheSpringDaylightSavingChange() {
        val zone = ZoneId.of("America/New_York")
        val today = LocalDate.of(2026, 3, 10)
        val window = brewWidgetWeekWindow(today, zone)
        val beforeJump = ZonedDateTime.of(2026, 3, 8, 1, 59, 0, 0, zone).toInstant().toEpochMilli()
        val afterJump = ZonedDateTime.of(2026, 3, 8, 3, 0, 0, 0, zone).toInstant().toEpochMilli()

        val summary = summarizeBrewWidgetWeek(
            listOf(brewEndingAt("before-jump", beforeJump), brewEndingAt("after-jump", afterJump)),
            window,
            zone,
        )

        assertEquals(Duration.ofHours(167).toMillis(), window.endExclusiveMs - window.startInclusiveMs)
        assertEquals(7, summary.buckets.size)
        assertEquals(2, summary.buckets.single { it.date == LocalDate.of(2026, 3, 8) }.brewCount)
    }

    @Test
    fun emptyWeekHasSevenZeroBucketsAndNoTotals() {
        val zone = ZoneId.of("UTC")
        val window = brewWidgetWeekWindow(LocalDate.of(2026, 11, 2), zone)

        val summary = summarizeBrewWidgetWeek(emptyList(), window, zone)

        assertTrue(summary.isEmpty)
        assertEquals(7, summary.buckets.size)
        assertTrue(summary.buckets.all { it.brewCount == 0 })
        assertEquals(0.0, summary.estimatedBeverageMl, 0.0)
        assertEquals(0.0, summary.doseGrams, 0.0)
        assertEquals(0.0, summary.estimatedCaffeineMg, 0.0)
    }

    @Test
    fun interruptedBrewsStillCountAndUseRecordedDoseAndYield() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 11, 2)
        val window = brewWidgetWeekWindow(today, zone)
        val end = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val interrupted = brewEndingAt(
            id = "interrupted-filter",
            endedAt = end,
            mode = BrewMode.FILTER,
            doseGrams = 15.0,
            yieldGrams = 100.0,
            interrupted = true,
        )

        val summary = summarizeBrewWidgetWeek(listOf(interrupted), window, zone)

        assertEquals(1, summary.brewCount)
        assertEquals(1, summary.buckets.last().brewCount)
        assertEquals(15.0, summary.doseGrams, 0.0)
        assertEquals(70.0, summary.estimatedBeverageMl, 0.0)
        assertEquals(165.0, summary.estimatedCaffeineMg, 0.0)
    }

    @Test
    fun perModeEstimatesUseTheApprovedFactorsAndClampFilterVolumeAtZero() {
        val espresso = estimateBrewWidgetBrew(
            brewEndingAt("espresso", 1_000, BrewMode.ESPRESSO, doseGrams = 18.0, yieldGrams = 36.4),
        )
        val filter = estimateBrewWidgetBrew(
            brewEndingAt("filter", 1_000, BrewMode.FILTER, doseGrams = 15.0, yieldGrams = 250.0),
        )
        val lowYieldFilter = estimateBrewWidgetBrew(
            brewEndingAt("low-yield-filter", 1_000, BrewMode.FILTER, doseGrams = 15.0, yieldGrams = 20.0),
        )

        assertEquals(36.4, espresso.beverageMl, 0.0)
        assertEquals(126.0, espresso.caffeineMg, 0.0)
        assertEquals(220.0, filter.beverageMl, 0.0)
        assertEquals(165.0, filter.caffeineMg, 0.0)
        assertEquals(0.0, lowYieldFilter.beverageMl, 0.0)
        assertEquals(165.0, lowYieldFilter.caffeineMg, 0.0)
    }

    @Test
    fun frenchPressUsesItsApprovedFactorsAndClampsBeverageVolumeAtZero() {
        val press = estimateBrewWidgetBrew(
            brewEndingAt("french-press", 1_000, BrewMode.FRENCH_PRESS, doseGrams = 20.0, yieldGrams = 300.0),
        )
        val lowYieldPress = estimateBrewWidgetBrew(
            brewEndingAt("low-yield-press", 1_000, BrewMode.FRENCH_PRESS, doseGrams = 20.0, yieldGrams = 20.0),
        )

        assertEquals(260.0, press.beverageMl, 0.0)
        assertEquals(140.0, press.caffeineMg, 0.0)
        assertEquals(0.0, lowYieldPress.beverageMl, 0.0)
        assertEquals(140.0, lowYieldPress.caffeineMg, 0.0)
    }

    private fun brewEndingAt(
        id: String,
        endedAt: Long,
        mode: BrewMode = BrewMode.FILTER,
        doseGrams: Double = 15.0,
        yieldGrams: Double = 250.0,
        interrupted: Boolean = false,
    ) = Brew(
        id = id,
        mode = mode,
        startedAt = endedAt - 1_000,
        durationMs = 1_000,
        doseGrams = doseGrams,
        yieldGrams = yieldGrams,
        timeToFirstDropMs = null,
        flowAvgGramsPerSecond = 1.0,
        flowPeakGramsPerSecond = 2.0,
        interrupted = interrupted,
    )
}
