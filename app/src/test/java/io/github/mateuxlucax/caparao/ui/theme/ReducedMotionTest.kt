package io.github.mateuxlucax.caparao.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

/** The one reduced-motion read: animations off means the system's animator duration scale is 0. */
@RunWith(AndroidJUnit4::class)
class ReducedMotionTest {
    @get:Rule
    val compose = createComposeRule()

    private val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver

    @After
    fun restoreScale() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    @Test
    fun animationsOffWhenTheScaleIsZero() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        assertTrue(readAnimationsOff())
    }

    @Test
    fun animationsOnWhenTheScaleIsOne() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        assertFalse(readAnimationsOff())
    }

    private fun readAnimationsOff(): Boolean {
        val off = AtomicBoolean()
        compose.setContent { off.set(animationsOff()) }
        compose.waitForIdle()
        return off.get()
    }
}
