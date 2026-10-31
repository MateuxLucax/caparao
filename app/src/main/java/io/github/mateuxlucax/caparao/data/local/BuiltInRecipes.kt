package io.github.mateuxlucax.caparao.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.StepKind

/**
 * The recipes a new or upgraded install starts with, as ordinary editable rows (docs/adr/0018-recipes.md). They come
 * from the user's recipe guide: Lance Hedrick's V60 "One and Done" and "Big Brew", Não Sou Barista's Melitta 102 and
 * 103, and Pirados por Café's espresso on the Oster Xpert Perfect Brew. The names are the same in every language.
 */
object BuiltInRecipes {
    const val ESPRESSO_ID = "6c0b6f5e-2f6a-4c55-9a39-0d1f6f5a0001"
    const val V60_ID = "6c0b6f5e-2f6a-4c55-9a39-0d1f6f5a0002"
    const val MELITTA_ID = "6c0b6f5e-2f6a-4c55-9a39-0d1f6f5a0003"
    const val V60_BIG_ID = "6c0b6f5e-2f6a-4c55-9a39-0d1f6f5a0004"
    const val MELITTA_LITER_ID = "6c0b6f5e-2f6a-4c55-9a39-0d1f6f5a0005"

    /** The ids the first set of built-ins used; [MIGRATION_2_3] replaces those the user never edited. */
    val FIRST_IDS = listOf(ESPRESSO_ID, V60_ID, MELITTA_ID)

    fun all(now: Long) = listOf(
        Recipe(
            id = ESPRESSO_ID, name = "Espresso Oster", mode = BrewMode.ESPRESSO, method = "Espresso",
            doseGrams = 19.0, targetRatio = 2.2, grind = "10", waterTempCelsius = 92, targetMinMs = 27_000, targetMaxMs = 33_000,
            notes = "Pirados por Café. Sour: grind 2 clicks finer. Bitter: 88 °C. The built-in grinder runs faster as it warms.",
            createdAt = now, updatedAt = now,
        ),
        Recipe(
            id = V60_ID, name = "V60 One and Done", mode = BrewMode.FILTER, method = "V60",
            doseGrams = 15.0, targetRatio = 16.7, grind = "Coarse, about 1 mm", waterTempCelsius = 91,
            notes = "Lance Hedrick. The bed should drain by 0:50–1:00. Total 2:00–2:30.",
            steps = listOf(
                RecipeStep(StepKind.BLOOM, 45.0, 0),
                RecipeStep(StepKind.POUR, 90.0, 30_000),
                RecipeStep(StepKind.POUR, 250.0, 60_000),
            ),
            createdAt = now, updatedAt = now,
        ),
        Recipe(
            id = MELITTA_ID, name = "Melitta 102", mode = BrewMode.FILTER, method = "Melitta",
            doseGrams = 20.0, targetRatio = 15.0, grind = "Medium-coarse", waterTempCelsius = 94,
            notes = "Não Sou Barista. Fold the filter seams. One steady circular pour. Total 2:40–2:50.",
            steps = listOf(
                RecipeStep(StepKind.BLOOM, 40.0, 0),
                RecipeStep(StepKind.POUR, 300.0, 30_000),
            ),
            createdAt = now, updatedAt = now,
        ),
        Recipe(
            id = V60_BIG_ID, name = "V60 Big Brew", mode = BrewMode.FILTER, method = "V60",
            doseGrams = 30.0, targetRatio = 15.0, grind = "Medium-coarse", waterTempCelsius = 87,
            notes = "Lance Hedrick. First bloom gently, about 5 g/s; last pour in the center, as fast as the kettle allows. Total 2:00–2:30.",
            steps = listOf(
                RecipeStep(StepKind.BLOOM, 90.0, 0),
                RecipeStep(StepKind.BLOOM, 155.0, 20_000),
                RecipeStep(StepKind.BLOOM, 205.0, 40_000),
                RecipeStep(StepKind.POUR, 450.0, 60_000),
            ),
            createdAt = now, updatedAt = now,
        ),
        Recipe(
            id = MELITTA_LITER_ID, name = "Melitta 103 1 L", mode = BrewMode.FILTER, method = "Melitta",
            doseGrams = 66.0, targetRatio = 15.9, grind = "Coarse, French press", waterTempCelsius = 93,
            notes = "Não Sou Barista. Circular pour to 600 g, then in the center. Total about 4:30; stir when it ends.",
            steps = listOf(
                RecipeStep(StepKind.BLOOM, 120.0, 0),
                RecipeStep(StepKind.POUR, 600.0, 30_000),
                RecipeStep(StepKind.POUR, 1050.0, 120_000),
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
