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
import org.junit.Assert.assertSame
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
    fun rotationKeepsTheScaleLink() {
        val first = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity = first.get()
        val scale = (activity.application as TestApplication).scale
        assertTrue(scale.state.value is ScaleState.Connected)

        activity.configurationChange = { true }
        first.pause().stop().destroy()

        val second = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        assertSame(scale, (second.get().application as TestApplication).scale)
        assertTrue(scale.state.value is ScaleState.Connected)

        second.pause().stop()
        assertTrue(scale.state.value is ScaleState.Idle)
        second.destroy()
    }

    class TestApplication : CaparaoApplication() {
        val scale = TestScale()
        override val debugHooks = object : DebugHooks {
            override fun createScale(context: Context, scope: CoroutineScope): ScaleSource = scale
        }
    }

    class TestScale : ScaleSource {
        private val mutableState = MutableStateFlow<ScaleState>(ScaleState.Connected(null))
        override val state = mutableState.asStateFlow()
        override val reading = MutableStateFlow<ScaleReading?>(null).asStateFlow()
        override val readings = MutableSharedFlow<ScaleReading>().asSharedFlow()
        override val log = MutableStateFlow(emptyList<String>()).asStateFlow()

        override fun start() = Unit
        override fun stop() { mutableState.value = ScaleState.Idle }
        override fun tare() = Unit
        override fun timer(action: TimerAction) = Unit
    }
}
