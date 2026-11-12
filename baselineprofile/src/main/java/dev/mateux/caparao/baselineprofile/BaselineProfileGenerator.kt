package dev.mateux.caparao.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/**
 * The three critical journeys of #312, collected into app/src/release/generated/baselineProfiles by
 * `./gradlew :app:generateReleaseBaselineProfile`.
 *
 * The generator runs against the app's nonMinifiedRelease variant, which carries the debug source set and
 * `SCRIPTED_SCALE` (see app/build.gradle.kts): the scripted scale stands in for the Dot, as on the emulator.
 * The UI labels are the English defaults; run the generator on an English-locale device or emulator.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    /** Cold start to Home, with onboarding already done. */
    @Test
    fun startupToHome() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        launchApp(onboarded = true)
    }

    /** A filter free brew with the scripted scale, through the dose and prepare steps to the live screen. */
    @Test
    fun filterBrewToLive() = rule.collect(packageName = TARGET_PACKAGE) {
        launchApp(onboarded = true, scriptedScale = true)
        openFilterBrewToLive()
        // The screen tares on Ready; the scripted pour resumes the scale and walks Taring through Brewing.
        ScriptedScale.postPour()
        device.awaitText("Brewing")
        Thread.sleep(LIVE_PROFILE_MS)
    }

    /** The Brews tab scrolled, then a brew detail with its chart. */
    @Test
    fun brewsListAndDetail() = rule.collect(packageName = TARGET_PACKAGE) {
        launchApp(onboarded = true, seededBrews = true)
        openBrewsThenDetail()
    }
}

/** The release applicationId; the benchmark variants do not add the debug suffix. */
private const val TARGET_PACKAGE = "dev.mateux.caparao"

private const val EXTRA_SCRIPTED_API = "scripted_api"
private const val EXTRA_BENCHMARK_ONBOARDED = "benchmark_onboarded"
private const val EXTRA_BENCHMARK_SEED_BREWS = "benchmark_seed_brews"

private const val TIMEOUT_MS = 10_000L
private const val LIVE_PROFILE_MS = 3_000L

/** Launches the app and waits for the Home tab, failing with the screen name when a step is missing. */
private fun MacrobenchmarkScope.launchApp(
    onboarded: Boolean,
    scriptedScale: Boolean = false,
    seededBrews: Boolean = false,
) {
    pressHome()
    startActivityAndWait { intent ->
        if (onboarded) intent.putExtra(EXTRA_BENCHMARK_ONBOARDED, true)
        if (scriptedScale) intent.putExtra(EXTRA_SCRIPTED_API, true)
        if (seededBrews) intent.putExtra(EXTRA_BENCHMARK_SEED_BREWS, true)
    }
    device.awaitText("Home")
}

private fun MacrobenchmarkScope.openFilterBrewToLive() {
    // The mode opens only once the scale connects: the scripted source reports connected before the first frame. Filter
    // always opens on the free brew, whose planned dose enables Next; a recipe chip may sit off-screen, so don't tap it.
    device.awaitEnabled("Filter")
    device.clickText("Filter")
    device.awaitEnabled("Next")
    device.clickText("Next")
    // An open coffee bag inserts the coffee step before the beans (#291); the benchmark must not depend on the device's data.
    if (device.awaitAnyText("Weigh the beans", "Which coffee?") == "Which coffee?") {
        device.clickText("Next")
        device.awaitText("Weigh the beans")
    }
    device.clickText("Next")
    device.awaitText("Prepare the filter")
    device.clickText("Ready")
    // The free filter brew tares straight away; the scripted pour below carries it to Brewing.
    device.awaitText("Taring…")
}

private fun MacrobenchmarkScope.openBrewsThenDetail() {
    device.clickText("Brews")
    device.awaitTextContains("→")
    device.swipe(
        device.displayWidth / 2,
        device.displayHeight * 3 / 4,
        device.displayWidth / 2,
        device.displayHeight / 4,
        100,
    )
    device.waitForIdle(2_000)
    // The list can still carry momentum after a quick swipe; click only once it is still.
    Thread.sleep(1_000)
    device.clickTextContains("→")
    device.awaitText("Edit notes")
}

private fun UiDevice.awaitText(text: String) {
    check(wait(Until.hasObject(By.text(text)), TIMEOUT_MS)) { "\"$text\" did not appear within ${TIMEOUT_MS}ms" }
}

private fun UiDevice.awaitEnabled(text: String) {
    check(wait(Until.hasObject(By.text(text).enabled(true)), TIMEOUT_MS)) { "\"$text\" was not enabled within ${TIMEOUT_MS}ms" }
}

/** The first of [texts] to appear, so an optional step (the coffee) doesn't make the journey order-dependent. */
private fun UiDevice.awaitAnyText(vararg texts: String): String {
    val deadline = System.currentTimeMillis() + TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
        texts.firstOrNull { hasObject(By.text(it)) }?.let { return it }
        Thread.sleep(50)
    }
    throw AssertionError("none of ${texts.toList()} appeared within ${TIMEOUT_MS}ms")
}

private fun UiDevice.awaitTextContains(text: String) {
    check(wait(Until.hasObject(By.textContains(text)), TIMEOUT_MS)) { "\"$text\" did not appear within ${TIMEOUT_MS}ms" }
}

private fun UiDevice.clickText(text: String) {
    val node = checkNotNull(wait(Until.findObject(By.text(text)), TIMEOUT_MS)) { "\"$text\" was not found" }
    node.click()
}

private fun UiDevice.clickTextContains(text: String) {
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
private object ScriptedScale {
    // localhost, not 127.0.0.1: the benchmark library's network security config only allows localhost cleartext.
    private const val SCENARIO_URL = "http://localhost:8765/scenario"

    /** Ten seconds of a filter pour: the live readout, flow and guide animate while the profile is collected. */
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

    /** The server is created with the activity; retry until it is listening (a benchmark launch may be cold). */
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
