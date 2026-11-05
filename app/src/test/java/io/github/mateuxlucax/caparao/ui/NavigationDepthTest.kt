package io.github.mateuxlucax.caparao.ui

import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.ui.brews.TrendKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The screen transition runs forward when the destination is deeper than the one it leaves (R2). */
class NavigationDepthTest {
    private val tabs = Destination.Tabs
    private val brew = Destination.Brew(BrewMode.ESPRESSO)
    private val detail = Destination.Detail("brew-1")
    private val compare = Destination.Compare(listOf("brew-1", "brew-2"))
    private val trends = Destination.Trends(TrendKey(BrewMode.ESPRESSO, null))

    @Test
    fun openingCompareOrTrendsFromADetailGoesForward() {
        assertTrue(compare.depth > detail.depth)
        assertTrue(trends.depth > detail.depth)
    }

    @Test
    fun backFromCompareOrTrendsToADetailGoesBackward() {
        assertFalse(detail.depth > compare.depth)
        assertFalse(detail.depth > trends.depth)
    }

    @Test
    fun openingAScreenFromTheTabsGoesForwardAndClosingItGoesBack() {
        assertTrue(detail.depth > tabs.depth)
        assertTrue(brew.depth > tabs.depth)
        assertFalse(tabs.depth > detail.depth)
        assertFalse(tabs.depth > brew.depth)
    }
}
