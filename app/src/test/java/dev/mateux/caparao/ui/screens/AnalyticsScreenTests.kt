package dev.mateux.caparao.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
import dev.mateux.caparao.R
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.CoffeeBag
import dev.mateux.caparao.ui.analytics.AnalyticsViewModel
import dev.mateux.caparao.data.local.CoffeeBagRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class AnalyticsScreenTests : ScreenTestSupport("en", "light") {
    @Test fun weeklySummaryUsesPhoneZoneAndPerBagCaffeine() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val monday = Instant.parse("2026-11-16T03:00:00Z") // Monday midnight locally.
        fun brew(id: String, at: Instant, dose: Double, bag: String?) = Brew(
            id, BrewMode.FILTER, at.toEpochMilli(), 60_000, dose, 250.0, null, 1.0, 2.0, false, bagId = bag,
        )
        val state = AnalyticsViewModel.summarize(
            listOf(
                brew("last-sunday", monday.minusSeconds(1), 20.0, "robusta"),
                brew("monday", monday, 18.0, "robusta"),
                brew("today", monday.plusSeconds(86_400), 15.0, null),
            ),
            listOf(CoffeeBag("robusta", "Blend", weightGrams = 250.0, createdAt = 0, caffeineMgPerGram = 20)),
            now = monday.plusSeconds(86_400), zone = zone,
        )
        assertEquals(2, state.thisWeek.brews)
        assertEquals(33.0, state.thisWeek.grams, 0.0)
        assertEquals(510, state.thisWeek.caffeineMg)
        assertEquals(1, state.lastWeek.brews)
        assertEquals(400, state.lastWeek.caffeineMg)
        assertEquals(150, state.todayCaffeineMg)
    }

    @Test fun analyticsTabShowsWeeklySummaryAndFifthDestination() {
        val now = System.currentTimeMillis()
        runBlocking { CoffeeBagRepository(db.coffeeBags()).save(CoffeeBag("robusta", "Robusta", weightGrams = 250.0, createdAt = now, caffeineMgPerGram = 20)) }
        linkBrew("analytics-brew", "robusta", 18.0)
        open("press-ramp", 38_300)
        compose.onNode(hasText(text(R.string.tab_analytics)) and isSelectable()).performClick()
        compose.onNodeWithText(text(R.string.analytics_this_week)).assertIsDisplayed()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.analytics_about_mg, 360)), 5_000)
        compose.onNode(hasText(text(R.string.tab_analytics)) and isSelectable()).assertIsDisplayed()
    }

    @Test fun editingCoffeeCaffeineStoresTheChosenRate() {
        val now = System.currentTimeMillis()
        runBlocking { CoffeeBagRepository(db.coffeeBags()).save(CoffeeBag("robusta", "Robusta", weightGrams = 250.0, createdAt = now)) }
        open("press-ramp", 38_300)
        click(R.string.tab_coffees)
        compose.onNodeWithText("Robusta").performClick()
        click(R.string.edit)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.coffee_caffeine)), 5_000)
        compose.onNode(hasSetTextAction() and hasText(text(R.string.coffee_caffeine))).performTextReplacement("20")
        click(R.string.save)
        compose.waitForIdle()
        assertEquals(20, runBlocking { CoffeeBagRepository(db.coffeeBags()).get("robusta")!!.caffeineMgPerGram })
    }
}
