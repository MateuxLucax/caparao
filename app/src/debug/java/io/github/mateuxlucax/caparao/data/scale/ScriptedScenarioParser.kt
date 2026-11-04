package io.github.mateuxlucax.caparao.data.scale

import org.json.JSONArray
import org.json.JSONObject

/** Converts CSV input to the same JSON event model used by scenario files, without depending on the HTTP layer. */
object ScriptedScenarioParser {
    fun fromCsv(csv: String): JSONObject {
        val lines = csv.lineSequence().filter { it.isNotBlank() }.map { it.split(',').map(String::trim) }.toList()
        require(lines.size >= 2) { "CSV needs a header and at least one row" }
        val columns = lines.first().map(String::lowercase)
        require(columns.containsAll(listOf("t_ms", "weight_g", "event"))) { "CSV columns must include t_ms, weight_g, event" }
        val timeIndex = columns.indexOf("t_ms")
        val weightIndex = columns.indexOf("weight_g")
        val eventIndex = columns.indexOf("event")
        val flowIndex = columns.indexOf("flow_gps")
        val points = lines.drop(1).map { row ->
            require(row.size == columns.size) { "CSV row has ${row.size} columns; expected ${columns.size}" }
            val time: Long = row[timeIndex].toLongOrNull() ?: throw IllegalArgumentException("t_ms must be an integer")
            val weight = row[weightIndex].numberOrNull("weight_g")
            val flow = if (flowIndex >= 0) row[flowIndex].numberOrNull("flow_gps") else null
            require(!(weight != null && flow != null)) { "CSV row cannot contain both weight_g and flow_gps" }
            require(weight != null || flow != null || row[eventIndex].isNotEmpty()) { "CSV row needs a weight, flow, or event" }
            CsvPoint(time, weight, flow, row[eventIndex])
        }
        require(points.zipWithNext().all { (first, second) -> first.atMs <= second.atMs }) { "CSV times must be ordered" }
        val events = JSONArray()
        var previousValue: CsvPoint? = null
        points.forEach { point ->
            val previous = previousValue
            if (previous != null && (previous.weight != null || previous.flow != null) && (point.weight != null || point.flow != null)) {
                val duration = point.atMs - previous.atMs
                var time = previous.atMs + SAMPLE_PERIOD_MS
                while (time < point.atMs) {
                    val fraction = (time - previous.atMs).toDouble() / duration.coerceAtLeast(1L)
                    val row = JSONObject().put("at_ms", time).put("type", "weight")
                    if (previous.weight != null && point.weight != null) {
                        row.put("grams", previous.weight + (point.weight - previous.weight) * fraction)
                    } else {
                        val startFlow = previous.flow ?: 0.0
                        val endFlow = point.flow ?: startFlow
                        row.put("flow_gps", startFlow + (endFlow - startFlow) * fraction)
                    }
                    events.put(row)
                    time += SAMPLE_PERIOD_MS
                }
            }
            when {
                point.weight != null -> events.put(JSONObject().put("at_ms", point.atMs).put("type", "weight").put("grams", point.weight))
                point.flow != null -> events.put(JSONObject().put("at_ms", point.atMs).put("type", "weight").put("flow_gps", point.flow))
            }
            if (point.event.isNotEmpty()) events.put(JSONObject().put("at_ms", point.atMs).put("type", point.event))
            if (point.weight != null || point.flow != null) previousValue = point
        }
        return JSONObject().put("name", "uploaded").put("events", events)
    }

    private data class CsvPoint(val atMs: Long, val weight: Double?, val flow: Double?, val event: String)

    private fun String.numberOrNull(column: String): Double? {
        if (isEmpty()) return null
        val value = toDoubleOrNull() ?: throw IllegalArgumentException("$column must be a number")
        require(value.isFinite()) { "$column must be a finite number" }
        return value
    }

    private const val SAMPLE_PERIOD_MS = 100L
}
