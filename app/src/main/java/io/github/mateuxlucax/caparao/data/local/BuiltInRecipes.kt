package io.github.mateuxlucax.caparao.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.StepKind

/**
 * The recipes a new or upgraded install starts with, as ordinary editable rows (docs/adr/0018-recipes.md). The
 * espresso comes from the first brews recorded on the exporter; the V60 follows the common 15 g : 250 g recipe in five
 * pours (James Hoffmann's "better 1 cup"); the Melitta is the usual cone recipe at 1:16, a bloom and three pours.
 * The names are the same in every language.
 */
object BuiltInRecipes {
    const val ESPRESSO_ID = "6c0b6f5e-2f6a-4c55-9a39-0d1f6f5a0001"
    const val V60_ID = "6c0b6f5e-2f6a-4c55-9a39-0d1f6f5a0002"
    const val MELITTA_ID = "6c0b6f5e-2f6a-4c55-9a39-0d1f6f5a0003"

    fun all(now: Long) = listOf(
        Recipe(
            id = ESPRESSO_ID, name = "Espresso 18 g", mode = BrewMode.ESPRESSO, method = "Espresso",
            doseGrams = 18.0, targetRatio = 2.4, targetMinMs = 28_000, targetMaxMs = 34_000,
            createdAt = now, updatedAt = now,
        ),
        Recipe(
            id = V60_ID, name = "V60", mode = BrewMode.FILTER, method = "V60",
            doseGrams = 15.0, targetRatio = 16.7, waterTempCelsius = 96,
            steps = listOf(
                RecipeStep(StepKind.BLOOM, 50.0, 0),
                RecipeStep(StepKind.POUR, 100.0, 45_000),
                RecipeStep(StepKind.POUR, 150.0, 70_000),
                RecipeStep(StepKind.POUR, 200.0, 80_000),
                RecipeStep(StepKind.POUR, 250.0, 90_000),
            ),
            createdAt = now, updatedAt = now,
        ),
        Recipe(
            id = MELITTA_ID, name = "Melitta", mode = BrewMode.FILTER, method = "Melitta",
            doseGrams = 20.0, targetRatio = 16.0, waterTempCelsius = 94,
            steps = listOf(
                RecipeStep(StepKind.BLOOM, 40.0, 0),
                RecipeStep(StepKind.POUR, 160.0, 45_000),
                RecipeStep(StepKind.POUR, 240.0, 90_000),
                RecipeStep(StepKind.POUR, 320.0, 135_000),
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
