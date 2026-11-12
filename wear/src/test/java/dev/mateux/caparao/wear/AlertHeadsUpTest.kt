package dev.mateux.caparao.wear

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mateux.caparao.watch.WatchAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** The wrist's heads-up for the brew's final moments (#184): one per brew, naming the next step for the mode. */
@RunWith(AndroidJUnit4::class)
class AlertHeadsUpTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun filterLastPourSaysToLetItDrain() {
        val heads = headsUpFor(WatchAlert.ALL_DONE)!!
        assertEquals("Last pour done", context.getString(heads.title))
        assertEquals("Let it drain.", context.getString(heads.text))
    }

    @Test
    fun frenchPressDeadlineSaysToRemoveThePressFirst() {
        val heads = headsUpFor(WatchAlert.FRENCH_PRESS_DEADLINE)!!
        assertEquals("Steep's over", context.getString(heads.title))
        assertEquals("Remove the press, then press and serve.", context.getString(heads.text))
    }

    @Test
    fun remindersAndOtherMomentsDoNotNotify() {
        assertNull(headsUpFor(WatchAlert.FRENCH_PRESS_REMINDER))
        assertNull(headsUpFor(WatchAlert.COUNTDOWN))
    }
}
