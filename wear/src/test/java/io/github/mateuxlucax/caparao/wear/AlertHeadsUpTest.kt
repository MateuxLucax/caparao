package io.github.mateuxlucax.caparao.wear

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mateuxlucax.caparao.watch.WatchAlert
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
        assertEquals("Last pour done · Let it drain.", context.getString(headsUpFor(WatchAlert.ALL_DONE)!!))
    }

    @Test
    fun frenchPressDeadlineSaysToRemoveThePressFirst() {
        assertEquals(
            "Steep's over · Remove the press, then press and serve.",
            context.getString(headsUpFor(WatchAlert.FRENCH_PRESS_DEADLINE)!!),
        )
    }

    @Test
    fun remindersAndOtherMomentsDoNotNotify() {
        assertNull(headsUpFor(WatchAlert.FRENCH_PRESS_REMINDER))
        assertNull(headsUpFor(WatchAlert.COUNTDOWN))
    }
}
