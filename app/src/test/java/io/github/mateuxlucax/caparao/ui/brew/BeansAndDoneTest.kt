package io.github.mateuxlucax.caparao.ui.brew

import io.github.mateuxlucax.caparao.ui.components.BrewNumbers
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.hasText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.ui.format.Format
import io.github.mateuxlucax.caparao.ui.theme.AppTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The espresso beans step (its buzz, once per visit, gated by the vibration setting) and the Done numbers that count up
 * to their saved values (#198). Animations are on here: the count-up and the spring run for real.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class BeansAndDoneTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val resolver = context.contentResolver
    private val locale get() = context.resources.configuration.locales[0]
    private val haptics = mutableListOf<HapticFeedbackType>()
    private val haptic = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            haptics += hapticFeedbackType
        }
    }

    @Before
    fun animationsOn() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    @After
    fun restoreScale() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    /** The beans step for an 18 g espresso, the weight on the scale given by [weight]. */
    private fun beansStep(weight: MutableState<Double>, vibrate: Boolean) {
        compose.setContent {
            AppTheme {
                CompositionLocalProvider(LocalHapticFeedback provides haptic) {
                    WeighStep(
                        BrewUiState(mode = BrewMode.ESPRESSO, dose = 18.0, connected = true, weightGrams = weight.value),
                        onTare = {},
                        onNext = {},
                        vibrate = vibrate,
                    )
                }
            }
        }
    }

    private fun buzzes() = haptics.count { it == HapticFeedbackType.LongPress }

    @Test
    fun beansBuzzOnceWhenTheDoseIsReachedWithVibrationOn() {
        val weight = mutableStateOf(0.0)
        beansStep(weight, vibrate = true)
        compose.waitForIdle()
        compose.runOnIdle { weight.value = 18.0 }
        compose.waitForIdle()
        assertEquals(1, buzzes())
    }

    /** The setting gate: with vibration off the same arrival is silent. */
    @Test
    fun beansStaySilentWithVibrationOff() {
        val weight = mutableStateOf(0.0)
        beansStep(weight, vibrate = false)
        compose.waitForIdle()
        compose.runOnIdle { weight.value = 18.0 }
        compose.waitForIdle()
        assertEquals(0, buzzes())
    }

    /** The latch: 18.0 → 17.9 → 18.0 crosses the dose twice, and the buzz fires once for the visit. */
    @Test
    fun beansBuzzOnlyOncePerVisitWhenTheWeightCrossesTheDoseAgain() {
        val weight = mutableStateOf(0.0)
        beansStep(weight, vibrate = true)
        compose.waitForIdle()
        compose.runOnIdle { weight.value = 18.0 }
        compose.waitForIdle()
        compose.runOnIdle { weight.value = 17.9 }
        compose.waitForIdle()
        compose.runOnIdle { weight.value = 18.0 }
        compose.waitForIdle()
        assertEquals(1, buzzes())
    }

    /** A screen opened with the dose already on the scale was never reached on this visit, so it stays quiet. */
    @Test
    fun aBeansStepOpenedAtTheDoseStaysQuiet() {
        val weight = mutableStateOf(18.0)
        beansStep(weight, vibrate = true)
        compose.waitForIdle()
        compose.runOnIdle { weight.value = 17.9 }
        compose.waitForIdle()
        compose.runOnIdle { weight.value = 18.0 }
        compose.waitForIdle()
        assertEquals(0, buzzes())
    }

    private fun espressoBrew(yieldGrams: Double) = Brew(
        id = "done-brew",
        mode = BrewMode.ESPRESSO,
        startedAt = 0L,
        durationMs = 27_350L,
        doseGrams = 18.0,
        yieldGrams = yieldGrams,
        timeToFirstDropMs = 6_420L,
        flowAvgGramsPerSecond = 1.3,
        flowPeakGramsPerSecond = 2.1,
        interrupted = false,
    )

    /** 36.35 g is 36.4 in the brew detail; a Float count would land on 36.3. The count must end on exactly that text. */
    @Test
    fun theCountUpEndsOnTheSavedValueWithAnimationsOn() {
        val brew = espressoBrew(yieldGrams = 36.35)
        compose.setContent { AppTheme { BrewNumbers(brew, countUp = true) } }
        val yieldText = "${Format.grams(36.35, locale)} ${context.getString(R.string.unit_grams)}"
        compose.waitUntilAtLeastOneExists(hasText(yieldText), 5_000)
        compose.waitForIdle()
        compose.onNodeWithText(yieldText).assertIsDisplayed()
        assertEquals("36.4 g", yieldText.replace(',', '.'))
        val ratio = context.getString(R.string.ratio_value, Format.ratio(brew.ratio, locale))
        compose.onNodeWithText(ratio).assertIsDisplayed()
        compose.onNodeWithText("${Format.grams(18.0, locale)} ${context.getString(R.string.unit_grams)}").assertIsDisplayed()
        compose.onNodeWithText(Format.timer(27)).assertIsDisplayed()
    }

    /** With animations off the numbers are the final values at once. */
    @Test
    fun theNumbersShowTheirFinalValuesWithAnimationsOff() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val brew = espressoBrew(yieldGrams = 36.35)
        compose.setContent { AppTheme { BrewNumbers(brew, countUp = true) } }
        compose.waitForIdle()
        compose.onNodeWithText("${Format.grams(36.35, locale)} ${context.getString(R.string.unit_grams)}").assertIsDisplayed()
    }
}
