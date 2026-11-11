package io.github.mateuxlucax.caparao

import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.BrewSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * Debug and benchmark variants only: fills an empty Brews tab with reproducible brews, so the
 * list and detail journeys have something to scroll and open (#312).
 *
 * The Benchmark Profile generator and the macrobenchmarks launch the app with
 * [DebugScaleHooks.EXTRA_BENCHMARK_SEED_BREWS]; the debug hook seeds the fixture before the first frame.
 */
object BenchmarkFixtures {
    private const val FILTER_BREWS = 12
    private const val ESPRESSO_BREWS = 12
    private const val SAMPLES = 120
    private const val SAMPLE_PERIOD_MS = 250L
    private const val HOUR_MS = 60L * 60L * 1000L

    @JvmStatic
    fun seed(container: AppContainer) = runBlocking(Dispatchers.IO) {
        // A repeated benchmark launch (WARM/HOT startup) must not duplicate the list.
        if (container.brews.latest() != null) return@runBlocking
        val now = System.currentTimeMillis()
        repeat(FILTER_BREWS + ESPRESSO_BREWS) { index ->
            val mode = if (index % 2 == 0) BrewMode.FILTER else BrewMode.ESPRESSO
            val filter = mode == BrewMode.FILTER
            val dose = if (filter) 20.0 else 18.0
            val yield = if (filter) 320.0 else 36.0
            val durationMs = SAMPLES * SAMPLE_PERIOD_MS
            val averageFlow = yield / (durationMs / 1000.0)
            val samples = List(SAMPLES) { sample ->
                BrewSample(
                    atMs = sample * SAMPLE_PERIOD_MS,
                    weightGrams = yield * (sample + 1) / SAMPLES.toDouble(),
                    flowGramsPerSecond = averageFlow,
                )
            }
            container.brews.save(
                Brew(
                    id = UUID.randomUUID().toString(),
                    mode = mode,
                    startedAt = now - index * HOUR_MS,
                    durationMs = durationMs,
                    doseGrams = dose,
                    yieldGrams = yield,
                    timeToFirstDropMs = if (filter) null else 4_000L,
                    flowAvgGramsPerSecond = averageFlow,
                    flowPeakGramsPerSecond = averageFlow * 1.2,
                    interrupted = false,
                    recipeName = if (filter) "V60" else "Espresso",
                    coffee = if (index % 4 == 0) "Benchmark coffee" else null,
                    rating = index % 5 + 1,
                ),
                samples,
            )
        }
    }
}
