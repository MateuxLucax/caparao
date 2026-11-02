package io.github.mateuxlucax.caparao.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewSample
import io.github.mateuxlucax.caparao.model.BrewWithSamples
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** What the share sheet needs: the file and its MIME type. */
data class ExportedFile(val uri: Uri, val mimeType: String)

/** Writes the exports to the cache and hands them to the share sheet through the FileProvider (#76). */
class BrewExportFiles(private val context: Context, private val repository: BrewRepository) {
    /** Every brew in one zip: contract JSON, `brews.csv` and, when asked, `samples.csv`. */
    suspend fun all(includeSamples: Boolean): ExportedFile = withContext(Dispatchers.IO) {
        val brews = repository.allWithSamples()
        val file = newFile("caparao-export.zip")
        file.outputStream().use { BrewExporter.writeZip(it, brews, includeSamples) }
        ExportedFile(uri(file), "application/zip")
    }

    /** One brew as contract JSON, curve included. */
    suspend fun one(brew: Brew, samples: List<BrewSample>): ExportedFile = withContext(Dispatchers.IO) {
        val file = newFile("caparao-brew-${brew.id}.json")
        file.writeText(BrewExporter.json(listOf(BrewWithSamples(brew, samples))))
        ExportedFile(uri(file), "application/json")
    }

    private fun newFile(name: String): File = File(context.cacheDir, "exports").apply { mkdirs() }.resolve(name)

    private fun uri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
