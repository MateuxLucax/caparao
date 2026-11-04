package io.github.mateuxlucax.caparao.data.scale

import androidx.test.platform.app.InstrumentationRegistry
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.model.ScaleError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Confirms the debug app bundles the same JSON files used by the JVM virtual-clock tests. */
class ScriptedScenarioAssetTest {
    @Test
    fun scenariosLoadFromDebugAssetsAndDecodeFrames() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scenarios = requireNotNull(context.assets.list("scenarios")).toSet()
        val names = setOf("filter.json", "espresso.json", "french-press.json", "connection-failed.json", "scale-drop.json", "overload.json")
        assertTrue(scenarios.containsAll(names))
        names.forEach { name ->
            val json = context.assets.open("scenarios/$name").bufferedReader().use { it.readText() }
            val root = org.json.JSONObject(json)
            val captures = root.getJSONArray("captures")
            val lastCapture = captures.getJSONObject(captures.length() - 1).getLong("at_ms")
            val source = ScriptedScaleSource(
                CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), json,
                autoRun = false, monotonicMs = { 0L },
            )
            source.advanceTo(lastCapture)
            when (name) {
                "connection-failed.json" -> assertEquals(ScaleState.Failed(ScaleError.NOT_FOUND, false), source.state.value)
                "scale-drop.json" -> assertTrue(source.state.value is ScaleState.Connected)
                "overload.json" -> assertTrue(source.reading.value?.overload == true)
                "espresso.json" -> assertEquals(286.0, source.reading.value?.weightGrams ?: 0.0, 0.0)
                "filter.json" -> assertEquals(250.0, source.reading.value?.weightGrams ?: 0.0, 0.0)
                "french-press.json" -> assertEquals(0.0, source.reading.value?.weightGrams ?: -1.0, 0.0)
            }
        }
    }
}
