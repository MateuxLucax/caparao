package dev.mateux.caparao.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LabelReadingTest {
    private val today = LocalDate.of(2026, 11, 15)
    private fun parse(vararg text: String) = readLabel(text.mapIndexed { i, s -> LabelLine(s, 20 - i) }, listOf("Sítio Baixadão"), listOf("Chocolate", "frutas vermelhas", "Caramel"), today)

    @Test fun sixLabels() {
        val a = parse("SÍTIO BAIXADÃO", "250 g", "Torra 10/11/2026", "Chocolate e frutas vermelhas")
        assertEquals("Sítio Baixadão", a.roaster)
        assertEquals(250, a.weightGrams)
        assertEquals(LocalDate.of(2026, 11, 10), a.roastDate)
        assertEquals(listOf("Chocolate", "frutas vermelhas"), a.tastingNotes)
        assertEquals(1000, parse("250g", "1 kg").weightGrams)
        assertEquals(500, parse("0,5 kg").weightGrams)
        assertNull(parse("Best before 12/12/2026", "Caramel").roastDate)
        assertEquals(LocalDate.of(2026, 1, 2), parse("Roasted on", "02/01/2026").roastDate)
        assertNull(parse("Torra 20/11/2026", "Validade 01/01/2027").roastDate)
        assertEquals(listOf("Café Da Serra"), parse("CAFÉ DA SERRA", "123456789", "250g").candidates)
    }

    @Test fun mergeKeepsFirstValuesAndDeduplicates() {
        val front = LabelReading(weightGrams = 250, roaster = "Café", tastingNotes = listOf("Chocolate"), candidates = listOf("Serra"))
        val back = LabelReading(weightGrams = 500, roastDate = LocalDate.of(2026, 1, 1), tastingNotes = listOf("chocolate", "Caramel"), candidates = listOf("sérra", "Brasil"))
        assertEquals(250, mergeLabelReadings(front, back).weightGrams)
        assertEquals(500, mergeLabelReadings(back, front).weightGrams)
        assertEquals(listOf("Chocolate", "Caramel"), mergeLabelReadings(front, back).tastingNotes)
        assertEquals(listOf("Serra", "Brasil"), mergeLabelReadings(front, back).candidates)
        assertEquals(front, mergeLabelReadings(front, front))
        assertEquals("Typed", mergeLabelReadings(LabelReading(roaster = "Typed"), front).roaster)
    }
}
