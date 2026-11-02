package io.github.mateuxlucax.caparao.model

/** A brew and its curve, as the export needs them. */
data class BrewWithSamples(val brew: Brew, val samples: List<BrewSample>)
