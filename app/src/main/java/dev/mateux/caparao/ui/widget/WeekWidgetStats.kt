package dev.mateux.caparao.ui.widget

import dev.mateux.caparao.model.Brew
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class WeekStats(val days: List<LocalDate>, val counts: List<Int>, val brews: Int, val grams: Double, val liters: Double)

internal fun weekStats(brews: List<Brew>, today: LocalDate, zone: ZoneId): WeekStats {
    val days = List(7) { today.minusDays((6 - it).toLong()) }
    val start = days.first().atStartOfDay(zone).toInstant().toEpochMilli()
    val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val recent = brews.filter { it.startedAt + it.durationMs in start until end }
    val counts = recent.groupingBy { Instant.ofEpochMilli(it.startedAt + it.durationMs).atZone(zone).toLocalDate() }.eachCount()
    return WeekStats(days, days.map { counts[it] ?: 0 }, recent.size, recent.sumOf { it.doseGrams }, recent.sumOf { it.yieldGrams } / 1000)
}
