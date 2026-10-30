package io.github.mateuxlucax.caparao.ui.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.ui.components.BrewModeGroup
import io.github.mateuxlucax.caparao.ui.components.EmptyState
import io.github.mateuxlucax.caparao.ui.components.icon
import io.github.mateuxlucax.caparao.ui.components.label
import io.github.mateuxlucax.caparao.ui.components.segmentedShape
import io.github.mateuxlucax.caparao.ui.format.Format

/** The Recipes tab (wireframe 1): one mode at a time, a card per recipe, and a button for a new one. */
@Composable
fun RecipesScreen(viewModel: RecipesViewModel, onOpen: (Recipe) -> Unit, onNew: (BrewMode) -> Unit, modifier: Modifier = Modifier) {
    val all by viewModel.recipes.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            BrewModeGroup(mode, viewModel::show, Modifier.padding(vertical = 12.dp), icons = true)
            val recipes = all?.get(mode) ?: return@Column
            if (recipes.isEmpty()) {
                EmptyState(R.drawable.ic_menu_book, stringResource(R.string.no_recipes_title), stringResource(R.string.no_recipes_text), Modifier.padding(top = 48.dp))
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(recipes, key = { _, recipe -> recipe.id }) { index, recipe ->
                        RecipeRow(recipe, segmentedShape(index, recipes.size)) { onOpen(recipe) }
                    }
                }
            }
        }
        MediumExtendedFloatingActionButton(
            onClick = { onNew(mode) },
            icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
            text = { Text(stringResource(R.string.new_recipe)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
}

@Composable
private fun RecipeRow(recipe: Recipe, shape: Shape, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(recipe.name, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(recipeSummary(recipe)) },
        trailingContent = { Text(recipe.method, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.fillMaxWidth().clip(shape).clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

/** "18 g · 1:16.7 · 300 g of water", or "18 g → 43.2 g · 28–34 s" for espresso. Shared with the brew's recipe picker. */
@Composable
fun recipeSummary(recipe: Recipe): String {
    val locale = LocalConfiguration.current.locales[0]
    val dose = Format.grams(recipe.doseGrams, locale)
    val target = Format.grams(recipe.targetYieldGrams, locale)
    return when (recipe.mode) {
        BrewMode.FILTER -> stringResource(R.string.recipe_summary_filter, dose, Format.ratio(recipe.targetRatio, locale), target)
        BrewMode.ESPRESSO -> {
            val min = recipe.targetMinMs
            val max = recipe.targetMaxMs
            if (min != null && max != null) {
                stringResource(R.string.recipe_summary_espresso, dose, target, (min / 1000).toString(), (max / 1000).toString())
            } else {
                stringResource(R.string.recipe_summary_espresso_no_window, dose, target)
            }
        }
    }
}
