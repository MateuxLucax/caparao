package dev.mateux.caparao.data.exporter

import dev.mateux.caparao.model.BrewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class BrewModeContractTest {
    @Test
    fun everyModeHasAnExplicitContractValue() {
        assertEquals(
            mapOf(
                BrewMode.FILTER to "filter",
                BrewMode.ESPRESSO to "espresso",
                BrewMode.FRENCH_PRESS to "french_press",
            ),
            BrewMode.entries.associateWith { it.toContractValue() },
        )
    }
}
