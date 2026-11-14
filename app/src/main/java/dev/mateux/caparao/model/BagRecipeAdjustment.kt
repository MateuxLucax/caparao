package dev.mateux.caparao.model

/** Phone-only values tuned for one coffee bag and recipe (#460). Null values follow the recipe. */
data class BagRecipeAdjustment(
    val bagId: String,
    val recipeId: String,
    val grind: Int? = null,
    val waterTempCelsius: Int? = null,
    val doseGrams: Double? = null,
    /** Set after a brew lands on target and tastes balanced. */
    val tuned: Boolean = false,
)

/** Why a coffee's recipe adjustment changed. Suggestions are tied to the brew that produced them. */
enum class BagRecipeAdjustmentReason {
    MANUAL,
    SUGGESTION,
}

/** A local record of the previous and new values for one adjustment change. */
data class BagRecipeAdjustmentHistory(
    val id: Long,
    val bagId: String,
    val recipeId: String,
    val changedAt: Long,
    val oldGrind: Int?,
    val newGrind: Int?,
    val oldWaterTempCelsius: Int?,
    val newWaterTempCelsius: Int?,
    val oldDoseGrams: Double?,
    val newDoseGrams: Double?,
    val oldTuned: Boolean,
    val newTuned: Boolean,
    val reason: BagRecipeAdjustmentReason,
    /** The source brew for a suggestion; kept as an id even if that brew is later deleted. */
    val brewId: String?,
)
