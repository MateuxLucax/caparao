package io.github.mateuxlucax.caparao.ui.onboarding

import io.github.mateuxlucax.caparao.model.ScaleError
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.ui.FakeScaleSource
import io.github.mateuxlucax.caparao.ui.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val scale = FakeScaleSource()
    private var done = false
    // Lazy: viewModelScope must be created after the rule sets the main dispatcher.
    private val viewModel by lazy { OnboardingViewModel(scale) { done = true } }

    @Test
    fun showsTheErrorAndTheLogForDebugging() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        scale.state.value = ScaleState.Failed(ScaleError.NOT_FOUND, retrying = true)
        scale.log.value = listOf("scan: nothing found in 10 s")
        assertEquals(ScaleState.Failed(ScaleError.NOT_FOUND, retrying = true), viewModel.uiState.value.scale)
        assertEquals(listOf("scan: nothing found in 10 s"), viewModel.uiState.value.log)
    }

    @Test
    fun finishMarksOnboardingDone() {
        viewModel.finish()
        assertTrue(done)
    }

    @Test
    fun retryRestartsTheConnection() {
        viewModel.retry()
        assertEquals(listOf("stop", "start"), scale.commands)
    }
}
