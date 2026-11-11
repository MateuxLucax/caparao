package io.github.mateuxlucax.caparao

import android.content.Context
import io.github.mateuxlucax.caparao.data.scale.ScaleSource
import io.github.mateuxlucax.caparao.data.scale.TimerAction
import io.github.mateuxlucax.caparao.model.ScaleReading
import io.github.mateuxlucax.caparao.model.ScaleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = MainActivityLifecycleTest.TestApplication::class)
class MainActivityLifecycleTest {
    @Test
    fun recreationKeepsConnectedScaleRunning() {
        val scale = TestApplication.scale
        val oldActivity = Robolectric.buildActivity(MainActivity::class.java).create()
        oldActivity.get().lifecycle("onStart")
        assertEquals(1, scale.starts)
        assertTrue(scale.state.value is ScaleState.Connected)

        oldActivity.get().stopScale(changingConfigurations = true)
        val newActivity = Robolectric.buildActivity(MainActivity::class.java).create()
        newActivity.get().lifecycle("onStart")

        assertEquals(2, scale.starts)
        assertEquals(0, scale.stops)
        assertTrue(scale.state.value is ScaleState.Connected)
        newActivity.get().lifecycle("onStop")
        assertEquals(1, scale.stops)
        oldActivity.destroy()
        newActivity.destroy()
    }

    private fun MainActivity.lifecycle(callback: String) {
        javaClass.getDeclaredMethod(callback).apply {
            isAccessible = true
            invoke(this@lifecycle)
        }
    }

    private fun MainActivity.stopScale(changingConfigurations: Boolean) {
        javaClass.getDeclaredMethod("stopScaleIfBackgrounded", Boolean::class.javaPrimitiveType).apply {
            isAccessible = true
            invoke(this@stopScale, changingConfigurations)
        }
    }

    class TestApplication : CaparaoApplication() {
        override val debugHooks = object : DebugHooks {
            override fun createScale(context: Context, scope: CoroutineScope): ScaleSource = scale
        }

        companion object {
            val scale = TestScale()
        }
    }

    class TestScale : ScaleSource {
        private val mutableState = MutableStateFlow<ScaleState>(ScaleState.Connected(null))
        override val state = mutableState.asStateFlow()
        override val reading = MutableStateFlow<ScaleReading?>(null).asStateFlow()
        override val readings = MutableSharedFlow<ScaleReading>().asSharedFlow()
        override val log = MutableStateFlow(emptyList<String>()).asStateFlow()
        var starts = 0
        var stops = 0

        override fun start() { starts++ }
        override fun stop() { stops++; mutableState.value = ScaleState.Idle }
        override fun tare() = Unit
        override fun timer(action: TimerAction) = Unit
    }
}
