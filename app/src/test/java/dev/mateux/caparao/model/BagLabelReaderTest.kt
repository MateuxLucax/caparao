package dev.mateux.caparao.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BagLabelReaderTest {
    private val today = LocalDate.of(2026, 11, 16)
    private val notes = listOf("Caramel", "Caramelo", "Red fruit", "Frutas vermelhas", "Chocolate", "Cítrico", "Citrus")

    @Test fun portugueseAndEnglishLabelFixtures() {
        val fixtures = listOf(
            listOf("TORRADO EM 10/11/2026", "250 g", "CHOCOLATE E CARAMELO") to listOf("Chocolate", "Caramelo"),
            listOf("Roasted on 2026-11-12", "1 kg", "Red fruit") to listOf("Red fruit"),
            listOf("Torra", "09/11/2026", "Frutas vermelhas") to listOf("Frutas vermelhas"),
            listOf("Roast 11.11.2026", "250g", "Citrus") to listOf("Citrus"),
            listOf("Roasted on 20/11/2026", "500 g", "Chocolate") to listOf("Chocolate"),
            listOf("Validade 10/12/2026", "Best before 2027-01-01", "250 g") to emptyList(),
        )
        fixtures.forEach { (texts, expectedNotes) ->
            val result = BagLabelReader.read(texts.mapIndexed { index, text -> LabelLine(text, 10f + index) }, emptyList(), notes, today)
            assertEquals(expectedNotes, result.tastingNotes)
            if (texts.first().contains("Validade") || texts.first().contains("Best")) assertNull(result.roastedAt)
        }
    }

    @Test fun largestWeightAndAccentedCapitalsKeepWords() {
        val result = BagLabelReader.read(
            listOf(LabelLine("SÍTIO BAIXADÃO", 20f), LabelLine("250 g 1 kg 0,5 kg", 10f)),
            listOf("Sítio Baixadão"), emptyList(), today,
        )
        assertEquals(1000.0, result.weightGrams!!, 0.0)
        assertEquals("Sítio Baixadão", result.roaster)
        assertEquals("Sítio Baixadão", result.candidates.first())
    }

    @Test fun tallestCandidatesComeFirstAndNoiseIsSkipped() {
        val result = BagLabelReader.read(listOf(LabelLine("12", 40f), LabelLine("Rua das Flores 10", 35f), LabelLine("123456789012", 30f), LabelLine("Fazenda Boa Vista", 10f)), emptyList(), emptyList(), today)
        assertEquals(listOf("Fazenda Boa Vista"), result.candidates)
    }

    @Test fun mergeKeepsFirstValuesAndAddsUniqueNotesAndCandidates() {
        val front = BagLabelReading(250.0, 1, listOf("Chocolate"), "Torra Alta", listOf("Sítio Azul"))
        val back = BagLabelReading(500.0, 2, listOf("chocolate", "Caramelo"), "Outra", listOf("sitio azul", "Processo natural"))
        val expected = BagLabelReading(250.0, 1, listOf("Chocolate", "Caramelo"), "Torra Alta", listOf("Sítio Azul", "Processo natural"))
        assertEquals(expected, BagLabelReader.merge(front, back))
        assertEquals(BagLabelReading(500.0, 2, listOf("chocolate", "Caramelo"), "Outra", listOf("sitio azul", "Processo natural")), BagLabelReader.merge(back, front))
        assertEquals(front, BagLabelReader.merge(front, front))
    }

    @Test fun existingUserValuesWinDuringMerge() {
        val typed = BagLabelReading(340.0, 7, listOf("My note"), "My roaster", listOf("My coffee"))
        val scanned = BagLabelReading(250.0, 9, listOf("Other note"), "Other roaster", listOf("Other line"))
        assertEquals(BagLabelReading(340.0, 7, listOf("My note", "Other note"), "My roaster", listOf("My coffee", "Other line")), BagLabelReader.merge(typed, scanned))
    }
}
