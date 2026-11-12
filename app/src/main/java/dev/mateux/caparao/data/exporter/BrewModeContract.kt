package dev.mateux.caparao.data.exporter

import dev.mateux.caparao.model.BrewMode

/** The explicit mode name used by the upload contract. */
internal fun BrewMode.toContractValue(): String = when (this) {
    BrewMode.ESPRESSO -> "espresso"
    BrewMode.FILTER -> "filter"
    BrewMode.FRENCH_PRESS -> "french_press"
}
