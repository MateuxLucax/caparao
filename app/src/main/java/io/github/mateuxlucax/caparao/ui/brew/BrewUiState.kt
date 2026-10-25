package io.github.mateuxlucax.caparao.ui.brew

import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode

/** The brew screen's steps. */
enum class BrewStep { DOSE, LIVE, SUMMARY }

data class BrewUiState(
    val mode: BrewMode,
    val step: BrewStep = BrewStep.DOSE,
    /** As typed or weighed; null until set (the last one is suggested).*/
    val dose: Double? = null,
    val phase: BrewPhase = BrewPhase.WAITING_FOR_CUP,
    val connected: Boolean = false,
    val weightGrams: Double = 0.0,
    /** Smoothed from the weights, not the scale's raw flow. */
    val flowGramsPerSecond: Double = 0.0,
    /** Since the first drop or first water; 0 before. */
    val elapsedMs: Long = 0,
    /** The saved brew, on the summary; null there when nothing was recorded. */
    val saved: Brew? = null,
) {
    /** Live 1:X while brewing, when there is a dose. */
    val ratio: Double? get() = dose?.takeIf { it > 0 && weightGrams > 0 }?.let { weightGrams / it }
}
