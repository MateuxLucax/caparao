package io.github.mateuxlucax.caparao.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateuxlucax.caparao.data.local.RecipeRepository
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.Recipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The Recipes tab: every recipe, split by mode; null until the first read. */
class RecipesViewModel(repository: RecipeRepository) : ViewModel() {
    val recipes: StateFlow<Map<BrewMode, List<Recipe>>?> = repository.recipes
        .map { all -> BrewMode.entries.associateWith { mode -> all.filter { it.mode == mode } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _mode = MutableStateFlow(BrewMode.FILTER)

    /** The mode shown; here and not in the screen, which leaves the composition while a recipe is open. */
    val mode: StateFlow<BrewMode> = _mode.asStateFlow()

    fun show(mode: BrewMode) {
        _mode.value = mode
    }
}
