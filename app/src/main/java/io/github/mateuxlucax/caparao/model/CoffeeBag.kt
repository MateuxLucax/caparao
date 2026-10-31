package io.github.mateuxlucax.caparao.model

/**
 * A bag of coffee beans on the shelf. Brews point to it by name, through their coffee note, so the bag needs nothing
 * from the brew table or the server: [usedGrams] is the dose of every brew with that coffee since the bag was added.
 */
data class CoffeeBag(
    val id: String,
    val name: String,
    val roaster: String? = null,
    /** Free text: origin, variety, process. */
    val origin: String? = null,
    /** Epoch millis of the roast day; null when unknown. */
    val roastedAt: Long? = null,
    val weightGrams: Double,
    val notes: String? = null,
    /** Empty or put away: hidden from the coffee suggestions. */
    val finished: Boolean = false,
    val createdAt: Long,
    val usedGrams: Double = 0.0,
) {
    val leftGrams: Double get() = (weightGrams - usedGrams).coerceAtLeast(0.0)

    /** Whole days since the roast, at [now]; null without a roast date. */
    fun daysSinceRoast(now: Long): Long? = roastedAt?.let { ((now - it) / DAY_MS).coerceAtLeast(0) }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
