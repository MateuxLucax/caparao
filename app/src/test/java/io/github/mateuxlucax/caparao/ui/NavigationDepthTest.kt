package io.github.mateuxlucax.caparao.ui

import io.github.mateuxlucax.caparao.model.BrewMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The screen transition runs forward when the destination is deeper than the one it leaves (R2). */
class NavigationDepthTest {
    private val tabs = Destination.Tabs
    private val brew = Destination.Brew(BrewMode.ESPRESSO)
    private val detail = Destination.Detail("brew-1")
    @Test
    fun openingBrewOrDetailFromTheTabsGoesForwardAndClosingItGoesBack() {
        assertTrue(detail.depth > tabs.depth)
        assertTrue(brew.depth > tabs.depth)
        assertFalse(tabs.depth > detail.depth)
        assertFalse(tabs.depth > brew.depth)
    }
}
