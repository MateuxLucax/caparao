package io.github.mateuxlucax.caparao.data.exporter

import io.github.mateuxlucax.caparao.model.BrewMode
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
