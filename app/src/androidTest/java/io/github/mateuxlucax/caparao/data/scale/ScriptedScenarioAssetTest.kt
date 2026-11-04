package io.github.mateuxlucax.caparao.data.scale

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

/** Confirms every scripted scenario file is available to the debug app. */
class ScriptedScenarioAssetTest {
    @Test
    fun scenariosLoadFromDebugAssets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scenarios = requireNotNull(context.assets.list("scenarios")).toSet()
        val names = setOf("filter.json", "espresso.json", "french-press.json", "connection-failed.json", "scale-drop.json", "overload.json")
        assertTrue(scenarios.containsAll(names))
        names.forEach { name ->
            val json = context.assets.open("scenarios/$name").bufferedReader().use { it.readText() }
            assertTrue("$name should not be empty", json.isNotBlank())
        }
    }
}
