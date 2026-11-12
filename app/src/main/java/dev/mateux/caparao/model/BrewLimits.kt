package dev.mateux.caparao.model

/** Shared dose, yield, ratio, temperature and steep bounds used by recipe entry and brew planning. */
object BrewLimits {
    val DOSE_GRAMS = 1.0..100.0
    val RATIO = 0.5..30.0
    val WATER_GRAMS = 1.0..2000.0
    val TEMPERATURE_CELSIUS = 1..100
    val STEEP_MS = 10_000L..3_600_000L
    const val DEFAULT_STEEP_MS = 4 * 60 * 1000L
}
