package io.github.mateuxlucax.caparao.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Compose screens that #312 profiles, measured with FrameTimingMetric on the release-like
 * benchmarkRelease variant: the Brews list and a brew detail, then a filter brew through its live
 * readout with the scripted scale. Each journey runs interpreted and with the Baseline Profile so
 * the issue can post both frame-time medians.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BrewBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun brewsListAndDetailWithoutProfile() = brewsListAndDetail(CompilationMode.None())

    @Test
    fun brewsListAndDetailWithProfile() = brewsListAndDetail(baselineProfile())

    @Test
    fun filterBrewLiveWithoutProfile() = filterBrewLive(CompilationMode.None())

    @Test
    fun filterBrewLiveWithProfile() = filterBrewLive(baselineProfile())

    private fun baselineProfile() = CompilationMode.Partial(BaselineProfileMode.Require, warmupIterations = WARMUP_ITERATIONS)

    /** Brews scrolled, then the first brew detail with its chart drawn in. */
    private fun brewsListAndDetail(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.WARM,
        iterations = ITERATIONS,
    ) {
        startActivityAndWait { intent ->
            intent.putExtra(EXTRA_BENCHMARK_ONBOARDED, true)
            intent.putExtra(EXTRA_BENCHMARK_SEED_BREWS, true)
        }
        device.awaitText("Brews")
        device.clickText("Brews")
        device.awaitTextContains("→")
        repeat(3) {
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight * 3 / 4,
                device.displayWidth / 2,
                device.displayHeight / 4,
                100,
            )
        }
        device.waitForIdle(2_000)
        // The list can still carry momentum after a quick swipe; click only once it is still.
        Thread.sleep(1_000)
        device.clickTextContains("→")
        device.awaitText("Edit notes")
    }

    /**
     * A filter free brew to the live screen, then a scripted pour. Cold starts keep each iteration on a fresh
     * process: a warm launch reuses the scripted scale, which does not replay its connect event.
     */
    private fun filterBrewLive(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
    ) {
        startActivityAndWait { intent ->
            intent.putExtra(EXTRA_BENCHMARK_ONBOARDED, true)
            intent.putExtra(EXTRA_SCRIPTED_API, true)
        }
        openFilterBrewToLive()
        pourToLive()
        Thread.sleep(LIVE_MEASURE_MS)
    }
}
