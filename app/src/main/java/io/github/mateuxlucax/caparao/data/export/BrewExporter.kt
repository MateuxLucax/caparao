package io.github.mateuxlucax.caparao.data.export

import io.github.mateuxlucax.caparao.data.exporter.ExporterClient
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.BrewWithSamples
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
                    if (brew.mode == BrewMode.ESPRESSO) "espresso" else "filter",
                    csv(Instant.ofEpochMilli(brew.startedAt).toString()),
                    seconds(brew.durationMs),
                    number(brew.doseGrams),
                    number(brew.yieldGrams),
                    brew.timeToFirstDropMs?.let { seconds(it) }.orEmpty(),
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
     * The shareable archive: `brews.json` and `brews.csv` always, `samples.csv` only when [includeSamples]. The JSON
     * always carries the curve, since it is the re-postable copy.
     */
    fun writeZip(out: OutputStream, brews: List<BrewWithSamples>, includeSamples: Boolean) {
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            zip.entry("brews.json", json(brews))
            zip.entry("brews.csv", brewsCsv(brews))
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
}
