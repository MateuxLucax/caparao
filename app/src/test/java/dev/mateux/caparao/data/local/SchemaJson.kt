package dev.mateux.caparao.data.local

import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Opens the exported schema JSON for [version] and creates its tables, indices and bookkeeping rows
 * in [db], exactly as `app/schemas/dev.mateux.caparao.data.local.BrewDatabase/<version>.json` describes them.
 * Shared by the migration tests so each one does not reimplement the schema-JSON → SQLite setup.
 */
fun createSchemaFromJson(db: SQLiteDatabase, version: Int) {
    val schema = JSONObject(File("schemas/dev.mateux.caparao.data.local.BrewDatabase/$version.json").readText())
        .getJSONObject("database")
    val entities = schema.getJSONArray("entities")
    for (index in 0 until entities.length()) {
        val entity = entities.getJSONObject(index)
        val table = entity.getString("tableName")
        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
        val indices = entity.optJSONArray("indices") ?: JSONArray()
        for (indexInEntity in 0 until indices.length()) {
            db.execSQL(indices.getJSONObject(indexInEntity).getString("createSql").replace("\${TABLE_NAME}", table))
        }
    }
    val setup = schema.getJSONArray("setupQueries")
    for (index in 0 until setup.length()) db.execSQL(setup.getString(index))
}
