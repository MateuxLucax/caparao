package io.github.mateuxlucax.caparao.data.diagnostics

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import io.github.mateuxlucax.caparao.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
class CaptureStorageMigrationTest {
    @Test
    fun legacySessionsStayExcludedFromBackupAndTransferBeforeFirstLaunch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val parser = context.resources.getXml(R.xml.data_extraction_rules)
        val excludedByMode = mutableMapOf<String, MutableSet<String>>()
        var mode = ""
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "cloud-backup", "device-transfer" -> mode = parser.name
                    "exclude" -> if (parser.getAttributeValue(null, "domain") == "file") {
                        excludedByMode.getOrPut(mode) { mutableSetOf() } += parser.getAttributeValue(null, "path")
                    }
                }
            }
            parser.next()
        }
        assertTrue("diagnostics/" in excludedByMode["cloud-backup"].orEmpty())
        assertTrue("diagnostics/" in excludedByMode["device-transfer"].orEmpty())
    }

    @Test
    fun firstLaunchDeletesLocalOnlySessionsBeforeTheNewWriterRefreshes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val filesDir = Files.createTempDirectory("capture-migration-").toFile()
        val legacyFile = File(filesDir, "diagnostics/session.jsonl").apply {
            parentFile!!.mkdirs()
            writeText("local-only session")
        }
        val migrations = context.getSharedPreferences("capture-migration-${System.nanoTime()}", Context.MODE_PRIVATE)
        val sent = mutableListOf<File>()
        val writer = CaptureWriter(
            directory = File(filesDir, "captures"),
            scope = this,
            ioDispatcher = Dispatchers.IO,
            optedIn = { true },
            send = { file, _, _ -> sent += file; true },
        )

        assertTrue(withContext(Dispatchers.IO) { removeLegacyDiagnosticCaptures(filesDir, migrations) })
        writer.refresh()

        assertFalse(legacyFile.exists())
        assertTrue(sent.isEmpty())
        assertTrue(migrations.getBoolean("legacy_diagnostic_captures_removed", false))
        filesDir.deleteRecursively()
        Unit
    }
}
