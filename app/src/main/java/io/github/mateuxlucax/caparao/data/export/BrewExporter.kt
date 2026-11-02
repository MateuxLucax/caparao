package io.github.mateuxlucax.caparao.data.export

import io.github.mateuxlucax.caparao.data.exporter.ExporterClient
import io.github.mateuxlucax.caparao.data.exporter.toContractValue
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.BrewWithSamples
import io.github.mateuxlucax.caparao.model.Recipe
import org.json.JSONArray
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports the phone's brews without new dependencies (#76). The JSON is the contract's brew array, so a file can be
 * re-posted to any server and is checked by the same rules as the upload body; the CSVs are for spreadsheets.
 */
object BrewExporter {
    /** One JSON object per brew, exactly the body of `POST /api/v1/brews`, curve included. */
    fun json(brews: List<BrewWithSamples>): String = JSONArray().apply {
        brews.forEach { put(ExporterClient.body(it.brew, it.samples)) }
    }.toString(2)

    /** One row per brew: the summary fields, the recipe name and the notes. */
    fun brewsCsv(brews: List<BrewWithSamples>): String = buildString {
        appendLine(BREWS_HEADER)
        brews.forEach { (brew, _) ->
            append(
                listOf(
                    csv(brew.id),
                    brew.mode.toContractValue(),
                    csv(Instant.ofEpochMilli(brew.startedAt).toString()),
                    seconds(brew.durationMs),
                    number(brew.doseGrams),
                    number(brew.yieldGrams),
                    when (brew.mode) {
                        BrewMode.ESPRESSO -> brew.timeToFirstDropMs?.let { seconds(it) }.orEmpty()
                        BrewMode.FILTER, BrewMode.FRENCH_PRESS -> ""
                    },
                    number(brew.flowAvgGramsPerSecond),
                    number(brew.flowPeakGramsPerSecond),
                    brew.interrupted.toString(),
                    csv(brew.recipeName),
                    csv(brew.coffee),
                    csv(brew.grind),
                    brew.waterTempCelsius?.toString().orEmpty(),
                    brew.rating?.toString().orEmpty(),
                    csv(brew.notes),
                ).joinToString(","),
            )
            appendLine()
        }
    }

    /** One row per sample, `at_ms` relative to the brew's start. */
    fun samplesCsv(brews: List<BrewWithSamples>): String = buildString {
        appendLine(SAMPLES_HEADER)
        brews.forEach { (brew, samples) ->
            samples.forEach { sample ->
                appendLine(listOf(csv(brew.id), sample.atMs.toString(), number(sample.weightGrams), number(sample.flowGramsPerSecond)).joinToString(","))
            }
        }
    }

    /**
     * One row per step, the recipe fields repeated; a recipe with no steps (espresso) still gets one row with the step
     * columns empty, so nothing is lost.
     */
    fun recipesCsv(recipes: List<Recipe>): String = buildString {
        appendLine(RECIPES_HEADER)
        recipes.forEach { recipe ->
            val fields = listOf(
                csv(recipe.id),
                csv(recipe.name),
                recipe.mode.toContractValue(),
                csv(recipe.method),
                number(recipe.doseGrams),
                number(recipe.targetRatio),
                number(recipe.targetYieldGrams),
                csv(recipe.grind),
                recipe.waterTempCelsius?.toString().orEmpty(),
                csv(recipe.notes),
                recipe.targetMinMs?.let { seconds(it) }.orEmpty(),
                recipe.targetMaxMs?.let { seconds(it) }.orEmpty(),
            )
            if (recipe.steps.isEmpty()) {
                appendLine(fields.joinToString(",") + ",,,,")
            } else {
                recipe.steps.forEachIndexed { index, step ->
                    appendLine(
                        (fields + listOf(
                            (index + 1).toString(),
                            step.kind.name.lowercase(),
                            step.targetWeightGrams?.let { number(it) }.orEmpty(),
                            seconds(step.targetAtMs),
                        )).joinToString(","),
                    )
                }
            }
        }
    }

    /**
     * The shareable archive: `brews.json`, `brews.csv` and `recipes.csv` always, `samples.csv` only when
     * [includeSamples]. The JSON always carries the curve, since it is the re-postable copy.
     */
    fun writeZip(out: OutputStream, brews: List<BrewWithSamples>, recipes: List<Recipe>, includeSamples: Boolean) {
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            zip.entry("brews.json", json(brews))
            zip.entry("brews.csv", brewsCsv(brews))
            zip.entry("recipes.csv", recipesCsv(recipes))
            if (includeSamples) zip.entry("samples.csv", samplesCsv(brews))
        }
    }

    private fun ZipOutputStream.entry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    /** Dot decimal separator, up to three decimals, whatever the device locale. */
    private fun number(value: Double): String {
        if (!value.isFinite()) return "0"
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    private fun seconds(ms: Long): String = number(ms / 1000.0)

    /** Quotes a field only when it needs it; quotes inside are doubled, per RFC 4180. */
    private fun csv(value: String?): String {
        if (value == null) return ""
        return if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    private const val BREWS_HEADER =
        "id,mode,started_at,duration_s,dose_g,yield_g,time_to_first_drop_s,flow_avg_gps,flow_peak_gps,interrupted," +
            "recipe_name,coffee,grind,water_temp_c,rating,notes"
    private const val SAMPLES_HEADER = "brew_id,at_ms,weight_g,flow_gps"
    private const val RECIPES_HEADER =
        "recipe_id,name,mode,method,dose_g,target_ratio,target_yield_g,grind,water_temp_c,notes,target_min_s,target_max_s," +
            "step_position,step_kind,step_weight_g,step_at_s"
}
