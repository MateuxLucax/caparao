package io.github.mateuxlucax.caparao.ui.brew

import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.BrewSample
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.brew.StepState
import io.github.mateuxlucax.caparao.ui.brews.NotesForm

/** The brew screen's steps. */
enum class BrewStep { DOSE, WEIGH, PREPARE_PORTAFILTER, LIVE, SUMMARY }

data class BrewUiState(
    val mode: BrewMode,
    val step: BrewStep = BrewStep.DOSE,
    /** As typed or weighed; null until set (the last one is suggested).*/
    val dose: Double? = null,
    val phase: BrewPhase = BrewPhase.WAITING_FOR_CUP,
    /** Taring and no zero even after the retry: the tare screen offers Retry until a zero comes. */
    val tareTimedOut: Boolean = false,
    val connected: Boolean = false,
    val weightGrams: Double = 0.0,
    /** Smoothed from the weights, not the scale's raw flow. */
    val flowGramsPerSecond: Double = 0.0,
    /** Since the first drop or first water; 0 before. */
    val elapsedMs: Long = 0,
    /** The saved brew, on the summary; null there when nothing was recorded. */
    val saved: Brew? = null,
    /** The saved brew's curve, drawn on the espresso summary (#198). */
    val curve: List<BrewSample> = emptyList(),
    /** The X of the 1:X to stop at: the recipe's, or the free brew one from Settings. */
    val targetRatio: Double = SettingsStore.defaultTargetRatio(mode),
    /** The recipes of [mode], to choose from on the dose step. */
    val recipes: List<Recipe> = emptyList(),
    /** The one being followed; null for a free brew. */
    val recipe: Recipe? = null,
    /** Filter preparation checklist marks, kept if the setup needs to be lifted and tared again. */
    val filterPreparationChecks: Set<Int> = emptySet(),
    /** Filter or French press with a recipe's steps, once brewing: where the guide stands (#103, #125). */
    val guide: StepState? = null,
    /** The post-4:00 French press removal was confirmed by stable near-zero scale readings. */
    val frenchPressRemoved: Boolean = false,
    /** On the summary: the saved brew's notes being typed, and past coffees to pick from. */
    val notes: NotesForm? = null,
    val coffees: List<String> = emptyList(),
) {
    /** The steps being followed, when the guide is on. */
    val steps: List<RecipeStep> get() = if (guide != null) recipe?.steps.orEmpty() else emptyList()

    /** Espresso with a recipe's time window: where the shot time stands; null without one or before the shot. */
    val window: Window?
        get() {
            val min = recipe?.targetMinMs ?: return null
            val max = recipe.targetMaxMs ?: return null
            return when (mode) {
                BrewMode.ESPRESSO -> if (phase != BrewPhase.BREWING && phase != BrewPhase.DONE) {
                    null
                } else {
                    when {
                        elapsedMs < min -> Window.BEFORE
                        elapsedMs <= max -> Window.INSIDE
                        else -> Window.PAST
                    }
                }
                BrewMode.FILTER, BrewMode.FRENCH_PRESS -> null
            }
        }

    /** Filter drain-time state, once the last guided step is done; time runs from the first pour. */
    val drainWindow: Window?
        get() {
            if (mode != BrewMode.FILTER || phase != BrewPhase.BREWING) return null
            val guidedSteps = steps
            if (guidedSteps.isEmpty() || guide?.allDone(guidedSteps.size) != true) return null
            val min = recipe?.targetMinMs ?: return null
            val max = recipe.targetMaxMs ?: return null
            if (min > max) return null
            return when {
                elapsedMs < min -> Window.BEFORE
                elapsedMs <= max -> Window.INSIDE
                else -> Window.PAST
            }
        }

    enum class Window { BEFORE, INSIDE, PAST }

    /** Live 1:X once the brew has started, when there is a dose; before, the weight on the scale is the cup. */
    val ratio: Double?
        get() = dose?.takeIf { it > 0 && weightGrams > 0 && (phase == BrewPhase.BREWING || phase == BrewPhase.DONE) }?.let { weightGrams / it }

    /** How far [ratio] is toward [targetRatio], 0 to 1. */
    val ratioProgress: Float get() = ((ratio ?: 0.0) / targetRatio).coerceIn(0.0, 1.0).toFloat()

    /** At or past the target as shown: the ratio reads to one decimal, so 1:1.96 already reads 1:2.0. */
    /** The water the dose and the target ratio call for. */
    val plannedWater: Double? get() = dose?.let { it * targetRatio }

    val onTarget: Boolean get() = ratio?.let { Math.round(it * 10) >= Math.round(targetRatio * 10) } ?: false
}
