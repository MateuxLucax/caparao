package io.github.mateuxlucax.caparao.data.export

import io.github.mateuxlucax.caparao.data.exporter.ExporterClient
import io.github.mateuxlucax.caparao.model.BrewWithSamples
import org.json.JSONArray

/**
 * Exports the phone's brews without new dependencies (#76). The JSON is the contract's brew array, so a file can be
 * re-posted to any server and is checked by the same rules as the upload body.
 */
object BrewExporter {
    /** One JSON object per brew, exactly the body of `POST /api/v1/brews`, curve included. */
    fun json(brews: List<BrewWithSamples>): String = JSONArray().apply {
        brews.forEach { put(ExporterClient.body(it.brew, it.samples)) }
    }.toString(2)
}
