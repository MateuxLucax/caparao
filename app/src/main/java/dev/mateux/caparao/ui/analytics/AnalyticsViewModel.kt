package dev.mateux.caparao.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mateux.caparao.data.local.BrewRepository
import dev.mateux.caparao.data.local.SettingsStore
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.CoffeeBag
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CoffeeSummary(val brews: Int = 0, val grams: Double = 0.0, val caffeineMg: Int = 0)
data class AnalyticsState(
    val thisWeek: CoffeeSummary = CoffeeSummary(), val lastWeek: CoffeeSummary = CoffeeSummary(), val todayCaffeineMg: Int = 0,
    val caffeine: CaffeineEstimate = CaffeineEstimate(),
)

data class CaffeinePoint(val hour: Double, val mg: Double)
data class CaffeineEstimate(
    val todayBrews: Int = 0, val nowMg: Int = 0, val bedtimeMg: Int = 0, val lastCoffeeByMinutes: Int? = null,
    val points: List<CaffeinePoint> = emptyList(), val brewHours: List<Double> = emptyList(),
)

class AnalyticsViewModel(repository: BrewRepository, bags: kotlinx.coroutines.flow.Flow<List<CoffeeBag>>, private val settings: SettingsStore) : ViewModel() {
    val state: StateFlow<AnalyticsState> = combine(repository.brews, bags) { brews, coffeeBags -> summarize(brews, coffeeBags, bedtimeMinutes = settings.bedtimeMinutes) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsState())

    companion object {
        fun summarize(brews: List<Brew>, bags: List<CoffeeBag>, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault(), bedtimeMinutes: Int = SettingsStore.DEFAULT_BEDTIME_MINUTES): AnalyticsState {
            val today = now.atZone(zone).toLocalDate()
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val byId = bags.associateBy(CoffeeBag::id)
            fun summary(from: LocalDate, through: LocalDate): CoffeeSummary {
                val selected = brews.filter {
                    val date = Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate()
                    date >= from && date <= through
                }
                return CoffeeSummary(selected.size, selected.sumOf(Brew::doseGrams), selected.sumOf { brew ->
                    brew.doseGrams * (brew.bagId?.let(byId::get)?.caffeineMgPerGram ?: 10)
                }.toInt())
            }
            val caffeine = caffeineEstimate(brews, byId, now, zone, bedtimeMinutes)
            return AnalyticsState(summary(monday, today), summary(monday.minusDays(7), monday.minusDays(1)), summary(today, today).caffeineMg, caffeine)
        }

        private fun caffeineEstimate(brews: List<Brew>, bags: Map<String, CoffeeBag>, now: Instant, zone: ZoneId, bedtimeMinutes: Int): CaffeineEstimate {
            val localNow = now.atZone(zone)
            val today = localNow.toLocalDate()
            val safeBedtime = bedtimeMinutes.coerceIn(0, 1439)
            val bedtime = today.atTime(LocalTime.of(safeBedtime / 60, safeBedtime % 60)).atZone(zone)
            val yesterday = today.minusDays(1).atStartOfDay(zone)
            val relevant = brews.filter { it.startedAt >= yesterday.toInstant().toEpochMilli() && it.startedAt <= now.toEpochMilli() }
            val doses = relevant.map { brew ->
                val instant = Instant.ofEpochMilli(brew.startedAt)
                val dose = brew.doseGrams * (brew.bagId?.let(bags::get)?.caffeineMgPerGram ?: 10)
                instant to dose
            }
            fun amount(at: Instant) = doses.sumOf { (time, dose) ->
                val elapsed = (at.toEpochMilli() - time.toEpochMilli()) / 3_600_000.0
                if (elapsed < 0) 0.0 else dose * KA / (KA - KE) * (kotlin.math.exp(-KE * elapsed) - kotlin.math.exp(-KA * elapsed))
            }
            val start = today.atTime(6, 0).atZone(zone)
            val end = bedtime
            val points = if (relevant.none { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == today }) emptyList() else
                (0..((kotlin.math.abs(end.toEpochSecond() - start.toEpochSecond()) / 900).toInt().coerceAtLeast(1))).map { step ->
                    val time = if (end.isAfter(start)) start.plusSeconds(step * 900L).coerceAtMost(end) else end.plusSeconds(step * 900L).coerceAtMost(start)
                    CaffeinePoint(time.hour + time.minute / 60.0, amount(time.toInstant()))
                }
            val bedtimeMg = amount(bedtime.toInstant()).toInt()
            val usual = brews.filter { it.startedAt >= today.minusDays(14).atStartOfDay(zone).toInstant().toEpochMilli() && it.startedAt < today.atStartOfDay(zone).toInstant().toEpochMilli() }
                .map { it.doseGrams * (it.bagId?.let(bags::get)?.caffeineMgPerGram ?: 10) }.sorted()
            val median = usual.takeIf { it.isNotEmpty() }?.let { values -> (values[(values.size - 1) / 2] + values[values.size / 2]) / 2.0 }
            val cutoff = median?.let { dose ->
                val stepMinutes = (0..safeBedtime / 15).map { it * 15 }
                val candidates = (stepMinutes + safeBedtime).distinct()
                candidates.lastOrNull { minute ->
                    val coffeeTime = today.atTime(minute / 60, minute % 60).atZone(zone)
                    val hours = (bedtime.toEpochSecond() - coffeeTime.toEpochSecond()) / 3600.0
                    (amount(bedtime.toInstant()) + (if (hours >= 0) absorption(dose, hours) else 0.0)) <= 50.0
                }
            }
            return CaffeineEstimate(
                todayBrews = relevant.count { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == today },
                nowMg = amount(now).toInt(),
                bedtimeMg = bedtimeMg, lastCoffeeByMinutes = cutoff,
                points = points,
                brewHours = relevant.filter { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == today }.map { Instant.ofEpochMilli(it.startedAt).atZone(zone).let { local -> local.hour + local.minute / 60.0 } },
            )
        }

        private fun absorption(dose: Double, hours: Double): Double = dose * KA / (KA - KE) * (kotlin.math.exp(-KE * hours) - kotlin.math.exp(-KA * hours))
        private const val KA = 2.772588722239781 // ln(2) / 0.25 h
        private const val KE = 0.13862943611198905 // ln(2) / 5 h
    }
}
