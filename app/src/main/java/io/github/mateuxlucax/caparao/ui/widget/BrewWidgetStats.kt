package io.github.mateuxlucax.caparao.ui.widget

import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max

private const val ESPRESSO_CAFFEINE_MG_PER_DOSE_GRAM = 7.0
private const val FILTER_CAFFEINE_MG_PER_DOSE_GRAM = 11.0
private const val FILTER_RETAINED_WATER_GRAMS_PER_DOSE_GRAM = 2.0

internal data class BrewWidgetDayBucket(val date: LocalDate, val brewCount: Int)

internal data class BrewWidgetWeekWindow(
    val days: List<LocalDate>,
    val startInclusiveMs: Long,
    val endExclusiveMs: Long,
)

internal data class BrewWidgetWeek(
    val buckets: List<BrewWidgetDayBucket>,
    val brewCount: Int,
    val estimatedBeverageMl: Double,
    val doseGrams: Double,
    val estimatedCaffeineMg: Double,
) {
    val isEmpty: Boolean get() = brewCount == 0
}

/** Seven local dates, oldest first, including [today]. Boundaries are local midnights, not fixed 24-hour periods. */
internal fun brewWidgetWeekWindow(today: LocalDate, zone: ZoneId): BrewWidgetWeekWindow {
    val days = List(7) { index -> today.minusDays((6 - index).toLong()) }
    return BrewWidgetWeekWindow(
        days = days,
        startInclusiveMs = days.first().atStartOfDay(zone).toInstant().toEpochMilli(),
        endExclusiveMs = days.last().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
    )
}

/** Summarizes saved brew rows by their end instant; interrupted brews count using their recorded dose and yield. */
internal fun summarizeBrewWidgetWeek(
    brews: List<Brew>,
    window: BrewWidgetWeekWindow,
    zone: ZoneId,
): BrewWidgetWeek {
    val included = brews.filter { brew ->
        val endedAt = brew.startedAt + brew.durationMs
        endedAt >= window.startInclusiveMs && endedAt < window.endExclusiveMs
    }
    val countsByDate = included.groupingBy { brew ->
        Instant.ofEpochMilli(brew.startedAt + brew.durationMs).atZone(zone).toLocalDate()
    }.eachCount()
    val estimates = included.map(::estimateBrewWidgetBrew)

    return BrewWidgetWeek(
        buckets = window.days.map { date -> BrewWidgetDayBucket(date, countsByDate[date] ?: 0) },
        brewCount = included.size,
        estimatedBeverageMl = estimates.sumOf { it.beverageMl },
        doseGrams = estimates.sumOf { it.doseGrams },
        estimatedCaffeineMg = estimates.sumOf { it.caffeineMg },
    )
}

/** Per-mode widget assumptions. Deliberately exhaustive so a new brew mode needs an explicit estimate here. */
internal fun estimateBrewWidgetBrew(brew: Brew): BrewWidgetBrewEstimate = when (brew.mode) {
    BrewMode.ESPRESSO -> BrewWidgetBrewEstimate(
        beverageMl = brew.yieldGrams,
        doseGrams = brew.doseGrams,
        caffeineMg = brew.doseGrams * ESPRESSO_CAFFEINE_MG_PER_DOSE_GRAM,
    )
    BrewMode.FILTER -> BrewWidgetBrewEstimate(
        beverageMl = max(brew.yieldGrams - brew.doseGrams * FILTER_RETAINED_WATER_GRAMS_PER_DOSE_GRAM, 0.0),
        doseGrams = brew.doseGrams,
        caffeineMg = brew.doseGrams * FILTER_CAFFEINE_MG_PER_DOSE_GRAM,
    )
}

internal data class BrewWidgetBrewEstimate(
    val beverageMl: Double,
    val doseGrams: Double,
    val caffeineMg: Double,
)
