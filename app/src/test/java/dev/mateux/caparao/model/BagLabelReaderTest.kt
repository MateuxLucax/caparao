package dev.mateux.caparao.model

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class BagLabelReaderTest {
    private val today = LocalDate.of(2026, 11, 16)
    private val notes = listOf("Caramel", "Caramelo", "Red fruit", "Frutas vermelhas", "Chocolate", "Cítrico", "Citrus")

    @Test fun portugueseAndEnglishLabelFixtures() {
        fun roastDate(value: String) = LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val fixtures = listOf(
            Fixture(listOf("TORRADO EM 10/11/2026", "250 g", "CHOCOLATE E CARAMELO", "SÍTIO BAIXADÃO"), 250.0, roastDate("2026-11-10"), listOf("Chocolate", "Caramelo"), listOf("Sítio Baixadão")),
            Fixture(listOf("Roasted on 2026-11-12", "1 kg", "Red fruit", "Fazenda Alta"), 1000.0, roastDate("2026-11-12"), listOf("Red fruit"), listOf("Fazenda Alta")),
            Fixture(listOf("Torra", "09/11/2026", "Frutas vermelhas"), null, roastDate("2026-11-09"), listOf("Frutas vermelhas"), emptyList()),
            Fixture(listOf("Roast 11.11.2026", "250g", "Citrus"), 250.0, roastDate("2026-11-11"), listOf("Citrus"), emptyList()),
            Fixture(listOf("Roasted on 20/11/2026", "500 g", "Chocolate"), 500.0, null, listOf("Chocolate"), emptyList()),
            Fixture(listOf("Validade 10/12/2026", "Best before 2027-01-01", "250 g"), 250.0, null, emptyList(), emptyList()),
        )
        fixtures.forEach { fixture ->
            val result = BagLabelReader.read(fixture.lines.mapIndexed { index, text -> LabelLine(text, 10f + index) }, emptyList(), notes, today)
            assertEquals(fixture.weightGrams, result.weightGrams)
            assertEquals(fixture.roastedAt, result.roastedAt)
            assertEquals(fixture.tastingNotes, result.tastingNotes)
            assertEquals(fixture.candidates, result.candidates)
        }
    }

    private data class Fixture(
        val lines: List<String>,
        val weightGrams: Double?,
        val roastedAt: Long?,
        val tastingNotes: List<String>,
        val candidates: List<String>,
    )

    @Test fun textNextToRoastDateRemainsAnUnrecognizedCandidate() {
        val result = BagLabelReader.read(
            listOf(LabelLine("Torrado em 10/11/2026", 30f), LabelLine("Caparaó natural", 20f)),
            emptyList(), emptyList(), today,
        )
        assertEquals(listOf("Caparaó Natural"), result.candidates)
    }

    @Test fun largestWeightAndAccentedCapitalsKeepWords() {
        val result = BagLabelReader.read(
            listOf(LabelLine("SÍTIO BAIXADÃO", 20f), LabelLine("Bourbon amarelo", 15f), LabelLine("250 g 1 kg 0,5 kg", 10f)),
            listOf("Sítio Baixadão"), emptyList(), today,
        )
        assertEquals(1000.0, result.weightGrams!!, 0.0)
        assertEquals("Sítio Baixadão", result.roaster)
        assertEquals("Bourbon Amarelo", result.candidates.first())
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
