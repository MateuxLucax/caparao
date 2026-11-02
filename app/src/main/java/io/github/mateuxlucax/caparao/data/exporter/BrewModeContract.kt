package io.github.mateuxlucax.caparao.data.exporter

import io.github.mateuxlucax.caparao.model.BrewMode

/** The explicit mode name used by the upload contract and exported CSVs. */
internal fun BrewMode.toContractValue(): String = when (this) {
    BrewMode.ESPRESSO -> "espresso"
    BrewMode.FILTER -> "filter"
    BrewMode.FRENCH_PRESS -> "french_press"
}
