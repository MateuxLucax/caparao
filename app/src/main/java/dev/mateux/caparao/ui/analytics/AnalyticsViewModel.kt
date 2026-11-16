package dev.mateux.caparao.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mateux.caparao.data.local.BrewRepository
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.CoffeeBag
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CoffeeSummary(val brews: Int = 0, val grams: Double = 0.0, val caffeineMg: Int = 0)
data class AnalyticsState(val thisWeek: CoffeeSummary = CoffeeSummary(), val lastWeek: CoffeeSummary = CoffeeSummary(), val todayCaffeineMg: Int = 0)

class AnalyticsViewModel(repository: BrewRepository, bags: kotlinx.coroutines.flow.Flow<List<CoffeeBag>>) : ViewModel() {
    val state: StateFlow<AnalyticsState> = combine(repository.brews, bags) { brews, coffeeBags -> summarize(brews, coffeeBags) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsState())

    companion object {
        fun summarize(brews: List<Brew>, bags: List<CoffeeBag>, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): AnalyticsState {
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
            return AnalyticsState(summary(monday, today), summary(monday.minusDays(7), monday.minusDays(1)), summary(today, today).caffeineMg)
        }
    }
}
