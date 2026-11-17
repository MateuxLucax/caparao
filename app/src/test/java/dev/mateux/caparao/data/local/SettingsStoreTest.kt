package dev.mateux.caparao.data.local

import dev.mateux.caparao.ui.FakePreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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

    @Test
    fun ratingReminderDelayDefaultsToTenAndStaysBetweenFiveAndFifteenMinutes() {
        val preferences = FakePreferences()
        val settings = SettingsStore(preferences)

        assertEquals(10, settings.ratingReminderDelayMinutes)
        settings.ratingReminderDelayMinutes = 4
        assertEquals(5, SettingsStore(preferences).ratingReminderDelayMinutes)
        settings.ratingReminderDelayMinutes = 16
        assertEquals(15, SettingsStore(preferences).ratingReminderDelayMinutes)
        settings.ratingReminderDelayMinutes = 12
        assertEquals(12, SettingsStore(preferences).ratingReminderDelayMinutes)
    }

    @Test
    fun notificationPermissionPromptStateDefaultsToNotAskedAndPersists() {
        val preferences = FakePreferences()
        val settings = SettingsStore(preferences)

        assertFalse(settings.ratingNotificationsPermissionAsked)
        settings.ratingNotificationsPermissionAsked = true
        assertTrue(SettingsStore(preferences).ratingNotificationsPermissionAsked)
    }

    @Test
    fun bedtimeDefaultsToElevenAndPersistsValidMinutes() {
        val preferences = FakePreferences()
        val settings = SettingsStore(preferences)
        assertEquals(23 * 60, settings.bedtimeMinutes)
        settings.bedtimeMinutes = 21 * 60 + 30
        assertEquals(21 * 60 + 30, SettingsStore(preferences).bedtimeMinutes)
    }
}
