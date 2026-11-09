package io.github.mateuxlucax.caparao.data.export

import io.github.mateuxlucax.caparao.data.exporter.ExporterClient
import io.github.mateuxlucax.caparao.data.exporter.toContractValue
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.BrewSample
import io.github.mateuxlucax.caparao.model.BrewWithSamples
import io.github.mateuxlucax.caparao.model.Recipe
import org.json.JSONArray
import org.json.JSONObject
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
        brews.forEach { appendLine(brewRow(it.brew)) }
    }

    /** One row per sample, `at_ms` relative to the brew's start. */
    fun samplesCsv(brews: List<BrewWithSamples>): String = buildString {
        appendLine(SAMPLES_HEADER)
        brews.forEach { (brew, samples) ->
            samples.forEach { appendLine(sampleRow(brew.id, it)) }
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
     *
     * [brews] carries only the summary fields: [samples] is called once per brew and per entry that needs its curve,
     * and the archive streams that brew's part straight to the zip, so peak memory follows the largest curve instead
     * of the whole history (#319).
     */
    suspend fun writeZip(
        out: OutputStream,
        brews: List<Brew>,
        samples: suspend (String) -> List<BrewSample>,
        recipes: List<Recipe>,
        includeSamples: Boolean,
    ) {
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            zip.entry("brews.json") { writeJson(it, brews, samples) }
            zip.entry("brews.csv") { writeBrewsCsv(it, brews) }
            zip.entry("recipes.csv", recipesCsv(recipes))
            if (includeSamples) zip.entry("samples.csv") { writeSamplesCsv(it, brews, samples) }
        }
    }

    private suspend fun writeJson(out: OutputStream, brews: List<Brew>, samples: suspend (String) -> List<BrewSample>) {
        val writer = out.writer(Charsets.UTF_8)
        when (brews.size) {
            0 -> writer.write("[]")
            1 -> writer.write(singleBrewJson(brews.single(), samples(brews.single().id)))
            else -> {
                writer.write("[\n")
                brews.forEachIndexed { index, brew ->
                    if (index > 0) writer.write(",\n")
                    writer.write("  ")
                    writer.write(brewJson(brew, samples(brew.id)))
                }
                writer.write("\n]")
            }
        }
        writer.flush()
    }

    /** The whole array for one brew, exactly as the in-memory export prints it. */
    private fun singleBrewJson(brew: Brew, samples: List<BrewSample>): String =
        JSONArray().put(ExporterClient.body(brew, samples)).toString(2)

    /**
     * One brew's object as it appears at the second level of the indented array. org.json only indents from the top
     * level, so a two-element array puts the object at that level; the `null` placeholder is dropped. A one-element
     * array would print its fields two spaces shallower.
     */
    private fun brewJson(brew: Brew, samples: List<BrewSample>): String {
        val placeholder = ",\n  null\n]"
        val wrapped = JSONArray().put(ExporterClient.body(brew, samples)).put(JSONObject.NULL).toString(2)
        check(wrapped.startsWith("[\n  ") && wrapped.endsWith(placeholder)) { "unexpected JSON indentation" }
        return wrapped.substring("[\n  ".length, wrapped.length - placeholder.length)
    }

    private fun writeBrewsCsv(out: OutputStream, brews: List<Brew>) {
        val writer = out.writer(Charsets.UTF_8)
        writer.write(BREWS_HEADER)
        writer.write("\n")
        brews.forEach {
            writer.write(brewRow(it))
            writer.write("\n")
        }
        writer.flush()
    }

    private suspend fun writeSamplesCsv(out: OutputStream, brews: List<Brew>, samples: suspend (String) -> List<BrewSample>) {
        val writer = out.writer(Charsets.UTF_8)
        writer.write(SAMPLES_HEADER)
        writer.write("\n")
        for (brew in brews) {
            for (sample in samples(brew.id)) {
                writer.write(sampleRow(brew.id, sample))
                writer.write("\n")
            }
        }
        writer.flush()
    }

    private suspend fun ZipOutputStream.entry(name: String, content: String) = entry(name) { it.write(content.toByteArray(Charsets.UTF_8)) }

    private suspend fun ZipOutputStream.entry(name: String, write: suspend (OutputStream) -> Unit) {
        putNextEntry(ZipEntry(name))
        write(this)
        closeEntry()
    }

    private fun brewRow(brew: Brew): String = listOf(
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
    ).joinToString(",")

    private fun sampleRow(brewId: String, sample: BrewSample): String =
        listOf(csv(brewId), sample.atMs.toString(), number(sample.weightGrams), number(sample.flowGramsPerSecond)).joinToString(",")

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
