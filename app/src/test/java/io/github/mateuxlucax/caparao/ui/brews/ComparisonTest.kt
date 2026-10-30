package io.github.mateuxlucax.caparao.ui.brews

import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComparisonTest {
    private fun brew(id: String, at: Long, mode: BrewMode = BrewMode.FILTER, recipe: String? = "v60") =
        Brew(id, mode, at, 180_000, 15.0, 240.0, null, 1.3, 4.0, interrupted = false, recipeId = recipe, recipeName = recipe)

    private val filters = (1..5).map { brew("f$it", it * 1_000L) }
    private val espresso = brew("e1", 500, BrewMode.ESPRESSO, recipe = "espresso")
    private val all = filters + espresso

    @Test
    fun picksUpToFourOfOneMode() {
        var selected = emptyList<String>()
        filters.forEach { selected = Comparison.toggle(selected, it, all) }
        assertEquals(listOf("f1", "f2", "f3", "f4"), selected)
        assertEquals(selected, Comparison.toggle(selected, espresso, all))
    }

    @Test
    fun anotherModeIsIgnoredAndATapUnpicks() {
        val selected = Comparison.toggle(listOf("e1"), filters[0], all)
        assertEquals(listOf("e1"), selected)
        assertEquals(emptyList<String>(), Comparison.toggle(selected, espresso, all))
    }

    @Test
    fun previousIsTheSameRecipeRightBefore() {
        val other = brew("x", 3_500, recipe = "kalita")
        assertEquals("f3", Comparison.previous(filters[3], all + other)?.id)
        assertNull(Comparison.previous(filters[0], all))
    }

    @Test
    fun brewsWithoutARecipeCompareWithEachOther() {
        val a = brew("a", 1, recipe = null)
        val b = brew("b", 2, recipe = null)
        assertEquals("a", Comparison.previous(b, all + a + b)?.id)
    }

    @Test
    fun trendKeepsTheLastTenOldestFirst() {
        val many = (1..12).map { brew("m$it", it * 1_000L) }
        val trend = Comparison.trend(TrendKey(BrewMode.FILTER, "v60"), many.reversed() + espresso)
        assertEquals((3..12).map { "m$it" }, trend.map { it.id })
    }

    @Test
    fun trendKeyRoundTrips() {
        listOf(TrendKey(BrewMode.ESPRESSO, null), TrendKey(BrewMode.FILTER, "a:b")).forEach {
            assertEquals(it, TrendKey.decode(it.encode()))
        }
    }
}
