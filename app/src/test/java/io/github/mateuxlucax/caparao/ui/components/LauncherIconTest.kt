package io.github.mateuxlucax.caparao.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The launcher icon's two drawables: the full-color foreground and the monochrome one (themed icons and the in-app
 * [Logo], which follow Material You) must draw the same shape, or the icon changes when the user turns on themed icons.
 */
class LauncherIconTest {
    private val android = "http://schemas.android.com/apk/res/android"

    @Test
    fun foregroundAndMonochromeDrawTheSameShape() {
        assertEquals(shape("drawable/ic_launcher_foreground.xml"), shape("drawable/ic_launcher_monochrome.xml"))
    }

    @Test
    fun ledIsAHoleSoItShowsTheBackground() {
        val paths = elements("drawable/ic_launcher_foreground.xml", "path")
        assertEquals("cup and plate only, no separate LED shape", 2, paths.size)
        assertEquals("evenOdd", paths.last().getAttributeNS(android, "fillType"))
    }

    @Test
    fun adaptiveIconDeclaresTheMonochromeLayer() {
        val monochrome = elements("mipmap-anydpi/ic_launcher.xml", "monochrome").single()
        assertEquals("@drawable/ic_launcher_monochrome", monochrome.getAttributeNS(android, "drawable"))
    }

    @Test
    fun foregroundIsOneColor() {
        val colors = elements("drawable/ic_launcher_foreground.xml", "path").map { it.getAttributeNS(android, "fillColor") }.toSet()
        assertEquals(setOf("#FFF1ECE2"), colors)
        assertTrue(File("src/main/res/values/colors.xml").readText().contains("ic_launcher_background"))
    }

    /** Every group transform and path geometry, in document order, without colors. */
    private fun shape(file: String): List<String> {
        val geometry = listOf("scaleX", "scaleY", "translateX", "translateY", "pivotX", "pivotY", "pathData", "fillType")
        return (elements(file, "group") + elements(file, "path")).map { e ->
            e.tagName + geometry.joinToString { "$it=" + e.getAttributeNS(android, it) }
        }
    }

    private fun elements(file: String, tag: String): List<Element> {
        // Unit tests run in the module directory.
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val nodes = factory.newDocumentBuilder().parse(File("src/main/res/$file")).getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }
}
