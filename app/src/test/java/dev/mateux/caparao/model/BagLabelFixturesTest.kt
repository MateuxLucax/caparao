package dev.mateux.caparao.model

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real Brazilian bag labels from #555, as ML Kit would return them: lines in reading order with a relative height.
 * W: facts from roaster and retailer pages, with assumed layout and roast-date lines. P: customer photos in
 * marketplace reviews. U: the maintainer's own bags. Company IDs, addresses and phone numbers are placeholders.
 */
class BagLabelFixturesTest {
    private val today = LocalDate.of(2026, 11, 16)

    /** What the coffee form passes in Portuguese: the tasting chips and the roasters already on the shelf. */
    private val notes = listOf("Caramelo", "Chocolate", "Cítrico", "Frutas vermelhas", "Floral", "Castanhas")
    private val roasters = listOf("Unique Cafés", "Coffee Mais", "Moka Clube", "Provisório", "Giói")

    private fun read(vararg lines: Pair<Float, String>) =
        BagLabelReader.read(lines.map { (height, text) -> LabelLine(text, height * 10) }, roasters, notes, today)

    private fun day(value: String) = LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun BagLabelReading.check(weight: Double?, roast: String?, tasting: List<String>, roaster: String?) {
        assertEquals("weight", weight, weightGrams)
        assertEquals("roast date", roast?.let(::day), roastedAt)
        assertEquals("tasting notes", tasting, tastingNotes)
        assertEquals("roaster", roaster, this.roaster)
    }

    private val fieldNames = listOf("Variedade", "Perfil Sensorial", "Processo", "Produtor", "Região")

    @Test fun w1UniqueBourbonAmarelo() = read(
        3.0f to "UNIQUE", 1.0f to "CAFÉS ESPECIAIS", 2.2f to "BOURBON AMARELO", 1.0f to "Mantiqueira de Minas",
        1.0f to "Cereja descascado", 1.0f to "Torra média-clara", 1.0f to "Notas de frutas cítricas", 1.0f to "Nota SCA 85 – 86",
        1.0f to "Peso líquido 250g", 0.8f to "Torrado em: 03/11/2026", 0.8f to "Lote 1126",
    ).run {
        check(250.0, "2026-11-03", listOf("Cítrico"), "Unique Cafés")
        assertEquals("Bourbon Amarelo", candidates.first())
        assertFalse(candidates.contains("Lote 1126"))
    }

    @Test fun w2UniqueFrutadoWithTwoDigitYear() = read(
        3.0f to "UNIQUE", 2.2f to "FRUTADO", 1.0f to "Bourbon Vermelho · Natural", 1.0f to "Mantiqueira de Minas",
        1.0f to "Notas de chocolate", 1.0f to "Aroma frutado", 1.0f to "Torra média", 1.0f to "250 g", 0.8f to "Data de torra 28/10/26",
    ).check(250.0, "2026-10-28", listOf("Chocolate"), null)

    @Test fun w3CoffeeMaisCaparaoAltitudeIsNotAWeight() = read(
        2.6f to "COFFEE MAIS", 2.2f to "CAPARAÓ", 1.0f to "Altitude média: 1.000 metros", 1.0f to "Notas: frutas amarelas e cacau",
        1.0f to "Torra média", 1.0f to "100% arábica", 1.0f to "Peso líquido: 250 g", 0.8f to "Torra: 05/11/2026",
    ).run {
        check(250.0, "2026-11-05", emptyList(), "Coffee Mais")
        assertEquals("Caparaó", candidates.first())
    }

    @Test fun w4CoffeeMaisCerradoDottedDate() = read(
        2.6f to "COFFEE++", 2.2f to "CERRADO MINEIRO", 1.0f to "Produtor Gabriel Nunes", 1.0f to "doce de marmelo,",
        1.0f to "chocolate e maracujá", 1.0f to "+84 PONTOS", 1.0f to "Torra Média", 1.0f to "250g", 0.8f to "Torrado em 10.11.2026",
    ).check(250.0, "2026-11-10", listOf("Chocolate"), null)

    @Test fun w5CoffeeMaisIntensoGramasSpelledOut() = read(
        2.6f to "COFFEE MAIS", 2.2f to "INTENSO", 1.0f to "Sul de Minas (Serra da Canastra)", 1.0f to "Altitude média de 1.100 metros",
        1.0f to "Torra média-escura", 1.0f to "Notas sensoriais: chocolate intenso", 1.0f to "250 GRAMAS", 0.8f to "Data de torra: 07/11/26",
    ).check(250.0, "2026-11-07", listOf("Chocolate"), "Coffee Mais")

    @Test fun w6CoffeeMaisArara() = read(
        2.6f to "COFFEE MAIS", 2.2f to "ARARA", 1.0f to "Notas de: Chocolate, caramelo e maracujá", 1.0f to "Altitude 1.150m",
        1.0f to "Torra Média", 1.0f to "100% Arábica", 1.0f to "Grãos - 250g", 0.8f to "Torra 12/11/2026",
    ).check(250.0, "2026-11-12", listOf("Chocolate", "Caramelo"), "Coffee Mais")

    @Test fun w7MokaClubeRodomunho() = read(
        2.6f to "MOKA CLUBE", 2.2f to "FAZENDA RODOMUNHO", 1.0f to "Geleia de frutas vermelhas,", 1.0f to "uva e suculento",
        1.0f to "100% arábica · acima de 84 pontos", 1.0f to "250g", 0.8f to "Torrado em 14/11/2026",
    ).run {
        check(250.0, "2026-11-14", listOf("Frutas vermelhas"), "Moka Clube")
        assertEquals("Fazenda Rodomunho", candidates.first())
    }

    @Test fun w8MokaClubeBelaEpocaSingleDigitDay() = read(
        2.6f to "MOKA CLUBE", 2.2f to "Bela Época", 1.4f to "ORGÂNICO", 1.0f to "Avelã, toffee e baunilha", 1.0f to "250g", 0.8f to "Torra: 1/11/2026",
    ).check(250.0, "2026-11-01", emptyList(), "Moka Clube")

    @Test fun w9MokaClubeMezzanotteOneKilo() = read(
        2.6f to "MOKA CLUBE", 2.2f to "MEZZANOTTE", 1.0f to "Chocolate amargo e encorpado", 1.0f to "1kg", 0.8f to "Torrado em 09/11/26",
    ).check(1000.0, "2026-11-09", listOf("Chocolate"), "Moka Clube")

    @Test fun p1UniqueTorradoEMoidoIsNotARoastDate() = read(
        3.0f to "unique", 0.6f to "MANTIQUEIRA DE MINAS", 0.6f to "TORRADO E MOÍDO", 1.4f to "BOURBON AMARELO",
        0.7f to "Café suave, sem amargor, com", 0.7f to "aroma adocicado e notas cítricas", 0.7f to "de tangerina e mel.",
        0.5f to "CAFÉ ESPECIAL", 0.5f to "100% ARÁBICA", 0.8f to "Peso líq. 250g",
        // The second bag in the photo repeats the front.
        3.0f to "unique", 1.4f to "BOURBON AMARELO", 0.8f to "Peso líq. 250g",
    ).run {
        check(250.0, null, listOf("Cítrico"), null)
        assertEquals(1, candidates.count { it == "Bourbon Amarelo" })
    }

    @Test fun p2MokaClubeLogoOnTwoLinesAndFieldNames() = read(
        3.0f to "moka", 3.0f to "clube", 1.6f to "bala de", 1.6f to "caramelo", 0.5f to "variedade", 0.7f to "BLEND",
        0.5f to "perfil sensorial", 0.7f to "CARAMELO, BAUNILHA", 0.7f to "E DOCE DE LEITE", 0.5f to "Processo", 0.5f to "Natural",
        0.5f to "Produtor", 0.7f to "250g",
    ).run {
        check(250.0, null, listOf("Caramelo"), "Moka Clube")
        fieldNames.forEach { assertFalse(it, candidates.contains(it)) }
    }

    @Test fun p3CoffeeArabicaPercentAndOuncesAreNotWeights() = read(
        3.0f to "Coffee", 2.4f to "++", 0.6f to "Em Grão", 0.8f to "- SUPER SPECIALTY -", 1.6f to "ARARA", 0.9f to "100% ARÁBICA",
        0.9f to "CAFÉ ESPECIAL", 0.8f to "CAFÉ TORRADO EM GRÃO", 0.6f to "TORRA MÉDIA | 250g | MEDIUM ROAST | NET WT 8.8 OZ", 0.5f to "WHOLE BEAN",
    ).check(250.0, null, emptyList(), null)

    @Test fun p4CoffeeNewerDesign() = read(
        3.0f to "Coffee", 2.4f to "++", 0.8f to "- SUPER SPECIALTY -", 1.4f to "ARARA", 0.8f to "GRÃO", 0.8f to "100% ARÁBICA",
        0.9f to "CAFÉ ESPECIAL", 0.6f to "TORRA MÉDIA", 0.6f to "CAFÉ TORRADO EM GRÃO   250g", 0.4f to "MEDIUM ROAST   WHOLE BEAN   NET WT 8.8 OZ",
    ).check(250.0, null, emptyList(), null)

    @Test fun p5CoffeeBackReadsOnlyTheRoasterFromTheAddress() = read(
        1.0f to "1953", 0.8f to "Somos a terceira geração de uma família produtora de café;", 0.8f to "são mais de 70 anos de muito trabalho, ...",
        0.8f to "Todos os nossos cafés pontuam", 0.8f to "acima de 84 pontos na escala da", 0.8f to "Associação de Cafés Especiais (SCA).",
        0.8f to "1% do lucro líquido proveniente desta venda ...", 0.8f to "@coffeemais", 0.8f to "www.coffeemais.com", 0.5f to "100% INDÚSTRIA BRASILEIRA",
    ).run {
        check(null, null, emptyList(), "Coffee Mais")
        assertTrue(candidates.none { it.contains("coffeemais", ignoreCase = true) })
    }

    @Test fun p6GioiInkjetStampReadsFabricationAndSkipsValidity() = read(
        0.9f to "F:21/07/2026", 0.9f to "V:21/01/2028", 0.9f to "L:P.MKG1K-07/26",
        1.6f to "giói", 0.7f to "Nossos cafés são 100% arábica e especiais, com pontuação", 0.7f to "acima de 84 na escala da BSCA",
        1.0f to "CAFÉ TORRADO EM GRÃOS", 0.5f to "LOTE E VALIDADE: Vide embalagem.", 0.6f to "Mantiqueira de Minas",
        0.4f to "Denominação de Origem", 0.6f to "1kg", 0.4f to "ORIGIN AND QUALITY CERTIFIED",
    ).run {
        check(1000.0, "2026-07-21", emptyList(), "Giói")
        assertTrue(candidates.none { it.startsWith("L:") || it.startsWith("V:") || it.startsWith("F:") })
    }

    private val rodomunhoFront = arrayOf(
        3.0f to "moka", 3.0f to "clube", 1.6f to "fazenda", 1.6f to "rodomunho", 0.5f to "variedade", 0.7f to "PARAÍSO",
        0.5f to "perfil sensorial", 0.7f to "GELÉIA DE FRUTAS VERMELHAS", 0.7f to "E UVA, SUCULENTO", 0.5f to "Região", 0.5f to "Cerrado Mineiro",
        0.5f to "Processo", 0.5f to "Fermentado", 0.5f to "Produtor", 0.5f to "AVENTUREIRO", 0.5f to "TRADICIONAL", 0.7f to "○ 250g   ○ 1kg",
    )

    private val mokaBackPanel = arrayOf(
        1.6f to "BEM-VINDO À", 1.6f to "EXPERIÊNCIA", 1.6f to "DO CAFÉ", 1.6f to "VERÍDICO.",
        0.7f to "Nós somos o Moka Clube, pioneiros na caça do café verídico", 0.6f to "PRODUZIDO E EMBALADO POR:",
        0.6f to "Moka Assinatura de Cafés Especiais LTDA", 0.6f to "CNPJ: 00.000.000/0001-00", 0.6f to "R. Exemplo de Novembro, 000 - Un. 1",
        0.6f to "CEP: 00000-000 - Cidade/PR", 0.6f to "@mokaclube", 0.6f to "www.mokaclube.com.br", 0.6f to "SAC: (00) 0000-0000",
        0.6f to "NÃO CONTÉM GLÚTEN.", 0.6f to "Conservar em local fresco e arejado. Após aberto manter a embalagem bem vedada.",
    )

    @Test fun u1RodomunhoFrontSizeTickBoxesAreNotTheBagWeight() = read(*rodomunhoFront).run {
        check(null, null, listOf("Frutas vermelhas"), "Moka Clube")
        assertTrue(candidates.contains("Paraíso"))
        fieldNames.forEach { assertFalse(it, candidates.contains(it)) }
    }

    @Test fun u2RodomunhoBackStickerReadsFabAndSkipsValAndLot() = read(
        *mokaBackPanel, 0.8f to "CAFE FAZENDA RODOMUNHO", 0.8f to "250G", 0.6f to "Fab: 09/11/2026", 0.6f to "Val: 09/11/2027",
        0.6f to "Lt: 1240911202601", 0.5f to "7 898684 714411", 0.3f to "packster   2000002516 - 5049",
    ).run {
        check(250.0, "2026-11-09", emptyList(), "Moka Clube")
        assertTrue(candidates.contains("Cafe Fazenda Rodomunho"))
        assertTrue(candidates.none { it.contains("Cnpj", true) || it.contains("Sac", true) || it.startsWith("Lt") || it.contains("www", true) })
    }

    @Test fun u1AndU2MergedTakeTheWeightAndDateFromTheBack() {
        val front = read(*rodomunhoFront)
        val back = read(*mokaBackPanel, 0.8f to "CAFE FAZENDA RODOMUNHO", 0.8f to "250G", 0.6f to "Fab: 09/11/2026", 0.6f to "Val: 09/11/2027")
        BagLabelReader.merge(front, back).check(250.0, "2026-11-09", listOf("Frutas vermelhas"), "Moka Clube")
    }

    @Test fun u3JatobaFront() = read(
        3.0f to "moka", 3.0f to "clube", 0.6f to "sabor profundo", 1.6f to "fazenda", 1.6f to "jatobá", 0.5f to "variedade", 0.7f to "TOPÁZIO",
        0.5f to "perfil sensorial", 0.7f to "SUCO DE MAÇÃ E MEL", 0.5f to "Região", 0.5f to "Alta Mogiana", 0.5f to "Processo", 0.5f to "Natural",
        0.5f to "Produtor", 0.5f to "AVENTUREIRO", 0.5f to "TRADICIONAL", 0.7f to "○ 250g   ○ 1kg",
    ).run {
        check(null, null, emptyList(), "Moka Clube")
        assertTrue(candidates.contains("Topázio"))
    }

    @Test fun u4JatobaBackNameAndWeightOnOneLine() = read(
        0.8f to "café 100% ar", *mokaBackPanel, 0.8f to "CAFE FAZENDA JATOBA 250G", 0.6f to "Fab: 06/11/2026", 0.6f to "Val: 06/11/2027",
        0.6f to "Lt: 1230611202601", 0.5f to "7 898684 714428",
    ).run {
        check(250.0, "2026-11-06", emptyList(), "Moka Clube")
        assertTrue(candidates.contains("Cafe Fazenda Jatoba"))
    }

    @Test fun u5ProvisorioTorradoPorIsNotARoastDate() = read(
        1.0f to "CAFÉ DO CÉLIO", 2.6f to "FERMENTADO", 0.4f to "FRAGRÂNCIA", 0.7f to "DOCE", 0.4f to "AROMA", 0.7f to "FRUTADO",
        0.4f to "ACIDEZ", 0.7f to "TARTÁRICA", 0.4f to "CORPO", 0.7f to "LICOROSO", 0.6f to "Finalização média para longa e muito prazerosa.",
        0.4f to "VARIEDADE", 0.5f to "Arara", 0.4f to "PROCESSO", 0.5f to "Fermentação Selvagem", 0.4f to "COLHEITA", 0.5f to "Seletiva",
        0.4f to "ALTITUDE", 0.5f to "1180 m", 0.4f to "ORIGEM", 0.5f to "Sítio Maracanã Borda Vulcânica, Poços de Caldas - MG.",
        0.6f to "Produtor: Célio Pereira da Silva", 1.0f to "PROVISÓRIO", 0.3f to "CAFÉ COM PROPÓSITO", 0.4f to "200g",
        0.5f to "SIGA:", 0.5f to "TORRADO POR:", 0.9f to "RT", 0.3f to "Romão Torrefador",
    ).run {
        check(200.0, null, emptyList(), "Provisório")
        assertEquals("Fermentado", candidates.first())
        assertTrue(candidates.contains("Café Do Célio"))
        listOf("Variedade", "Processo", "Altitude", "Origem", "Torrado Por:").forEach { assertFalse(it, candidates.contains(it)) }
    }
}
