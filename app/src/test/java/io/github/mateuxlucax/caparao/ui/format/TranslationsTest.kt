package io.github.mateuxlucax.caparao.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class TranslationsTest {
    private val english = parse("values")
    private val portuguese = parse("values-pt-rBR")

    @Test
    fun everyTranslatableKeyExistsInBothLanguages() {
        assertEquals("missing in values-pt-rBR", emptySet<String>(), english.keys - portuguese.keys)
        assertEquals("missing in values (English)", emptySet<String>(), portuguese.keys - english.keys)
    }

    @Test
    fun translationsUseTheSameFormatArguments() {
        val mismatched = english.filter { (key, text) -> portuguese[key]?.let { arguments(it) != arguments(text) } == true }
        assertEquals(emptyMap<String, String>(), mismatched)
    }

    @Test
    fun portugueseIsActuallyTranslated() {
        // Units, mode names shared by both languages and the like may match; most strings must not.
        val same = english.filter { (key, text) -> portuguese[key] == text }.keys
        assertEquals(setOf("unit_grams", "unit_grams_per_second", "mode_espresso", "battery_percent", "permissions_title"), same)
    }

    /** Positional arguments such as `%1$s` and `%2$d`, sorted. */
    private fun arguments(text: String) = Regex("""%\d+\$[sdf]""").findAll(text).map { it.value }.sorted().toList()

    /** Translatable `string` name to text, from src/main/res/<folder>/strings.xml (tests run in the module directory). */
    private fun parse(folder: String): Map<String, String> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/$folder/strings.xml")).documentElement
        return (0 until root.childNodes.length).map { root.childNodes.item(it) }.filterIsInstance<Element>()
            .filter { it.tagName == "string" && it.getAttribute("translatable") != "false" }
            .associate { it.getAttribute("name") to it.textContent }
    }
}
