package io.github.mateuxlucax.caparao.ui.brew

import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.data.local.SettingsStore
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
    /** The X of the 1:X to stop at (Settings). */
    val targetRatio: Double = SettingsStore.defaultTargetRatio(mode),
) {
    /** Live 1:X while brewing, when there is a dose. */
    val ratio: Double? get() = dose?.takeIf { it > 0 && weightGrams > 0 }?.let { weightGrams / it }

    /** How far [ratio] is toward [targetRatio], 0 to 1. */
    val ratioProgress: Float get() = ((ratio ?: 0.0) / targetRatio).coerceIn(0.0, 1.0).toFloat()

    /** At or past the target as shown: the ratio reads to one decimal, so 1:1.96 already reads 1:2.0. */
    val onTarget: Boolean get() = ratio?.let { Math.round(it * 10) >= Math.round(targetRatio * 10) } ?: false
}
