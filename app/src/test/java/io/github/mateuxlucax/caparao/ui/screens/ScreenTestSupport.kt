package io.github.mateuxlucax.caparao.ui.screens
import android.content.Context
import android.os.Looper
import android.provider.Settings
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.luminance
import io.github.mateuxlucax.caparao.ui.components.PressArt
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mateuxlucax.caparao.BrewWidgetProvider
import io.github.mateuxlucax.caparao.BuildConfig
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.data.export.BrewExportFiles
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticRecorder
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticShare
import io.github.mateuxlucax.caparao.data.local.BrewDatabase
import io.github.mateuxlucax.caparao.data.local.BrewEntity
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.BuiltInRecipes
import io.github.mateuxlucax.caparao.data.local.CoffeeBagRepository
import io.github.mateuxlucax.caparao.data.local.RecipeRepository
import io.github.mateuxlucax.caparao.data.local.SeedBuiltInRecipes
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.BrewSample
import io.github.mateuxlucax.caparao.model.CoffeeBag
import io.github.mateuxlucax.caparao.model.Recipe
import io.github.mateuxlucax.caparao.model.RecipeStep
import io.github.mateuxlucax.caparao.model.ScaleError
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.model.StepKind
import io.github.mateuxlucax.caparao.ui.recipes.RecipeEditorViewModel
import io.github.mateuxlucax.caparao.ui.coffees.CoffeeBagDetailViewModel
import io.github.mateuxlucax.caparao.ui.coffees.CoffeeBagsViewModel
import io.github.mateuxlucax.caparao.ui.recipes.RecipesViewModel
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import io.github.mateuxlucax.caparao.data.local.SettingsStore
import io.github.mateuxlucax.caparao.ui.App
import io.github.mateuxlucax.caparao.ui.AppActions
import io.github.mateuxlucax.caparao.ui.Tab
import io.github.mateuxlucax.caparao.brew.StepState
import io.github.mateuxlucax.caparao.brew.Pace
import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.ui.brew.BrewNumbers
import io.github.mateuxlucax.caparao.ui.brew.BrewStep
import io.github.mateuxlucax.caparao.ui.brew.BrewUiState
import io.github.mateuxlucax.caparao.ui.brew.BrewViewModel
import io.github.mateuxlucax.caparao.ui.brew.FlowChart
import io.github.mateuxlucax.caparao.ui.brew.FlowPoint
import io.github.mateuxlucax.caparao.ui.brew.GuidedStep
import io.github.mateuxlucax.caparao.ui.brews.BrewDetailViewModel
import io.github.mateuxlucax.caparao.ui.brews.BrewsViewModel
import io.github.mateuxlucax.caparao.ui.format.Format
import io.github.mateuxlucax.caparao.ui.home.HomeViewModel
import io.github.mateuxlucax.caparao.ui.onboarding.OnboardingViewModel
import io.github.mateuxlucax.caparao.ui.settings.ScaleStatusViewModel
import io.github.mateuxlucax.caparao.ui.settings.ServerViewModel
import io.github.mateuxlucax.caparao.ui.settings.ExportViewModel
import io.github.mateuxlucax.caparao.ui.settings.DiagnosticsViewModel
import io.github.mateuxlucax.caparao.ui.settings.TargetRatioViewModel
import io.github.mateuxlucax.caparao.ui.theme.AppTheme
import io.github.mateuxlucax.caparao.ui.components.ScaleReadout
import io.github.mateuxlucax.caparao.ui.components.readableOn
import io.github.mateuxlucax.caparao.ui.components.ringStroke
import io.github.mateuxlucax.caparao.ui.components.targetBand
import io.github.mateuxlucax.caparao.ui.components.TargetBand
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestName
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.time.LocalDate
import java.time.ZoneId
/**
 * The app's screens on the JVM (Robolectric) instead of an emulator: onboarding, every tab, espresso and filter
 * brews, the guided flows and both home-screen widget sizes. The recorded sessions are played frame by frame
 * ([SessionScale]), so every flow is deterministic.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenTestSupport(protected val lang: String, protected val theme: String, protected val compactOnly: Boolean = false) {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val testName = TestName()

    protected val context = ApplicationProvider.getApplicationContext<Context>()
    protected val actions = AppActions(requestBluetoothPermissions = { true }, bluetoothPermissionGranted = { true }, enableBluetooth = {}, openAppSettings = {}, requestLocalNetwork = { true })
    protected val db = Room.inMemoryDatabaseBuilder(context, BrewDatabase::class.java).addCallback(SeedBuiltInRecipes).allowMainThreadQueries().build()
    protected val brews = BrewRepository(db.brews())
    protected val recipes = RecipeRepository(db.recipes())
    protected val settings = SettingsStore(context.getSharedPreferences("screen-tests", Context.MODE_PRIVATE))

    /** The server form's fake host check: whether the host is local, gated to inspect the checking state. */
    protected var localServer = false
    protected var localServerGate: CompletableDeferred<Unit>? = null
    protected val serverChecks = mutableListOf<Pair<String, String>>()
    protected val diagnosticScope = MainScope()
    protected val diagnostics = DiagnosticRecorder(
        context.cacheDir.resolve("diagnostic-screen-tests/$lang-$theme-${System.nanoTime()}"),
        diagnosticScope,
    )
    protected val diagnosticShare = DiagnosticShare(context, diagnostics)
    protected val locale get() = context.resources.configuration.locales[0]

    @Before
    fun setUp() {
        if (compactOnly) assumeTrue("compact screen variant only", testName.methodName in setOf("smallScreenWeigh", "smallScreenHome", "smallScreenPortafilter", "smallScreenFilterPrepare"))
        // The animations' still frames (the full cup, the guide's pictures), as on an emulator with animations off.
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        // Brew dates and times read the same wherever this runs.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        runBlocking { diagnostics.stop() }
        diagnosticScope.cancel()
        db.close()
    }

    protected fun text(id: Int) = context.getString(id)

    /** The rendered text of a TextView [id] in an applied widget's view tree. */
    protected fun widgetText(root: View, id: Int): String {
        val view = root.findViewById<TextView>(id)
        assertNotNull("missing TextView $id in the applied widget view tree", view)
        return view!!.text.toString()
    }

    /** Asserts the rendered text of a TextView [id], so a blank or missing widget fails. */
    protected fun assertWidgetText(root: View, id: Int, expected: String) {
        assertEquals(expected, widgetText(root, id))
    }

    /** Asserts a view [id]'s visibility in an applied widget's view tree. */
    protected fun assertWidgetVisibility(root: View, id: Int, visibility: Int) {
        val view = root.findViewById<View>(id)
        assertNotNull("missing view $id in the applied widget view tree", view)
        assertEquals(visibility, view!!.visibility)
    }

    protected fun waitFor(id: Int) = compose.waitUntilAtLeastOneExists(hasText(text(id)), 5_000)

    protected fun click(id: Int) = compose.onNodeWithText(text(id)).performClick()

    /** A node whose whole text matches [pattern]: the readout's weight (a decimal) and timer (mm:ss). */
    protected fun textMatching(pattern: Regex) =
        SemanticsMatcher("text matching $pattern") { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { pattern.matches(it.text) } == true }

    /** The live readout's weight and timer, which stay on screen through the shot. */
    protected fun assertWeightAndTimerShown() {
        compose.onNode(textMatching(Regex("\\d+[.,]\\d"))).assertIsDisplayed()
        compose.onNode(textMatching(Regex("\\d\\d:\\d\\d"))).assertIsDisplayed()
    }

    protected fun open(
        session: String,
        atMs: Long,
        onboardingDone: Boolean = true,
    ): SessionScale {
        val scale = SessionScale(context.assets.open("sessions/$session.jsonl").bufferedReader().use { it.readLines() })
        val factory = viewModelFactory {
            initializer { HomeViewModel(scale) }
            initializer { OnboardingViewModel(scale) {} }
            initializer { ScaleStatusViewModel(scale) }
            initializer { TargetRatioViewModel(settings) }
            initializer { ExportViewModel(BrewExportFiles(context, brews, recipes)) }
            initializer { DiagnosticsViewModel(diagnostics, diagnosticShare) }
            initializer { RecipesViewModel(recipes) }
            initializer { CoffeeBagsViewModel(CoffeeBagRepository(db.coffeeBags())) }
            initializer { CoffeeBagDetailViewModel(requireNotNull(this[CoffeeBagDetailViewModel.BAG_ID]), CoffeeBagRepository(db.coffeeBags()), brews) }
            initializer { RecipeEditorViewModel(requireNotNull(this[RecipeEditorViewModel.TARGET]), recipes) }
            initializer { BrewsViewModel(brews, settings.syncStatus) {} }
            initializer {
                ServerViewModel(
                    settings,
                    { url, token -> serverChecks += url to token; error("no server in screen tests") },
                    {}, {},
                    localNetwork = { localServerGate?.await(); localServer },
                )
            }
            initializer {
                BrewViewModel(
                    requireNotNull(this[BrewViewModel.MODE]), scale, brews, settings, recipes,
                    clock = { scale.nowMs }, wallClock = { BREWED_AT + scale.nowMs }, coffeeBags = CoffeeBagRepository(db.coffeeBags()),
                )
            }
            initializer { BrewDetailViewModel(requireNotNull(this[BrewDetailViewModel.BREW_ID]), brews) }
        }
        scale.start()
        compose.setContent { AppTheme { App(factory, scale, onboardingDone, actions, BuildConfig.VERSION_NAME) } }
        play(scale, atMs)
        return scale
    }

    /** Plays [scale] up to [atMs], letting the brew detector see each frame. */
    protected fun play(scale: SessionScale, atMs: Long) = scale.playUntil(atMs) { shadowOf(Looper.getMainLooper()).idle() }

    /** Plays [scale] in 100 ms steps until [id]'s text shows, at most until [untilMs]. */
    protected fun playUntilShown(scale: SessionScale, id: Int, untilMs: Long) {
        while (compose.onAllNodes(hasText(text(id))).fetchSemanticsNodes().isEmpty()) {
            check(scale.nowMs < untilMs) { "\"${text(id)}\" never showed" }
            play(scale, scale.nowMs + 100)
            compose.waitForIdle()
        }
    }

    protected fun openDiagnosticsCard() {
        open("idle-5min", 0)
        click(R.string.tab_settings)
        compose.onNodeWithText(text(R.string.section_diagnostics)).performScrollTo()
        compose.waitForIdle()
    }

    protected fun diagnosticsAction(id: Int) {
        compose.onNodeWithText(text(id)).performScrollTo().performClick()
    }

    protected fun openV60Editor() {
        open("press-ramp", 38_300)
        click(R.string.tab_recipes)
        compose.waitUntilAtLeastOneExists(hasText("V60 One and Done"), 5_000)
        compose.onNodeWithText("V60 One and Done").performClick()
        waitFor(R.string.recipe_name)
    }

    /** Back in an untouched recipe editor closes at once (#181, B1). */
    protected fun seedCoffeeBags() {
        val day = 86_400_000L
        val now = System.currentTimeMillis()
        runBlocking {
            CoffeeBagRepository(db.coffeeBags()).save(CoffeeBag("bag-1", "Caparaó natural", roastedAt = now - 12 * day, weightGrams = 250.0, createdAt = now))
            CoffeeBagRepository(db.coffeeBags()).save(CoffeeBag("bag-2", "Mantiqueira", weightGrams = 10.0, createdAt = now - day))
        }
    }

    protected fun coffeeStepBeforeTheBeans(session: String, pickMode: () -> Unit) {
        seedCoffeeBags()
        open(session, 0)
        pickMode()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.milestone_coffee)), 5_000)
        click(R.string.next)
        waitFor(R.string.coffee_step_title)
        compose.onNode(hasText("Caparaó natural") and isSelected()).assertExists()
        compose.onNode(hasText("Mantiqueira") and isNotSelected()).assertExists()
        compose.onNodeWithText(text(R.string.coffee_below_dose)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.no_bag)).performClick()
        compose.onNode(hasText(text(R.string.no_bag)) and isSelected()).assertExists()
        compose.onNodeWithText("Mantiqueira").performClick()
        compose.onNode(hasText("Mantiqueira") and isSelected()).assertExists()
        compose.onNode(hasTestTag("coffee-step-next")).performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 5_000)
    }

    protected fun openCoffeeBag(withBrew: Boolean = true) {
        val day = 86_400_000L
        val now = System.currentTimeMillis()
        runBlocking {
            CoffeeBagRepository(db.coffeeBags()).save(CoffeeBag("bag-1", "Caparaó natural", roaster = "Torra Alta", roastedAt = now - 12 * day, weightGrams = 250.0, createdAt = now))
        }
        if (withBrew) linkBrew("brew-1", "bag-1", 18.0, rating = 4)
        open("press-ramp", 38_300)
        click(R.string.tab_coffees)
        compose.waitUntilAtLeastOneExists(hasText("Caparaó natural", substring = true), 5_000)
        compose.onNodeWithText("Caparaó natural", substring = true).performClick()
        val left = if (withBrew) "232" else "250"
        compose.waitUntilAtLeastOneExists(hasText(context.getString(R.string.coffee_left, left, "250")), 5_000)
    }

    /** The last node with [id]: a dialog's button sits above the screen's, which stays in the tree. */
    protected fun clickLast(id: Int) = compose.onAllNodesWithText(text(id)).onLast().performClick()

    /** The Coffees tab's add sheet (C3): Save waits for a name and a weight; More details holds origin and notes. */
    protected fun linkBrew(id: String, bagId: String, dose: Double, rating: Int? = null) = runBlocking {
        db.brews().insertWithSamples(
            BrewEntity(
                id = id, mode = "FILTER", startedAt = System.currentTimeMillis(), durationMs = 60_000, doseGrams = dose,
                yieldGrams = 250.0, timeToFirstDropMs = null, flowAvg = 1.0, flowPeak = 2.0, interrupted = false,
                syncedAt = null, coffee = null, rating = rating, bagId = bagId,
            ),
            emptyList(),
        )
    }

    protected fun serverField(label: Int, value: String) {
        compose.onNode(hasSetTextAction() and hasText(text(label))).performScrollTo().performTextReplacement(value)
    }

    protected fun espressoSummary() {
        val scale = open("synthetic-espresso", 0)
        click(R.string.mode_espresso)
        compose.waitForIdle()
        click(R.string.next)
        compose.waitForIdle()
        click(R.string.next)
        click(R.string.prepare_ready)
        play(scale, 21_000)
        compose.waitForIdle()
        playUntilShown(scale, R.string.brew_saved, 60_000)
        compose.waitForIdle()
    }

    /** The top bar's arrow, which runs the same action as Back. */
    protected fun tapBack() = compose.onNodeWithContentDescription(text(R.string.back)).performClick()

    /** The recipe editor's arrow, which is still labelled Close. */
    protected fun tapEditorClose() = compose.onNodeWithContentDescription(text(R.string.close)).performClick()

    /** The top bar's X, which closes the whole brew from the dose, beans and preparation steps (#185). */
    protected fun tapCloseBrew() = compose.onNodeWithContentDescription(text(R.string.close_brew)).performClick()

    /** Back on the summary saves the typed notes into the saved brew and closes, with no dialog (#181, A1 → A2). */
    protected fun espressoDose() {
        open("synthetic-espresso", 0)
        click(R.string.mode_espresso)
        compose.waitForIdle()
    }

    /** Waits until the brew has closed and the tabs are back. */
    protected fun waitForBrewClosed() {
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("brew-screen")).fetchSemanticsNodes().isEmpty() }
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.tab_home)), 5_000)
    }

    /** On the dose step, Back and the X both close the brew (#185, Dose row). */
    protected fun espressoLive() {
        val scale = open("synthetic-espresso-guide", 0)
        click(R.string.mode_espresso)
        compose.waitUntilAtLeastOneExists(hasText("Espresso Oster") and isSelectable(), 5_000)
        compose.onNode(hasText("Espresso Oster") and isSelectable()).performClick()
        click(R.string.next)
        click(R.string.next)
        click(R.string.prepare_ready)
        compose.waitForIdle()
        playUntilShown(scale, R.string.brewing_espresso, 60_000)
        compose.waitForIdle()
    }

    /** Back in Live asks to restart the brew, and Cancel keeps the shot running (#287). */
    protected fun pressArtStages(animated: Boolean) {
        val recipe = Recipe(
            id = "press-art-$animated", name = "Press art", mode = BrewMode.FRENCH_PRESS, method = "French press",
            doseGrams = 20.0, targetRatio = 15.0,
            steps = listOf(
                RecipeStep(StepKind.BLOOM, 100.0, 0),
                RecipeStep(StepKind.POUR, 300.0, 60_000),
                RecipeStep(StepKind.PRESS, null, 240_000),
            ),
            createdAt = 0, updatedAt = 0,
        )
        fun state(
            index: Int,
            weight: Double = 0.0,
            progress: Float = 0f,
            elapsedMs: Long = 0,
            removed: Boolean = false,
            dueInMs: Long? = null,
        ) = BrewUiState(
            mode = BrewMode.FRENCH_PRESS, step = BrewStep.LIVE, phase = BrewPhase.BREWING, connected = true,
            weightGrams = weight, elapsedMs = elapsedMs, recipe = recipe, frenchPressRemoved = removed,
            guide = StepState(index, progress, dueInMs, null, null, null),
        )

        var current by mutableStateOf(state(0, weight = 100.0))
        var scheme = lightColorScheme()
        var dark = false
        compose.setContent {
            AppTheme {
                scheme = MaterialTheme.colorScheme
                dark = isSystemInDarkTheme()
                GuidedStep(current) {}
            }
        }

        fun hasColor(tag: String, color: Color): Boolean {
            val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            val expected = color.toArgb()
            return (0 until bitmap.height).any { y -> (0 until bitmap.width).any { x -> bitmap.getPixel(x, y) == expected } }
        }

        /**
         * The pixel of a point in the press's 80 × 90 box (PressArt.drawPress), drawn under a [lift] of the scene and a
         * [scaleRoom] under it (0 without the scale, see PressOnScale).
         */
        fun pixelAt(tag: String, viewX: Float, viewY: Float, lift: Float, scaleRoom: Float = 0.12f): Int {
            val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            val size = bitmap.width.toFloat()
            val room = size * scaleRoom
            val rise = size * 0.16f * lift
            val boxHeight = size - room
            val scale = minOf(size / 80f, boxHeight / 90f)
            // The beaker's center line (x = 36 in the box) sits on the canvas center, over the scale.
            val x = size / 2f + (viewX - 36f) * scale
            val y = -rise + (boxHeight - 90f * scale) / 2f + viewY * scale
            return bitmap.getPixel(x.toInt(), y.toInt())
        }

        // Fill: the carafe takes the band color of the weight against the target, with no target line.
        for ((weight, band) in listOf(60.0 to TargetBand.BELOW, 100.0 to TargetBand.AT, 120.0 to TargetBand.ABOVE)) {
            current = state(0, weight = weight)
            compose.waitForIdle()
            compose.onNodeWithTag("guided-server").assertIsDisplayed()
            assertEquals(band, targetBand(weight, 100.0))
            assertTrue("the fill follows the band", hasColor("guided-cup", scheme.ringStroke(band, dark)))
            // The lid is not on yet: the spot where it sits in the steep is empty.
            assertTrue("no lid before the steep", pixelAt("guided-cup", 24f, 19.5f, lift = 0f, scaleRoom = 0.12f) != scheme.outline.toArgb())
            compose.onNodeWithTag("guided-weight").assertIsDisplayed()
            compose.onNodeWithText("${Format.grams(weight, locale)} / ${Format.grams(100.0, locale)} g").assertIsDisplayed()
        }

        // Steep: the coffee darkens on the shape with the brew toward the deadline, as the bar fills, and the countdown
        // is the time left to it. The states are the ones StepTracker produces: dueInMs = deadline - elapsed.
        for ((elapsed, stepProgress) in listOf(100_000L to 0f, 230_000L to 0.93f)) {
            current = state(2, weight = 300.0, progress = stepProgress, elapsedMs = elapsed, dueInMs = 240_000 - elapsed)
            compose.waitForIdle()
            compose.onNodeWithTag("steep-shape").assertIsDisplayed()
            compose.onNodeWithTag("steep-press").assertIsDisplayed()
            assertTrue("steep at $elapsed", hasColor("steep-press", PressArt.steepColor(elapsed / 240_000f, scheme.primary)))
            // The lid is on from the steep on: its bar sits above the frame.
            assertEquals("the lid at $elapsed", scheme.outline.toArgb(), pixelAt("steep-press", 24f, 19.5f, lift = 0f, scaleRoom = 0f))
            compose.onNodeWithText(Format.timer(((240_000 - elapsed) / 1000).toInt())).assertIsDisplayed()
        }
        assertTrue(PressArt.steepColor(1f, scheme.primary).luminance() < PressArt.steepColor(0f, scheme.primary).luminance())

        // Deadline: the carafe at its darkest, the delay in view.
        current = state(3, weight = 300.0, elapsedMs = 255_000)
        compose.waitForIdle()
        compose.onNodeWithTag("press-deadline-art").assertIsDisplayed()
        assertTrue(hasColor("guided-cup", PressArt.steepColor(1f, scheme.primary)))
        compose.onNodeWithText(text(R.string.guide_remove_press)).assertIsDisplayed()
        compose.onNodeWithTag("press-delay").assertIsDisplayed()
        compose.onNodeWithText("+${Format.timer(15)}").assertIsDisplayed()

        // Removal: the press on its shape with the plunger in the coffee, in both settings (#302).
        current = state(3, weight = 0.0, elapsedMs = 270_000, removed = true)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.guide_press_serve)), 5_000)
        compose.waitForIdle()
        compose.onNodeWithTag("press-serve-shape").assertIsDisplayed()
        assertEquals("the plunger plate", scheme.outline.toArgb(), pixelAt("press-serve", 30f, 74f, lift = 0f, scaleRoom = 0f))
        compose.onNodeWithText(text(R.string.guide_press_serve_text)).assertIsDisplayed()
    }

    /** Filter waits and swirls count down on the smooth ring; the French press steep has its own clock (#302). */
    protected fun assertMilestone(name: Int) {
        compose.onNode(hasContentDescription(text(name), substring = true)).assertIsDisplayed()
    }

    /** Water is the French press recipe's control: 400 ml scales the dose and weights, and the steep stays at 4:00 (#215). */
    protected fun openFreePress(steep: String? = null, waterMl: String? = null): SessionScale {
        val scale = open("synthetic-french-press", 0)
        compose.onNodeWithContentDescription(text(R.string.mode_french_press)).performClick()
        compose.waitUntilAtLeastOneExists(hasText("French press") and isSelectable(), 5_000)
        click(R.string.free_brew)
        compose.waitUntilAtLeastOneExists(hasTestTag("press-steep-field"), 5_000)
        val confirm = hasText(text(R.string.done)) and hasClickAction()
        steep?.let {
            compose.onNodeWithTag("press-steep-field").performClick()
            compose.onNodeWithTag("press-steep-input").performTextReplacement(it)
            compose.onNode(confirm).performClick()
        }
        waterMl?.let {
            compose.onNodeWithText("${Format.grams(300.0, locale)} ${text(R.string.unit_ml)}").performClick()
            compose.onNode(hasSetTextAction()).performTextReplacement(it)
            compose.onNode(confirm).performClick()
        }
        click(R.string.next)
        compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 5_000)
        compose.onNodeWithTag("weigh-step-next").performClick()
        waitFor(R.string.prepare_press_title)
        click(R.string.prepare_ready)
        return scale
    }

    /** Reaching the target water starts the steep at once (#218). */
    protected fun frenchPressSteep(session: String = "synthetic-french-press"): SessionScale {
        val scale = open(session, 0)
        compose.onNodeWithContentDescription(text(R.string.mode_french_press)).performClick()
        compose.waitUntilAtLeastOneExists(hasText("French press") and isSelectable(), 5_000)
        click(R.string.next)
        compose.waitUntilAtLeastOneExists(hasTestTag("weigh-step-next"), 5_000)
        compose.onNodeWithTag("weigh-step-next").performClick()
        waitFor(R.string.prepare_press_title)
        click(R.string.prepare_ready)
        return scale
    }

    /** Before the deadline Done asks first (#222). */
    protected fun curve(water: Double, durationMs: Long) = (0..durationMs / 1000).map { s ->
        val f = s * 1000.0 / durationMs
        val weight = water * f * f * (3 - 2 * f)
        BrewSample(s * 1000, weight, water * 6 * f * (1 - f) / (durationMs / 1000.0))
    }

    private companion object {
        /** 12 October 2026, 08:30 UTC: the saved brews' date. */
        const val BREWED_AT = 1_791_793_800_000L

    }
}
