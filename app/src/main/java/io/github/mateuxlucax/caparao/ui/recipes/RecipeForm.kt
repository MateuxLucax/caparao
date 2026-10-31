package io.github.mateuxlucax.caparao.ui.recipes

import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.StepKind
import java.util.Locale

/** One step row as typed: weight in grams, start as m:ss. */
data class StepForm(val kind: StepKind, val weight: String = "", val start: String = "")

/** A recipe as typed in the editor: text fields, checked by [validate] before saving. */
data class RecipeForm(
    val mode: BrewMode,
    val name: String = "",
    val method: String = "",
    val dose: String = "",
    val ratio: String = "",
    val grind: String = "",
    val temp: String = "",
    /** Espresso: the shot window in seconds. */
    val minSeconds: String = "",
    val maxSeconds: String = "",
    val notes: String = "",
    val steps: List<StepForm> = emptyList(),
) {
    /** The fields that are wrong; empty when the form can be saved. */
    fun validate(): Set<Field> = buildSet {
        if (name.isBlank()) add(Field.NAME)
        if (method.isBlank()) add(Field.METHOD)
        if (number(dose)?.takeIf { it in DOSE } == null) add(Field.DOSE)
        if (number(ratio)?.takeIf { it in RATIO } == null) add(Field.RATIO)
        if (temp.isNotBlank() && temp.trim().toIntOrNull()?.takeIf { it in TEMP } == null) add(Field.TEMP)
        if (mode == BrewMode.ESPRESSO) {
            val min = seconds(minSeconds)
            val max = seconds(maxSeconds)
            if (minSeconds.isNotBlank() && min == null) add(Field.WINDOW)
            if (maxSeconds.isNotBlank() && max == null) add(Field.WINDOW)
            if (min != null && max != null && min > max) add(Field.WINDOW)
        } else {
            if (steps.isEmpty()) add(Field.STEPS)
            var lastStart = -1L
            var lastWeight = 0.0
            for (step in steps) {
                val start = clock(step.start)
                val weight = number(step.weight)
                if (start == null || start < lastStart) add(Field.STEPS)
                if (step.kind.pours && (weight == null || weight <= lastWeight || weight > MAX_WATER)) add(Field.STEPS)
                lastStart = start ?: lastStart
                if (step.kind.pours) lastWeight = weight ?: lastWeight
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
        targetMinMs = if (mode == BrewMode.ESPRESSO) seconds(minSeconds)?.times(1000) else null,
        targetMaxMs = if (mode == BrewMode.ESPRESSO) seconds(maxSeconds)?.times(1000) else null,
        steps = if (mode == BrewMode.FILTER) {
            steps.map { RecipeStep(it.kind, if (it.kind.pours) number(it.weight) else null, clock(it.start)!!) }
        } else {
            emptyList()
        },
        createdAt = createdAt,
        updatedAt = now,
    )

    /** The X of 1:X total: water (filter) or beverage (espresso); null while the dose or ratio is not a number. */
    val targetGrams: Double? get() = number(dose)?.let { d -> number(ratio)?.let { d * it } }

    enum class Field { NAME, METHOD, DOSE, RATIO, TEMP, WINDOW, STEPS }

    companion object {
        val DOSE = 1.0..100.0
        val RATIO = 0.5..30.0
        val TEMP = 1..100
        private const val MAX_WATER = 2000.0

        /** Suggestions for [method]; any text is allowed. */
        val METHODS = mapOf(
            BrewMode.FILTER to listOf("V60", "Melitta", "Kalita", "AeroPress", "Chemex", "Origami"),
            BrewMode.ESPRESSO to listOf("Espresso"),
        )

        fun of(recipe: Recipe, locale: Locale) = RecipeForm(
            mode = recipe.mode,
            name = recipe.name,
            method = recipe.method,
            dose = plain(recipe.doseGrams, locale),
            ratio = plain(recipe.targetRatio, locale),
            grind = recipe.grind.orEmpty(),
            temp = recipe.waterTempCelsius?.toString().orEmpty(),
            minSeconds = recipe.targetMinMs?.let { (it / 1000).toString() }.orEmpty(),
            maxSeconds = recipe.targetMaxMs?.let { (it / 1000).toString() }.orEmpty(),
            notes = recipe.notes.orEmpty(),
            steps = recipe.steps.map { StepForm(it.kind, it.targetWeightGrams?.let { w -> plain(w, locale) }.orEmpty(), clockText(it.targetAtMs)) },
        )

        /** A new recipe: one bloom at 0:00 for filter. */
        fun new(mode: BrewMode) = RecipeForm(
            mode = mode,
            method = METHODS.getValue(mode).first(),
            steps = if (mode == BrewMode.FILTER) listOf(StepForm(StepKind.BLOOM, start = "0:00")) else emptyList(),
        )

        /** A comma or a dot. */
        fun number(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

        private fun seconds(text: String): Long? = text.trim().toLongOrNull()?.takeIf { it in 1..600 }

        /** m:ss, or plain seconds. */
        fun clock(text: String): Long? {
            val t = text.trim()
            val parts = t.split(':')
            val secs = when (parts.size) {
                1 -> parts[0].toLongOrNull()
                2 -> parts[0].toLongOrNull()?.let { m -> parts[1].takeIf { it.length == 2 }?.toLongOrNull()?.takeIf { it < 60 }?.let { m * 60 + it } }
                else -> null
            }
            return secs?.takeIf { it in 0..3600 }?.times(1000)
        }

        fun clockText(ms: Long): String = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)

        /** No trailing ",0": 18 and 16,7. */
        fun plain(value: Double, locale: Locale): String =
            if (value % 1.0 == 0.0) value.toLong().toString() else String.format(locale, "%.1f", value)
    }
}
