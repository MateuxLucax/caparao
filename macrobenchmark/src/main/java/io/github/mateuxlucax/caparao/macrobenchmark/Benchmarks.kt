package io.github.mateuxlucax.caparao.macrobenchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.net.HttpURLConnection
import java.net.URL

/** The release applicationId; the benchmark variants do not add the debug suffix. */
internal const val TARGET_PACKAGE = "io.github.mateuxlucax.caparao"

internal const val EXTRA_SCRIPTED_API = "scripted_api"
internal const val EXTRA_BENCHMARK_ONBOARDED = "benchmark_onboarded"
internal const val EXTRA_BENCHMARK_SEED_BREWS = "benchmark_seed_brews"

internal const val TIMEOUT_MS = 10_000L
internal const val ITERATIONS = 5
internal const val WARMUP_ITERATIONS = 3
internal const val LIVE_MEASURE_MS = 3_000L

/** The dose, weigh and prepare steps of a free filter brew, ending on the live screen while it tares. */
internal fun MacrobenchmarkScope.openFilterBrewToLive() {
    // Filter always opens on the free brew, whose planned dose enables Next; a recipe chip may sit off-screen.
    device.awaitEnabled("Filter")
    device.clickText("Filter")
    device.awaitEnabled("Next")
    device.clickText("Next")
    // An open coffee bag inserts the coffee step before the beans (#291); don't depend on the device's data.
    if (device.awaitAnyText("Weigh the beans", "Which coffee?") == "Which coffee?") {
        device.clickText("Next")
        device.awaitText("Weigh the beans")
    }
    device.clickText("Next")
    device.awaitText("Prepare the filter")
    device.clickText("Ready")
    device.awaitText("Taring…")
}

/** Posts the pour and waits for the first water to start the brew, so the measured frames cover the live screen. */
internal fun MacrobenchmarkScope.pourToLive() {
    ScriptedScale.postPour()
    device.awaitText("Brewing")
}

internal fun UiDevice.awaitText(text: String) {
    check(wait(Until.hasObject(By.text(text)), TIMEOUT_MS)) { "\"$text\" did not appear within ${TIMEOUT_MS}ms" }
}

internal fun UiDevice.awaitEnabled(text: String) {
    check(wait(Until.hasObject(By.text(text).enabled(true)), TIMEOUT_MS)) { "\"$text\" was not enabled within ${TIMEOUT_MS}ms" }
}

/** The first of [texts] to appear, so an optional step (the coffee) doesn't make the journey order-dependent. */
internal fun UiDevice.awaitAnyText(vararg texts: String): String {
    val deadline = System.currentTimeMillis() + TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
        texts.firstOrNull { hasObject(By.text(it)) }?.let { return it }
        Thread.sleep(50)
    }
    throw AssertionError("none of ${texts.toList()} appeared within ${TIMEOUT_MS}ms")
}

internal fun UiDevice.awaitTextContains(text: String) {
    check(wait(Until.hasObject(By.textContains(text)), TIMEOUT_MS)) { "\"$text\" did not appear within ${TIMEOUT_MS}ms" }
}

internal fun UiDevice.clickText(text: String) {
    val node = checkNotNull(wait(Until.findObject(By.text(text)), TIMEOUT_MS)) { "\"$text\" was not found" }
    node.click()
}

internal fun UiDevice.clickTextContains(text: String) {
    // A swipe can leave a clipped row under the top bar; click a fully visible one, by coordinates so a recycled
    // accessibility node cannot go stale between the lookup and the tap.
    repeat(3) {
        val nodes = wait(Until.findObjects(By.textContains(text)), TIMEOUT_MS) ?: emptyList()
        val bounds = nodes.mapNotNull { node ->
            runCatching { node.visibleBounds }.getOrNull()?.takeIf { it.height() > 40 && it.top > 200 && it.bottom < displayHeight - 100 }
        }.minByOrNull { it.top }
        if (bounds != null) {
            click(bounds.centerX(), bounds.centerY())
            return
        }
        waitForIdle(1_000)
    }
    throw AssertionError("no fully visible \"$text\" was found")
}

/** Drives the app's loopback scripted scale, the same API the README documents for captures. */
internal object ScriptedScale {
    // localhost, not 127.0.0.1: the benchmark library's network security config only allows localhost cleartext.
    private const val SCENARIO_URL = "http://localhost:8765/scenario"

    /** Ten seconds of a filter pour: the live readout, flow and guide animate while frames are measured. */
    private val POUR = """
        t_ms,weight_g,event
        0,0,connected
        500,0,
        1000,0,
        1500,10,
        2000,20,
        2500,30,
        3000,45,
        3500,60,
        4000,80,
        4500,100,
        5000,120,
        5500,140,
        6000,160,
        6500,180,
        7000,200,
        7500,220,
        8000,240,
        8500,260,
        9000,280,
        9500,300,
        10000,320,
    """.trimIndent()

    /** The server is created with the activity; retry until it is listening (a WARM launch recreates it). */
    fun postPour() {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        var failure: Exception? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                val connection = URL(SCENARIO_URL).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 2_000
                connection.readTimeout = 2_000
                connection.setRequestProperty("Content-Type", "text/csv")
                connection.outputStream.use { it.write(POUR.toByteArray()) }
                if (connection.responseCode == HttpURLConnection.HTTP_OK) return
                failure = IllegalStateException("scripted scale answered ${connection.responseCode}")
            } catch (e: Exception) {
                failure = e
            }
            Thread.sleep(100)
        }
        throw AssertionError("the scripted scale did not accept the pour", failure)
    }
}
