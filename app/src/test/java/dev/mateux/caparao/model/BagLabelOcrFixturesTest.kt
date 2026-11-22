package dev.mateux.caparao.model

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What ML Kit returned for the maintainer's five bag photos in #555 (U1–U5 in `BagLabelFixturesTest`): lines in
 * ML Kit's order with their pixel heights, OCR mistakes kept ("lkg", "Perhlsensorial", "8amokaclube"). Company IDs,
 * addresses and phone numbers are placeholders. Front labels carry the coffee's name; the back sticker carries the
 * weight, the roast date and the roaster.
 */
class BagLabelOcrFixturesTest {
    private val today = LocalDate.of(2026, 11, 16)
    private val notes = listOf("Caramelo", "Chocolate", "Cítrico", "Frutas vermelhas", "Floral", "Castanhas")
    private val roasters = listOf("Unique Cafés", "Coffee Mais", "Moka Clube", "Provisório", "Giói")

    private fun lines(vararg lines: Pair<Float, String>) = lines.map { (height, text) -> LabelLine(text, height) }
    private fun read(lines: List<LabelLine>) = BagLabelReader.read(lines, roasters, notes, today)
    private fun day(value: String) = LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun BagLabelReading.check(weight: Double?, roast: String?, tasting: List<String>, roaster: String?) {
        assertEquals("weight", weight, weightGrams)
        assertEquals("roast date", roast?.let(::day), roastedAt)
        assertEquals("tasting notes", tasting, tastingNotes)
        assertEquals("roaster", roaster, this.roaster)
    }

    /** The OCR spellings of field names printed on every bag, which must not be offered as values. */
    private val misreadFieldNames = listOf("Variedad", "Perhlsensorial", "Proceso", "Perfl Sens", "Fracrancia", "Processo", "Produtor")

    private fun BagLabelReading.assertNoNoise() {
        misreadFieldNames.forEach { assertFalse(it, candidates.contains(it)) }
        assertTrue(candidates.toString(), candidates.none { it.split(' ').size > 4 || it.endsWith('.') || it.endsWith(',') })
        assertTrue(candidates.toString(), candidates.none { it.contains("Bem-Vindo", true) || it.contains("Glúten", true) || it.contains("Ltda", true) })
        assertTrue(candidates.toString(), candidates.none { it.contains("mokaclube", true) || !it.first().isLetterOrDigit() || it.contains("89R682") })
    }

    @Test fun u1RodomunhoFront() = read(rodomunhoFront).run {
        // "○ 250g ○ 1kg" came back as "o 250g o lkg": a size choice, not this bag's weight.
        check(null, null, listOf("Frutas vermelhas"), null)
        assertEquals(listOf("Fazenda", "Rodonmunho"), candidates.take(2))
        assertTrue(candidates.contains("Paraíso"))
        assertNoNoise()
    }

    @Test fun u2RodomunhoBack() = read(rodomunhoBack).run {
        check(250.0, "2026-11-09", emptyList(), "Moka Clube")
        assertTrue(candidates.toString(), candidates.contains("Cafe Fazendarodomunho"))
        assertNoNoise()
    }

    @Test fun u3JatobaFront() = read(jatobaFront).run {
        // The sizes broke into "o 250g" and "o lkg"; neither is the weight. A lone "MOKa" logo is not enough for a roaster.
        check(null, null, emptyList(), null)
        assertTrue(candidates.contains("Topázio"))
        assertFalse(candidates.contains("O Lkg"))
        assertNoNoise()
    }

    @Test fun u4JatobaBack() = read(jatobaBack).run {
        check(250.0, "2026-11-06", emptyList(), "Moka Clube")
        assertTrue(candidates.toString(), candidates.take(3).contains("Cafe Fazenda Jatoba"))
        assertNoNoise()
    }

    @Test fun u5Provisorio() = read(provisorio).run {
        check(200.0, null, emptyList(), "Provisório")
        assertEquals("Fermentado", candidates.first())
        assertTrue(candidates.contains("Café Do Célio"))
        assertNoNoise()
    }

    @Test fun frontAndBackMergedFillEveryField() {
        BagLabelReader.merge(read(rodomunhoFront), read(rodomunhoBack)).check(250.0, "2026-11-09", listOf("Frutas vermelhas"), "Moka Clube")
        BagLabelReader.merge(read(jatobaFront), read(jatobaBack)).check(250.0, "2026-11-06", emptyList(), "Moka Clube")
    }

    @Test fun aShortNameNextToAFieldNameStays() {
        assertEquals(listOf("Arara"), read(lines(6f to "AROMA", 6f to "Arara", 6f to "VARIEDADE")).candidates)
    }

    @Test fun aCompanyIdEndingInAMisreadDigitIsNotTheWeight() {
        val back = rodomunhoBack.filterNot { it.text == "250G" || it.text.startsWith("CAFE FAZENDA") }
        assertEquals(null, read(back).weightGrams)
    }

    private val rodomunhoFront = lines(
        25f to "fazenda",
        24f to "rodonmunho",
        14f to "variedad",
        19f to "PARAÍSo",
        13f to "perhlsensorial",
        22f to "GELEIA DE FRUTAS VERMELHAS",
        19f to "EUVA, SUCULENTO",
        16f to "o 250g o lkg",
        11f to "Cerradot",
        11f to "Rege",
        11f to "Processo",
        11f to "Feeient",
        11f to "Produtor",
    )

    private val rodomunhoBack = lines(
        42f to "BEM-VINDO A",
        37f to "XPERÉNGIA",
        34f to "Do CAFS",
        30f to "VERIDICO.",
        26f to "Este pacote que vOcê acabou de",
        21f to "pegar guarda o melhor cafő do",
        21f to "mundo. E essa certeza toda vem",
        19f to "Com um aperto de mão.",
        24f to "Nös somos o Moka clube, ploneiros",
        21f to "na caça do café veridico nas",
        21f to "melhores roças do Brasil, tratando",
        22f to "no fio do bigode com os produtores",
        19f to "mais incriveis do pais.",
        19f to "Bebae prepare-se para um",
        14f to "caminho sem volta.",
        11f to "DUZIDOE EMBALADO POR:",
        17f to "Meka Assinatura de Cafés Especiais LTDA",
        16f to "CNPJ: 00.000.000/0001-8g",
        16f to "R. Exemplo de Novembro, 000- Un. 1",
        12f to "CEP: 00000-000 - Cidade/PR",
        16f to "8amokaclube",
        12f to "www.mokaclube.com.br",
        16f to "SAC: (00) 0000-0000",
        12f to "Fab: 09/11/2026",
        19f to "CAFE FAZENDARODOMUNHO",
        12f to "250G",
        11f to "Val: 09/11/2027",
        22f to "NÃO CONTÉM GLÚTEN.",
        11f to "Lt: 1240911202601",
        9f to "cal fresco e",
        15f to "aroiado Anós aberto manter",
        18f to "a embalagem bem vedada.",
        13f to "\"89R682Z1",
    )

    private val jatobaFront = lines(
        80f to "MOKa",
        24f to "fazennda",
        23f to "jatobá",
        8f to "variedade",
        10f to "TOPÁZIO",
        7f to "perfl sens",
        14f to "SUCO DE MAÇA EMEL",
        12f to "o 250g",
        11f to "o lkg",
        9f to "Proceso",
    )

    private val jatobaBack = lines(
        25f to "cateiooo",
        28f to "BEM-VINDO Ã",
        30f to "EXPERIẾNCIA",
        29f to "DO CAFE",
        32f to "VERIDICO.",
        15f to "Este pacote que você acabou de",
        15f to "pegar guarda o melhor café do",
        13f to "mundo. E essa certeza toda vem",
        13f to "com um aperto de mão.",
        15f to "Nos somos o Moka Clube, pioneiros",
        14f to "na caça do cafē veridico nas",
        19f to "melhores roças do Brasil, tratando",
        15f to "no fio do bigode com os produtores",
        14f to "mais incriveis do pais.",
        14f to "Bebae prepare-se para um",
        13f to "caminho sem volta.",
        10f to "PRODUZIDO E EMBALADO POR:",
        15f to "Moka Assinatura de Cafés Especiais LTDA",
        12f to "CNPJ: 00.000.000/0001-89",
        12f to "R. Exemplo de Novembro, 000 - Un. 1",
        12f to "CEP: 00000-000 - Cidade/PR",
        15f to "famokaclube",
        11f to "www.mokaclube.com.br",
        12f to "SAC: (00) 0000-0000",
        27f to "CAFE FAZENDA JATOBA 250G",
        16f to "Fab: 06/11/2026",
        18f to "Val: 06/11/2027",
        16f to "NÃO CONTÉM GLÚTEN.",
        14f to "Conservar em local fresco e",
        12f to ". Após aberto manter",
        19f to "a embalagem bem vedada.",
        14f to "L: 1230611202601",
    )

    private val provisorio = lines(
        7f to "200g",
        7f to "FRACRANCIA",
        55f to "FERMENTADO",
        9f to "DOCE",
        6f to "VARIEDADE",
        25f to "CAFÉ DO CÉLIO",
        6f to "Arara",
        5f to "AROMA",
        9f to "FRUTADO",
        5f to "ACIDEZ",
        6f to "PROCESso",
        7f to "Fermentacão",
        9f to "Selvagem",
        11f to "TARTÁRICA",
        10f to "Finalização média para longa e muito prazerosa.",
        7f to "cOLHEITA",
        8f to "Seletiva",
        11f to "Produtorr Célio Pereira da Silva",
        13f to "PROVISÓRIo",
        7f to "CAFE CoM PROP0siTO",
        6f to "cORPO",
        6f to "ALTITUDE",
        8f to "1180 m",
        11f to "SIGA",
        10f to "LICORoso",
        8f to "ORIGEM",
        9f to "Sitio Maracanā",
        12f to "Borda Vulcanica.",
        12f to "Poços de Caldas - MG,",
        16f to "TORRADO POR:",
    )
}
