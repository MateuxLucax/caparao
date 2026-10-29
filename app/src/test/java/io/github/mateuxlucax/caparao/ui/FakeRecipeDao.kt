package io.github.mateuxlucax.caparao.ui

import io.github.mateuxlucax.caparao.data.local.RecipeDao
import io.github.mateuxlucax.caparao.data.local.RecipeEntity
import io.github.mateuxlucax.caparao.data.local.RecipeStepEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** The recipe DAO's behaviour in memory; the real queries are tested on a device (RecipeDaoTest). */
class FakeRecipeDao : RecipeDao {
    val recipes = MutableStateFlow<List<RecipeEntity>>(emptyList())
    val steps = MutableStateFlow<List<RecipeStepEntity>>(emptyList())

    override suspend fun upsert(recipe: RecipeEntity) {
        recipes.value = recipes.value.filterNot { it.id == recipe.id } + recipe
    }

    override suspend fun insertSteps(steps: List<RecipeStepEntity>) {
        this.steps.value += steps
    }

    override suspend fun deleteSteps(recipeId: String) {
        steps.value = steps.value.filterNot { it.recipeId == recipeId }
    }

    override fun observeAll() = recipes.map { list -> list.sortedWith(compareBy({ it.mode }, { it.name.lowercase() })) }
    override fun observeAllSteps() = steps.map { list -> list.sortedWith(compareBy({ it.recipeId }, { it.position })) }
    override suspend fun get(id: String) = recipes.value.find { it.id == id }
    override suspend fun steps(recipeId: String) = steps.value.filter { it.recipeId == recipeId }.sortedBy { it.position }

    override suspend fun delete(id: String) {
        recipes.value = recipes.value.filterNot { it.id == id }
        deleteSteps(id)
    }
}
