package dev.mateux.caparao.data.local

import dev.mateux.caparao.ui.FakePreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreTest {
    @Test
    fun remindToRateDefaultsOnAndPersistsChanges() {
        val preferences = FakePreferences()
        val settings = SettingsStore(preferences)

        assertTrue(settings.remindToRate)
        settings.remindToRate = false
        assertFalse(SettingsStore(preferences).remindToRate)
    }
}
