package dev.mateux.caparao.model

/**
 * A bag of coffee beans on the shelf. Brews link to it on the phone only (#288), so the server never sees the bag.
 * [usedGrams], [brewCount], [lastBrewedAt] and [averageRating] sum up the brews linked to the bag.
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
    val brewCount: Int = 0,
    /** Epoch millis of the newest linked brew's start; null when none. */
    val lastBrewedAt: Long? = null,
    /** Average rating of the linked brews that have one; null when none has one. */
    val averageRating: Double? = null,
    /** Ordered tasting profile; at most six short notes, stored on this phone only. */
    val tastingNotes: List<String> = emptyList(),
) {
    val leftGrams: Double get() = (weightGrams - usedGrams).coerceAtLeast(0.0)

    /** Whole days since the roast, at [now]; null without a roast date. */
    fun daysSinceRoast(now: Long): Long? = roastedAt?.let { ((now - it) / DAY_MS).coerceAtLeast(0) }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
