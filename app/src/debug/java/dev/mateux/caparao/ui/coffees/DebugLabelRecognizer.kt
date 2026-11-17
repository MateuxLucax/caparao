package dev.mateux.caparao.ui.coffees

import dev.mateux.caparao.model.LabelLine
import java.io.File

/** Predictable label text used only by the scripted API debug launch. */
class DebugLabelRecognizer : LabelRecognizer {
    override suspend fun recognize(photo: File) = listOf(
        LabelLine("Torra Alta", 40f), LabelLine("250 g", 36f),
        LabelLine("Torrado em 10/11/2026", 32f), LabelLine("Caparaó natural", 28f), LabelLine("Jasmim", 24f),
    )
}
