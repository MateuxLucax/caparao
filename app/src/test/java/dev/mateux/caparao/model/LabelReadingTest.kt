package dev.mateux.caparao.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LabelReadingTest {
    private val today = LocalDate.of(2026, 11, 15)
    private val knownRoasters = listOf("Sítio Baixadão", "Café da Serra")
    private val knownNotes = listOf(
        "Chocolate",
        "frutas vermelhas",
        "Caramel",
        "Jasmim",
        "Laranja",
        "Mel",
        "Amêndoa",
    )

    private fun parse(vararg lines: String) = readLabel(
        lines.mapIndexed { index, text -> LabelLine(text, 100 - index) },
        knownRoasters,
        knownNotes,
        today,
    )

    @Test
    fun readsPortugueseLabelFields() {
        val reading = parse(
            "SÍTIO BAIXADÃO",
            "250 g",
            "Torra 10/11/2026",
            "Notas: frutas vermelhas, Chocolate",
        )

        assertEquals("Sítio Baixadão", reading.roaster)
        assertEquals(250, reading.weightGrams)
        assertEquals(LocalDate.of(2026, 11, 10), reading.roastDate)
        assertEquals(listOf("frutas vermelhas", "Chocolate"), reading.tastingNotes)
    }

    @Test
    fun readsEnglishLabelAndDateOnFollowingLine() {
        val reading = parse("Roasted on", "02/01/2026", "1 kg", "Caramel")

        assertEquals(1000, reading.weightGrams)
        assertEquals(LocalDate.of(2026, 1, 2), reading.roastDate)
        assertEquals(listOf("Caramel"), reading.tastingNotes)
    }

    @Test
    fun picksLargestOfSeveralWeightsAndReadsDecimalComma() {
        assertEquals(1000, parse("250 g", "1 kg").weightGrams)
        assertEquals(500, parse("0,5 kg").weightGrams)
        assertEquals(250, parse("250g").weightGrams)
    }

    @Test
    fun ignoresBestBeforeAndManufacturingDatesWithoutRoastDate() {
        assertNull(parse("Best before 12/12/2026", "Caramel").roastDate)
        assertNull(parse("Validade 12/12/2026", "Fabricação 01/01/2026").roastDate)
        assertNull(parse("Val. 12/12/2026", "Caramel").roastDate)
    }

    @Test
    fun ignoresFutureAndInvalidRoastDates() {
        assertNull(parse("Torra 20/11/2026").roastDate)
        assertNull(parse("Roast 31/02/2026").roastDate)
        assertNull(parse("Validade 01/01/2027", "Torra 20/11/2026").roastDate)
    }

    @Test
    fun associatesDatesWithTheNearestLabelAndRejectsShortYears() {
        assertEquals(LocalDate.of(2026, 1, 2), parse("Torra 02/01/2026 Validade 02/01/2027").roastDate)
        assertNull(parse("Torra 02/01/26").roastDate)
        assertNull(parse("Torra", "Validade 02/01/2026").roastDate)
    }

    @Test
    fun retainsMixedCandidateLinesWithWeightOrKnownNotes() {
        assertEquals(listOf("Serra 250 G", "Chocolate Hills"), parse("SERRA 250 G", "CHOCOLATE HILLS").candidates)
    }

    @Test
    fun matchesNotesIgnoringCaseAndAccentsInLabelOrder() {
        val reading = parse("JASMIM e laranja", "AMÊNDOA, chocolate", "frutas vermelhas")

        assertEquals(listOf("Jasmim", "Laranja", "Amêndoa", "Chocolate", "frutas vermelhas"), reading.tastingNotes)
    }

    @Test
    fun returnsAtMostSixDistinctNotesAndRequiresWholeWords() {
        val reading = parse(
            "chocolates caramelizado",
            "Mel, chocolate e frutas vermelhas",
            "Jasmim, Laranja, Amêndoa, Caramel",
        )

        assertEquals(listOf("Mel", "Chocolate", "frutas vermelhas", "Jasmim", "Laranja", "Amêndoa"), reading.tastingNotes)
    }

    @Test
    fun returnsTallestUsefulCandidatesFirstAndFormatsCapitalizedAccents() {
        val reading = readLabel(
            listOf(
                LabelLine("CAFÉ DA SERRA", 50),
                LabelLine("SÍTIO BAIXADÃO", 45),
                LabelLine("123456789", 100),
                LabelLine("250 g", 95),
                LabelLine("Rua das Flores, 10", 90),
                LabelLine("CAFÉ ESPECIAL", 80),
                LabelLine("MICROLOTE 42", 70),
                LabelLine("CATUAÍ AMARELO", 60),
                LabelLine("CERRADO MINEIRO", 55),
                LabelLine("PROCESSO NATURAL", 40),
                LabelLine("ALTITUDE 1200 M", 30),
                LabelLine("LOTE PREMIADO", 20),
                LabelLine("EDIÇÃO LIMITADA", 10),
            ),
            knownRoasters,
            knownNotes,
            today,
        )

        assertEquals("Café da Serra", reading.roaster)
        assertEquals(
            listOf("Café Especial", "Microlote 42", "Catuaí Amarelo", "Cerrado Mineiro", "Processo Natural", "Altitude 1200 M", "Lote Premiado", "Edição Limitada"),
            reading.candidates,
        )
        assertEquals(8, reading.candidates.size)
    }

    @Test
    fun doesNotGuessCoffeeNameOrOriginWithoutKnownRoasterMatch() {
        val reading = parse("SERRA DO CAPARAÓ", "CATUAÍ AMARELO", "250 g")

        assertNull(reading.roaster)
        assertEquals(listOf("Serra Do Caparaó", "Catuaí Amarelo"), reading.candidates)
    }

    @Test
    fun mergeKeepsFirstFrontValuesWhenBackAddsFields() {
        val front = LabelReading(weightGrams = 250, roaster = "Café", tastingNotes = listOf("Chocolate"), candidates = listOf("Serra"))
        val back = LabelReading(
            weightGrams = 500,
            roastDate = LocalDate.of(2026, 1, 1),
            roaster = "Outro café",
            tastingNotes = listOf("chocolate", "Caramel"),
            candidates = listOf("sérra", "Brasil"),
        )

        val merged = mergeLabelReadings(front, back)
        assertEquals(250, merged.weightGrams)
        assertEquals(LocalDate.of(2026, 1, 1), merged.roastDate)
        assertEquals("Café", merged.roaster)
        assertEquals(listOf("Chocolate", "Caramel"), merged.tastingNotes)
        assertEquals(listOf("Serra", "Brasil"), merged.candidates)
    }

    @Test
    fun mergeKeepsFirstBackValuesWhenBackWasReadFirst() {
        val back = LabelReading(weightGrams = 500, roastDate = LocalDate.of(2026, 1, 1), roaster = "Café")
        val front = LabelReading(weightGrams = 250, roastDate = LocalDate.of(2026, 1, 2), roaster = "Outro café")

        val merged = mergeLabelReadings(back, front)
        assertEquals(500, merged.weightGrams)
        assertEquals(LocalDate.of(2026, 1, 1), merged.roastDate)
        assertEquals("Café", merged.roaster)
    }

    @Test
    fun mergingSameSideTwiceDoesNotDuplicateValues() {
        val side = LabelReading(
            weightGrams = 250,
            roastDate = LocalDate.of(2026, 1, 1),
            roaster = "Café",
            tastingNotes = listOf("Chocolate", "Caramel"),
            candidates = listOf("Serra", "Brasil"),
        )

        assertEquals(side, mergeLabelReadings(side, side))
    }

    @Test
    fun mergePreservesUserEnteredValuesAndAppendsDistinctReadings() {
        val entered = LabelReading(
            weightGrams = 350,
            roastDate = LocalDate.of(2026, 2, 3),
            roaster = "Typed roaster",
            tastingNotes = listOf("My note"),
            candidates = listOf("User's origin"),
        )
        val detected = LabelReading(
            weightGrams = 250,
            roastDate = LocalDate.of(2026, 1, 1),
            roaster = "Detected roaster",
            tastingNotes = listOf("my NOTE", "Chocolate"),
            candidates = listOf("user's origin", "Coffee name"),
        )

        val merged = mergeLabelReadings(entered, detected)
        assertEquals(350, merged.weightGrams)
        assertEquals(LocalDate.of(2026, 2, 3), merged.roastDate)
        assertEquals("Typed roaster", merged.roaster)
        assertEquals(listOf("My note", "Chocolate"), merged.tastingNotes)
        assertEquals(listOf("User's origin", "Coffee name"), merged.candidates)
    }
}
