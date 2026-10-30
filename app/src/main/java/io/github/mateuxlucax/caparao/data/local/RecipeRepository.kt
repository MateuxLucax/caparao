package io.github.mateuxlucax.caparao.data.local

import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.StepKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Recipes on the phone (docs/adr/0018-recipes.md); they never leave it, only a brew's copy of the name does. */
class RecipeRepository(private val dao: RecipeDao) {
    /** Every recipe with its steps, by mode, then name. */
    val recipes: Flow<List<Recipe>> = combine(dao.observeAll(), dao.observeAllSteps()) { rows, steps ->
        val byRecipe = steps.groupBy { it.recipeId }
        rows.map { it.toModel(byRecipe[it.id].orEmpty()) }
    }

    suspend fun get(id: String): Recipe? = dao.get(id)?.toModel(dao.steps(id))

    /** Inserts or replaces [recipe] and its steps. */
    suspend fun save(recipe: Recipe) = dao.save(recipe.toEntity(), recipe.stepEntities())

    /** Brews that followed it keep their copy of the name. */
    suspend fun delete(id: String) = dao.delete(id)
}

internal fun Recipe.toEntity() = RecipeEntity(
    id = id,
    name = name,
    mode = mode.name,
    method = method,
    doseGrams = doseGrams,
    targetRatio = targetRatio,
    grind = grind,
    waterTempCelsius = waterTempCelsius,
    notes = notes,
    targetMinMs = targetMinMs,
    targetMaxMs = targetMaxMs,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun Recipe.stepEntities() = steps.mapIndexed { i, step ->
    RecipeStepEntity(id, i, step.kind.name, step.targetWeightGrams, step.targetAtMs)
}

internal fun RecipeEntity.toModel(steps: List<RecipeStepEntity>) = Recipe(
    id = id,
    name = name,
    mode = BrewMode.valueOf(mode),
    method = method,
    doseGrams = doseGrams,
    targetRatio = targetRatio,
    grind = grind,
    waterTempCelsius = waterTempCelsius,
    notes = notes,
    targetMinMs = targetMinMs,
    targetMaxMs = targetMaxMs,
    steps = steps.sortedBy { it.position }.map { RecipeStep(StepKind.valueOf(it.kind), it.targetWeightGrams, it.targetAtMs) },
    createdAt = createdAt,
    updatedAt = updatedAt,
)
