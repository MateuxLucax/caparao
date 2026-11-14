package dev.mateux.caparao.ui.coffees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import dev.mateux.caparao.data.local.BagRecipeAdjustmentRepository
import dev.mateux.caparao.data.local.BrewRepository
import dev.mateux.caparao.data.local.RecipeRepository
import dev.mateux.caparao.model.BagRecipeAdjustment
import dev.mateux.caparao.model.Recipe
import kotlinx.coroutines.flow.combine
import dev.mateux.caparao.data.local.CoffeeBagRepository
import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.CoffeeBag
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A recipe the bag was brewed with and what this coffee changes in it; null when it follows the recipe. */
data class RecipeAdjustment(val recipe: Recipe, val adjustment: BagRecipeAdjustment?)

/** Whether the adjustment changes anything; one put back to the recipe keeps its row, with nothing in it. */
val BagRecipeAdjustment.overrides: Boolean get() = grind != null || waterTempCelsius != null || doseGrams != null

/** One coffee bag: what is left, the brews that used it, and finishing or deleting it (#290, C4 and C5). */
class CoffeeBagDetailViewModel(
    private val id: String,
    private val bags: CoffeeBagRepository,
    brews: BrewRepository,
    recipes: RecipeRepository,
    adjustments: BagRecipeAdjustmentRepository,
) : ViewModel() {
    /** Null while loading, and once the bag is deleted. */
    val bag: StateFlow<CoffeeBag?> = bags.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val brews: StateFlow<List<Brew>> = brews.forBag(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The recipes this bag has been brewed with, newest brew first, each with its adjustment if it has one (#462). */
    val recipeAdjustments: StateFlow<List<RecipeAdjustment>> =
        combine(brews.forBag(id), recipes.recipes, adjustments.forBag(id)) { brewed, all, adjusted ->
            brewed.mapNotNull { it.recipeId }.distinct().mapNotNull { recipeId ->
                all.firstOrNull { it.id == recipeId }?.let { recipe ->
                    RecipeAdjustment(recipe, adjusted.firstOrNull { it.recipeId == recipeId }?.takeIf { it.overrides })
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFinished(finished: Boolean) {
        val current = bag.value ?: return
        viewModelScope.launch { bags.save(current.copy(finished = finished)) }
    }

    /** The brews keep their coffee name; the screen closes once this is called. */
    fun delete() {
        viewModelScope.launch { bags.delete(id) }
    }

    companion object {
        val BAG_ID = object : CreationExtras.Key<String> {}
    }
}
