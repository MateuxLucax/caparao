package io.github.mateuxlucax.caparao.ui.brews

import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode

/** What the trends view follows: a recipe, or the brews of a mode that followed none (docs/adr/0022-compare-and-trends.md). */
data class TrendKey(val mode: BrewMode, val recipeId: String?) {
    fun matches(brew: Brew) = brew.mode == mode && brew.recipeId == recipeId

    /** For rememberSaveable: "MODE" or "MODE:recipeId". */
    fun encode() = recipeId?.let { "${mode.name}:$it" } ?: mode.name

    companion object {
        fun of(brew: Brew) = TrendKey(brew.mode, brew.recipeId)

        fun decode(value: String): TrendKey {
            val mode = value.substringBefore(':')
            return TrendKey(BrewMode.valueOf(mode), value.substringAfter(':', "").ifEmpty { null })
        }
    }
}

/** Compare selection and trend picking, pure so they are unit tested (docs/adr/0022-compare-and-trends.md). */
object Comparison {
    /** Two to four brews per comparison. */
    const val MIN = 2
    const val MAX = 4

    /** Number of brews the trends view shows. */
    const val TREND_SIZE = 10

    /**
     * [brew] added to or removed from [selected]. A brew of another mode than the selected ones, or a fifth one, is
     * ignored: same-mode only keeps the axes meaningful.
     */
    fun toggle(selected: List<String>, brew: Brew, all: List<Brew>): List<String> {
        if (brew.id in selected) return selected - brew.id
        if (selected.size >= MAX) return selected
        val mode = all.firstOrNull { it.id == selected.firstOrNull() }?.mode
        return if (mode == null || mode == brew.mode) selected + brew.id else selected
    }

    /** The brew of the same recipe (or same mode without a recipe) right before [brew], for "Compare with last". */
    fun previous(brew: Brew, all: List<Brew>): Brew? {
        val key = TrendKey.of(brew)
        return all.filter { it.id != brew.id && key.matches(it) && it.startedAt < brew.startedAt }.maxByOrNull { it.startedAt }
    }

    /** The last [TREND_SIZE] brews of [key], oldest first, so the trend reads left to right. */
    fun trend(key: TrendKey, all: List<Brew>): List<Brew> =
        all.filter(key::matches).sortedBy { it.startedAt }.takeLast(TREND_SIZE)
}
