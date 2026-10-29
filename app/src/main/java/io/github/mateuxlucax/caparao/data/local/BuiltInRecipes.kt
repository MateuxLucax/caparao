package io.github.mateuxlucax.caparao.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.StepKind

/**
 * The two recipes a new or upgraded install starts with, as ordinary editable rows, taken from the first brews
 * recorded on the exporter (docs/adr/0018-recipes.md). The names are the same in every language.
 */
object BuiltInRecipes {
    const val ESPRESSO_ID = "6c0b6f5e-2f6a-4c55-9a39-0d1f6f5a0001"
    const val V60_ID = "6c0b6f5e-2f6a-4c55-9a39-0d1f6f5a0002"

    fun all(now: Long) = listOf(
        Recipe(
            id = ESPRESSO_ID, name = "Espresso 18 g", mode = BrewMode.ESPRESSO, method = "Espresso",
            doseGrams = 18.0, targetRatio = 2.4, targetMinMs = 28_000, targetMaxMs = 34_000,
            createdAt = now, updatedAt = now,
        ),
        Recipe(
            id = V60_ID, name = "V60", mode = BrewMode.FILTER, method = "V60",
            doseGrams = 18.0, targetRatio = 16.7,
            steps = listOf(
                RecipeStep(StepKind.BLOOM, 50.0, 0),
                RecipeStep(StepKind.POUR, 100.0, 45_000),
                RecipeStep(StepKind.POUR, 150.0, 85_000),
                RecipeStep(StepKind.POUR, 300.0, 120_000),
            ),
            createdAt = now, updatedAt = now,
        ),
    )

    /** Raw inserts: runs inside the migration and the create callback, before any DAO exists. */
    fun insert(db: SupportSQLiteDatabase, now: Long) {
        for (recipe in all(now)) {
            val row = recipe.toEntity()
            db.insert("recipes", SQLiteDatabase.CONFLICT_IGNORE, ContentValues().apply {
                put("id", row.id)
                put("name", row.name)
                put("mode", row.mode)
                put("method", row.method)
                put("dose_grams", row.doseGrams)
                put("target_ratio", row.targetRatio)
                put("grind", row.grind)
                put("water_temp_celsius", row.waterTempCelsius)
                put("notes", row.notes)
                put("target_min_ms", row.targetMinMs)
                put("target_max_ms", row.targetMaxMs)
                put("created_at", row.createdAt)
                put("updated_at", row.updatedAt)
            })
            for (step in recipe.stepEntities()) {
                db.insert("recipe_steps", SQLiteDatabase.CONFLICT_IGNORE, ContentValues().apply {
                    put("recipe_id", step.recipeId)
                    put("position", step.position)
                    put("kind", step.kind)
                    put("target_weight_grams", step.targetWeightGrams)
                    put("target_at_ms", step.targetAtMs)
                })
            }
        }
    }
}
