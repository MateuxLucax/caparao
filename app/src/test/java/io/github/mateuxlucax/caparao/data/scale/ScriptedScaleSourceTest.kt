package io.github.mateuxlucax.caparao.data.scale

import io.github.mateuxlucax.caparao.model.ScaleBattery
import io.github.mateuxlucax.caparao.model.ScaleError
import io.github.mateuxlucax.caparao.model.ScaleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScriptedScaleSourceTest {
    @Test
    fun `scenarios advance through decoded frames on virtual time`() {
        listOf("filter", "espresso", "french-press").forEach { name ->
            val source = source(name)
            source.advanceTo(0)
            assertEquals(ScaleState.Connected(ScaleBattery(80, 3)), source.state.value)
            assertEquals(0.0, source.reading.value?.weightGrams ?: -1.0, 0.0)
            source.advanceTo(if (name == "french-press") 240_000 else 300_000)
            assertTrue("$name should reach a final water weight", (source.reading.value?.weightGrams ?: 0.0) > 0)
        }
    }

    @Test
    fun `relative ramps use dose and recipe target values`() {
        val source = source("espresso")
        source.advanceTo(1_000)
        source.tare()
        source.advanceTo(24_000)
        assertEquals(36.0, source.reading.value?.weightGrams ?: 0.0, 0.0)
    }

    @Test
    fun `exception scenarios reach connection failure drop and overload`() {
        val failed = source("connection-failed")
        failed.advanceTo(300)
        assertEquals(ScaleState.Failed(ScaleError.NOT_FOUND, false), failed.state.value)

        val dropped = source("scale-drop")
        dropped.advanceTo(500)
        assertEquals(ScaleState.Failed(ScaleError.CONNECTION_LOST, true), dropped.state.value)

        val overloaded = source("overload")
        overloaded.advanceTo(500)
        assertTrue(overloaded.reading.value?.overload == true)
        assertEquals(1300.0, overloaded.reading.value?.weightGrams ?: 0.0, 0.0)
    }

    @Test
    fun `battery push and negative boot reading use the frame decoder`() {
        val source = ScriptedScaleSource(
            CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
            """{"events":[{"at_ms":0,"type":"connected"},{"at_ms":1,"type":"battery","delivery":"push","bars":2,"percent":55},{"at_ms":2,"type":"negative_boot"}]}""",
            autoRun = false,
            monotonicMs = { 0L },
        )
        source.advanceTo(1)
        assertEquals(ScaleState.Connected(ScaleBattery(55, 2)), source.state.value)
        source.advanceTo(2)
        assertEquals(-2.0, source.reading.value?.weightGrams ?: 0.0, 0.0)
    }

    @Test
    fun `CSV parser interpolates samples at ten hertz without HTTP`() {
        val scenario = ScriptedScenarioParser.fromCsv("""t_ms,weight_g,event
0,0,
300,3,
""".trimIndent())
        val events = scenario.getJSONArray("events")
        assertEquals(4, events.length())
        assertEquals(1.0, events.getJSONObject(1).getDouble("grams"), 0.0)
        assertEquals(100L, events.getJSONObject(1).getLong("at_ms"))
    }

    @Test
    fun `CSV parser rejects non-numeric measurements even when a row has an event`() {
        listOf(
            "t_ms,weight_g,event\n0,not-a-number,connected",
            "t_ms,weight_g,event,flow_gps\n0,,connected,not-a-number",
        ).forEach { csv ->
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                ScriptedScenarioParser.fromCsv(csv)
            }
        }
    }

    @Test
    fun `hold events emit repeated decoded frames`() {
        val source = ScriptedScaleSource(
            CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
            """{"events":[{"at_ms":0,"type":"hold","grams":2,"until_ms":300,"every_ms":100}]}""",
            autoRun = false, monotonicMs = { 0L },
        )
        assertEquals(4, source.position.getInt("total"))
        source.advanceTo(300)
        assertEquals(2.0, source.reading.value?.weightGrams ?: 0.0, 0.0)
    }

    @Test
    fun `flow-only JSON rows derive weight and contradictory rows fail`() {
        val source = ScriptedScaleSource(
            CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
            """{"events":[{"at_ms":0,"type":"weight","flow_gps":2},{"at_ms":1000,"type":"weight","flow_gps":2}]}""",
            autoRun = false, monotonicMs = { 0L },
        )
        source.advanceTo(1000)
        assertEquals(2.0, source.reading.value?.weightGrams ?: 0.0, 0.0)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ScriptedScaleSource(
                CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
                """{"events":[{"at_ms":0,"type":"weight","grams":1,"flow_gps":2}]}""",
                autoRun = false, monotonicMs = { 0L },
            )
        }
    }

    @Test
    fun `scenario tare shifts subsequent raw Dot weights`() {
        val source = ScriptedScaleSource(
            CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()),
            """{"events":[{"at_ms":0,"type":"weight","grams":20},{"at_ms":10,"type":"tare"},{"at_ms":20,"type":"weight","grams":20}]}""",
            autoRun = false, monotonicMs = { 0L },
        )
        source.advanceTo(0)
        assertEquals(20.0, source.reading.value?.weightGrams ?: 0.0, 0.0)
        source.advanceTo(20)
        assertEquals(0.0, source.reading.value?.weightGrams ?: -1.0, 0.0)
    }

    @Test
    fun `pause and explicit virtual advance do not sleep`() {
        val source = source("filter")
        source.pause()
        source.advance(500)
        assertEquals(500L, source.nowMs)
        assertTrue(source.state.value is ScaleState.Connected)
    }

    private fun source(name: String): ScriptedScaleSource {
        val json = requireNotNull(javaClass.getResourceAsStream("/scenarios/$name.json"))
            .bufferedReader().use { it.readText() }
        return ScriptedScaleSource(CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher()), json, autoRun = false, monotonicMs = { 0L })
    }
}
