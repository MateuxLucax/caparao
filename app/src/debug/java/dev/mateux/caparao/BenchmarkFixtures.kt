package dev.mateux.caparao

import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.BrewSample
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
    private const val BREWS_PER_DAY = 2
    private const val DAYS = 14
    private const val SAMPLES = 120
    private const val SAMPLE_PERIOD_MS = 250L
    private const val HOUR_MS = 60L * 60L * 1000L
    private const val DAY_MS = 24L * HOUR_MS

    @JvmStatic
    fun seed(container: AppContainer) = runBlocking(Dispatchers.IO) {
        // A repeated benchmark launch (WARM/HOT startup) must not duplicate the list.
        if (container.brews.latest() != null) return@runBlocking
        val now = System.currentTimeMillis()
        repeat(BREWS_PER_DAY * DAYS) { index ->
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
                    startedAt = now - (index / BREWS_PER_DAY) * DAY_MS - (if (index % BREWS_PER_DAY == 0) 8 else 2) * HOUR_MS,
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
