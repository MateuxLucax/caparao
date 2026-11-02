package io.github.mateuxlucax.caparao.ui.launcher

import io.github.mateuxlucax.caparao.model.BrewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The shortcut-to-destination mapping and the two XML copies that declare it (#116). */
class LauncherShortcutTest {
    private val android = "http://schemas.android.com/apk/res/android"
    private val appId = "io.github.mateuxlucax.caparao"

    @Test
    fun espressoAndFilterOpenTheirBrewAtTheDoseAndBrewsOpensTheTab() {
        assertEquals(ShortcutDestination.Brew(BrewMode.ESPRESSO), LauncherShortcut.ESPRESSO.destination)
        assertEquals(ShortcutDestination.Brew(BrewMode.FILTER), LauncherShortcut.FILTER.destination)
        assertEquals(ShortcutDestination.Brews, LauncherShortcut.BREWS.destination)
    }

    @Test
    fun theDestinationComesFromTheActionAlone() {
        for (shortcut in LauncherShortcut.entries) {
            assertEquals(shortcut, LauncherShortcut.fromAction(shortcut.action))
            assertEquals(shortcut, LauncherShortcut.fromId(shortcut.id))
        }
    }

    @Test
    fun unknownActionsAndIdsOpenTheAppNormally() {
        assertNull(LauncherShortcut.fromAction(null))
        assertNull(LauncherShortcut.fromAction("android.intent.action.MAIN"))
        assertNull(LauncherShortcut.fromAction("io.github.mateuxlucax.caparao.shortcut.FRENCH_PRESS"))
        assertNull(LauncherShortcut.fromId(null))
        assertNull(LauncherShortcut.fromId("french-press"))
    }

    @Test
    fun aFreshLaunchReadsTheShortcutFromTheIntent() {
        assertEquals(LauncherShortcut.FILTER, LauncherShortcut.pendingOnCreate(savedId = null, launchAction = LauncherShortcut.FILTER.action, recreated = false))
        assertNull(LauncherShortcut.pendingOnCreate(savedId = null, launchAction = "android.intent.action.MAIN", recreated = false))
    }

    @Test
    fun aRecreationKeepsAnUnconsumedShortcut() {
        assertEquals(LauncherShortcut.BREWS, LauncherShortcut.pendingOnCreate(savedId = "brews", launchAction = LauncherShortcut.BREWS.action, recreated = true))
    }

    @Test
    fun aConsumedShortcutIsNotAppliedAgainAfterARecreation() {
        // Consumed: the saved state has no id, and the original launch intent must not be read a second time.
        assertNull(LauncherShortcut.pendingOnCreate(savedId = null, launchAction = LauncherShortcut.ESPRESSO.action, recreated = true))
    }

    @Test
    fun bothBuildVariantsDeclareTheCodeShortcuts() {
        val release = declared("src/main/res/xml/shortcuts.xml")
        val debug = declared("src/debug/res/xml/shortcuts.xml")
        val ids = LauncherShortcut.entries.map { it.id }.toSet()
        assertEquals("release shortcuts", ids, release.keys)
        assertEquals("debug shortcuts", ids, debug.keys)

        for (shortcut in LauncherShortcut.entries) {
            for ((variant, declared) in listOf("release" to release, "debug" to debug)) {
                val entry = declared.getValue(shortcut.id)
                assertEquals("$variant ${shortcut.id} action", shortcut.action, entry.action)
                assertEquals("$variant ${shortcut.id} activity", "$appId.MainActivity", entry.targetClass)
            }
            assertEquals("release target package", appId, release.getValue(shortcut.id).targetPackage)
            assertEquals("debug target package", "$appId.debug", debug.getValue(shortcut.id).targetPackage)
        }
    }

    private data class Declared(val action: String, val targetPackage: String, val targetClass: String)

    /** Shortcut id to its single intent, from a shortcuts XML (unit tests run in the module directory). */
    private fun declared(file: String): Map<String, Declared> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val root = factory.newDocumentBuilder().parse(File(file)).documentElement
        val shortcuts = root.getElementsByTagName("shortcut")
        return (0 until shortcuts.length).map { shortcuts.item(it) as Element }.associate { shortcut ->
            val intents = shortcut.getElementsByTagName("intent")
            assertEquals("${shortcut.getAttributeNS(android, "shortcutId")} has one intent", 1, intents.length)
            val intent = intents.item(0) as Element
            shortcut.getAttributeNS(android, "shortcutId") to Declared(
                action = intent.getAttributeNS(android, "action"),
                targetPackage = intent.getAttributeNS(android, "targetPackage"),
                targetClass = intent.getAttributeNS(android, "targetClass"),
            )
        }
    }
}
