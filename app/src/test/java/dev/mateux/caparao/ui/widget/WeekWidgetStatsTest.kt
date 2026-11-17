package dev.mateux.caparao.ui.widget

import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class WeekWidgetStatsTest {
    private fun brew(at: String) = Brew(at, BrewMode.FILTER, Instant.parse(at).toEpochMilli(), 60_000, 18.0, 300.0, null, 1.0, 1.0, false)

    @Test fun bucketsUseLocalMidnightsAcrossDstAndIncludeToday() {
        val zone = ZoneId.of("America/New_York")
        val today = LocalDate.parse("2026-03-08")
        val stats = weekStats(listOf(brew("2026-03-02T05:00:00Z"), brew("2026-03-08T04:30:00Z"), brew("2026-03-09T04:00:00Z")), today, zone)
        assertEquals(7, stats.counts.size)
        assertEquals(listOf(1, 0, 0, 0, 0, 1, 0), stats.counts)
        assertEquals(2, stats.brews)
        assertEquals(LocalDate.parse("2026-03-08"), stats.days.last())
    }
}
