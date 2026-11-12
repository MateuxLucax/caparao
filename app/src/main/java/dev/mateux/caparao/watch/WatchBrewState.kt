package dev.mateux.caparao.watch

import dev.mateux.caparao.brew.BrewPhase
import dev.mateux.caparao.brew.StepState
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.RecipeStep

/**
 * What the watch needs from the open brew (#389): the session's live state and whether the phone is still before it.
 * Kept separate from the brew screen's state so the watch does not mirror UI fields it never shows.
 */
data class WatchBrewState(
    /** On the dose, coffee, weigh or preparation steps: the watch shows the scale, not a brew. */
    val beforeBrew: Boolean,
    val mode: BrewMode,
    val phase: BrewPhase,
    val weightGrams: Double,
    val flowGramsPerSecond: Double,
    val elapsedMs: Long,
    /** Where the guide stands; null without a recipe or a free brew. */
    val guide: StepState?,
    /** The steps being followed, empty when there is no guide. */
    val steps: List<RecipeStep>,
    /** The French press removal was confirmed, so the watch shows it is ready to serve. */
    val frenchPressRemoved: Boolean,
)
