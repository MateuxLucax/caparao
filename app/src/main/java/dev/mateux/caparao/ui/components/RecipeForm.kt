package dev.mateux.caparao.ui.components

import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.BrewLimits
import dev.mateux.caparao.model.Recipe
import dev.mateux.caparao.model.RecipeStep
import dev.mateux.caparao.model.StepKind
import dev.mateux.caparao.ui.components.formatClockText
import dev.mateux.caparao.ui.components.parseClockText
import java.util.Locale

/** One step row as typed: weight in grams, start as m:ss, and an optional flow target in g/s. */
data class StepForm(val kind: StepKind, val weight: String = "", val start: String = "", val flowTarget: String = "")

/** A recipe as typed in the editor: text fields, checked by [validate] before saving. */
data class RecipeForm(
    val mode: BrewMode,
    val name: String = "",
    val method: String = "",
    val dose: String = "",
    val ratio: String = "",
    val grind: String = "",
    val temp: String = "",
    /** Espresso shot or filter total-time window, as m:ss. */
    val minTime: String = "",
    val maxTime: String = "",
    val notes: String = "",
    val steps: List<StepForm> = emptyList(),
) {
    /** The fields that are wrong; empty when the form can be saved. */
    fun validate(): Set<Field> = buildSet {
        if (name.isBlank()) add(Field.NAME)
        if (method.isBlank()) add(Field.METHOD)
        if (number(dose)?.takeIf { it in BrewLimits.DOSE_GRAMS } == null) add(Field.DOSE)
        if (number(ratio)?.takeIf { it in BrewLimits.RATIO } == null) add(Field.RATIO)
        if (temp.isNotBlank() && temp.trim().toIntOrNull()?.takeIf { it in BrewLimits.TEMPERATURE_CELSIUS } == null) add(Field.TEMP)
        when (mode) {
            BrewMode.ESPRESSO -> {
                val min = window(minTime)
                val max = window(maxTime)
                if (minTime.isNotBlank() && min == null) add(Field.WINDOW)
                if (maxTime.isNotBlank() && max == null) add(Field.WINDOW)
                if (min != null && max != null && min > max) add(Field.WINDOW)
            }
            BrewMode.FILTER, BrewMode.FRENCH_PRESS -> {
                val min = window(minTime)
                val max = window(maxTime)
                val hasMin = minTime.isNotBlank()
                val hasMax = maxTime.isNotBlank()
                if (mode == BrewMode.FILTER && (hasMin != hasMax || (hasMin && (min == null || max == null || min > max)))) {
                    add(Field.WINDOW)
                }
                if (steps.isEmpty()) add(Field.STEPS)
                if (mode == BrewMode.FRENCH_PRESS && steps.lastOrNull()?.kind != StepKind.PRESS) add(Field.STEPS)
                var lastStart = -1L
                var lastWeight = 0.0
                for ((index, step) in steps.withIndex()) {
                    val start = clock(step.start)
                    val weight = number(step.weight)
                    if (start == null || start < lastStart) add(Field.STEPS)
                    if (step.kind.pours && (weight == null || weight <= lastWeight || weight > BrewLimits.WATER_GRAMS.endInclusive)) add(Field.STEPS)
                    if (step.kind.pours && step.flowTarget.isNotBlank() && flowRate(step.flowTarget) == null) add(Field.STEPS)
                    if (step.kind == StepKind.PRESS && (mode != BrewMode.FRENCH_PRESS || index != steps.lastIndex)) add(Field.STEPS)
                    lastStart = start ?: lastStart
                    if (step.kind.pours) lastWeight = weight ?: lastWeight
                }
            }
        }
    }

    /** The recipe this form describes; call only when [validate] is empty. */
    fun toRecipe(id: String, createdAt: Long, now: Long) = Recipe(
        id = id,
        name = name.trim(),
        mode = mode,
        method = method.trim(),
        doseGrams = number(dose)!!,
        targetRatio = number(ratio)!!,
        grind = grind.trim().ifEmpty { null },
        waterTempCelsius = temp.trim().toIntOrNull(),
        notes = notes.trim().ifEmpty { null },
        targetMinMs = when (mode) {
            BrewMode.ESPRESSO -> window(minTime)
            BrewMode.FILTER -> window(minTime)
            BrewMode.FRENCH_PRESS -> null
        },
        targetMaxMs = when (mode) {
            BrewMode.ESPRESSO -> window(maxTime)
            BrewMode.FILTER -> window(maxTime)
            BrewMode.FRENCH_PRESS -> null
        },
        steps = when (mode) {
            BrewMode.ESPRESSO -> emptyList()
            BrewMode.FILTER, BrewMode.FRENCH_PRESS ->
                steps.map {
                    RecipeStep(
                        it.kind,
                        if (it.kind.pours) number(it.weight) else null,
                        clock(it.start)!!,
                        if (it.kind.pours) flowRate(it.flowTarget) else null,
                    )
                }
        },
        createdAt = createdAt,
        updatedAt = now,
    )

    /** The X of 1:X total: water (filter) or beverage (espresso); null while the dose or ratio is not a number. */
    val targetGrams: Double? get() = number(dose)?.let { d -> number(ratio)?.let { d * it } }

    enum class Field { NAME, METHOD, DOSE, RATIO, TEMP, WINDOW, STEPS }

    companion object {
        private fun flowRate(text: String): Double? = number(text)?.takeIf { it.isFinite() }

        /** Suggestions for [method]; any text is allowed. */
        val METHODS = mapOf(
            BrewMode.FILTER to listOf("V60", "Melitta", "Kalita", "AeroPress", "Chemex", "Origami"),
            BrewMode.ESPRESSO to listOf("Espresso"),
            BrewMode.FRENCH_PRESS to listOf("French press"),
        )

        fun of(recipe: Recipe, locale: Locale) = RecipeForm(
            mode = recipe.mode,
            name = recipe.name,
            method = recipe.method,
            dose = plain(recipe.doseGrams, locale),
            ratio = plain(recipe.targetRatio, locale),
            grind = recipe.grind.orEmpty(),
            temp = recipe.waterTempCelsius?.toString().orEmpty(),
            minTime = recipe.targetMinMs?.let(::clockText).orEmpty(),
            maxTime = recipe.targetMaxMs?.let(::clockText).orEmpty(),
            notes = recipe.notes.orEmpty(),
            steps = recipe.steps.map {
                StepForm(
                    it.kind,
                    it.targetWeightGrams?.let { w -> plain(w, locale) }.orEmpty(),
                    clockText(it.targetAtMs),
                    it.targetFlowGramsPerSecond?.let { rate -> plain(rate, locale) }.orEmpty(),
                )
            }.let { steps ->
                if (recipe.mode == BrewMode.FRENCH_PRESS && steps.lastOrNull()?.kind != StepKind.PRESS) {
                    steps + StepForm(StepKind.PRESS, start = clockText(BrewLimits.DEFAULT_STEEP_MS))
                } else {
                    steps
                }
            },
        )

        /** A new pour-based recipe: one bloom at 0:00, plus the French press steep deadline. */
        fun new(mode: BrewMode) = RecipeForm(
            mode = mode,
            method = METHODS.getValue(mode).first(),
            steps = when (mode) {
                BrewMode.ESPRESSO -> emptyList()
                BrewMode.FILTER -> listOf(StepForm(StepKind.BLOOM, start = "0:00"))
                BrewMode.FRENCH_PRESS -> listOf(StepForm(StepKind.BLOOM, start = "0:00"), StepForm(StepKind.PRESS, start = clockText(BrewLimits.DEFAULT_STEEP_MS)))
            },
        )

        /** A comma or a dot. */
        fun number(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

        /** m:ss or plain seconds, from one second to ten minutes. */
        private fun window(text: String): Long? = clock(text)?.takeIf { it in 1_000..600_000 }

        /** m:ss, or plain seconds. */
        fun clock(text: String): Long? = parseClockText(text)

        fun clockText(ms: Long): String = formatClockText(ms)

        /** No trailing ",0": 18 and 16,7. */
        fun plain(value: Double, locale: Locale): String =
            if (value % 1.0 == 0.0) value.toLong().toString() else String.format(locale, "%.1f", value)
    }
}
