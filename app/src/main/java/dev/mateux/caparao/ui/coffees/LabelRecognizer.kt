package dev.mateux.caparao.ui.coffees

import android.content.Context
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.mateux.caparao.model.LabelLine
import kotlinx.coroutines.tasks.await
import java.io.File

/** On-device text recognition for a temporary coffee label photo. */
fun interface LabelRecognizer {
    suspend fun recognize(photo: File): List<LabelLine>
}

class MlKitLabelRecognizer(context: Context) : LabelRecognizer {
    private val appContext = context.applicationContext

    override suspend fun recognize(photo: File): List<LabelLine> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val result = recognizer.process(InputImage.fromFilePath(appContext, android.net.Uri.fromFile(photo))).await()
            result.textBlocks.flatMap { block -> block.lines.map { line ->
                LabelLine(line.text, line.boundingBox?.height()?.toFloat() ?: 0f)
            } }
        } finally {
            recognizer.close()
        }
    }
}

