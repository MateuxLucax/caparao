package dev.mateux.caparao.ui.components

import dev.mateux.caparao.R
import dev.mateux.caparao.model.StepKind

/** The string for a recipe [StepKind], shared by the editor and the brew guide. */
val StepKind.label: Int
    get() = when (this) {
        StepKind.BLOOM -> R.string.step_bloom
        StepKind.POUR -> R.string.step_pour
        StepKind.WAIT -> R.string.step_wait
        StepKind.SWIRL -> R.string.step_swirl
        StepKind.STIR -> R.string.step_stir
        StepKind.PRESS -> R.string.step_press
    }
