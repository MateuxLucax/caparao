package dev.mateux.caparao.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.ViewModelStoreNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.mateux.caparao.R
import dev.mateux.caparao.brew.BrewPhase
import dev.mateux.caparao.data.scale.ScaleSource
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.ui.brew.BrewScreen
import dev.mateux.caparao.ui.brew.BrewViewModel
import dev.mateux.caparao.ui.brews.BrewDetailScreen
import dev.mateux.caparao.ui.brews.BrewDetailViewModel
import dev.mateux.caparao.ui.brews.BrewRatingScreen
import dev.mateux.caparao.ui.brews.BrewRatingViewModel
import dev.mateux.caparao.ui.brews.BrewSuggestionScreen
import dev.mateux.caparao.ui.brews.BrewSuggestionViewModel
import dev.mateux.caparao.ui.brews.BrewsScreen
import dev.mateux.caparao.ui.coffees.CoffeeAdjustmentScreen
import dev.mateux.caparao.ui.coffees.CoffeeAdjustmentViewModel
import dev.mateux.caparao.ui.coffees.CoffeeBagDetailScreen
import dev.mateux.caparao.ui.coffees.CoffeeBagDetailViewModel
import dev.mateux.caparao.ui.coffees.CoffeeBagsViewModel
import dev.mateux.caparao.ui.coffees.CoffeesScreen
import dev.mateux.caparao.ui.components.AppActions
import dev.mateux.caparao.ui.components.LocalSystemBack
import dev.mateux.caparao.ui.components.ScaleStatusIcon
import dev.mateux.caparao.ui.home.HomeScreen
import dev.mateux.caparao.ui.home.HomeViewModel
import dev.mateux.caparao.ui.components.LauncherShortcut
import dev.mateux.caparao.ui.components.ShortcutDestination
import dev.mateux.caparao.ui.onboarding.OnboardingScreen
import dev.mateux.caparao.ui.recipes.RecipeEditorScreen
import dev.mateux.caparao.ui.recipes.RecipeEditorViewModel
import dev.mateux.caparao.ui.recipes.RecipesScreen
import dev.mateux.caparao.ui.settings.SettingsScreen
import dev.mateux.caparao.ui.settings.CaptureViewModel
import dev.mateux.caparao.ui.settings.ScaleStatusViewModel
import dev.mateux.caparao.ui.server.ServerViewModel
import dev.mateux.caparao.ui.settings.SettingsDetail
import dev.mateux.caparao.ui.settings.SettingsDetailScreen
import dev.mateux.caparao.ui.theme.Motion
import kotlinx.serialization.Serializable

/** The bottom-bar tabs, in order. */
enum class Tab(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    HOME(R.string.tab_home, R.drawable.ic_home),
    RECIPES(R.string.tab_recipes, R.drawable.ic_menu_book),
    COFFEES(R.string.tab_coffees, R.drawable.ic_coffee),
    BREWS(R.string.tab_brews, R.drawable.ic_receipt_long),
    SETTINGS(R.string.tab_settings, R.drawable.ic_settings),
}

/**
 * Onboarding on first launch, then the four tabs under a top bar with the name and the scale connection.
 * The activity owns the scale connection so configuration changes can reuse it; resume still retries (#364).
 *
 * [shortcut] is the launcher shortcut still to apply (#116), consumed with [onShortcutConsumed] once [Main] has opened
 * its destination. It waits here, above the onboarding gate, so a first run still onboards and then honors it.
 */
@Composable
fun App(
    viewModelFactory: ViewModelProvider.Factory,
    scale: ScaleSource,
    onboardingDone: Boolean,
    actions: AppActions,
    versionName: String,
    shortcut: LauncherShortcut? = null,
    onShortcutConsumed: () -> Unit = {},
    entryStoreProvider: ViewModelStoreProvider? = null,
) {
    LifecycleStartEffect(scale) {
        scale.start()
        onStopOrDispose { scale.stop() }
    }
    LifecycleResumeEffect(scale) {
        scale.start()
        onPauseOrDispose {}
    }
    var onboarded by rememberSaveable { mutableStateOf(onboardingDone) }
    if (!onboarded) {
        OnboardingScreen(viewModel(factory = viewModelFactory), viewModel(key = "server-onboarding", factory = viewModelFactory), actions) {
            onboarded = true
        }
    } else {
        Main(viewModelFactory, actions, versionName, shortcut, onShortcutConsumed, entryStoreProvider)
    }
}

/** The tabs, or a brew on top of them while one is open. */
@Composable
private fun Main(
    viewModelFactory: ViewModelProvider.Factory,
    actions: AppActions,
    versionName: String,
    shortcut: LauncherShortcut?,
    onShortcutConsumed: () -> Unit,
    entryStoreProvider: ViewModelStoreProvider?,
) {
    val backStack = rememberNavBackStack(Destination.Tabs)
    // Settings and its detail entries must observe the same capture instance: each instance refreshes pending uploads.
    val settingsServer: ServerViewModel = viewModel(key = "server-settings", factory = viewModelFactory)
    val settingsScale: ScaleStatusViewModel = viewModel(factory = viewModelFactory)
    val settingsDiagnostics: CaptureViewModel = viewModel(factory = viewModelFactory)
    val openBrew = remember { mutableStateOf<OpenBrew?>(null) }
    var shortcutTab by remember { mutableStateOf<Tab?>(null) }
    // Each entry's own system-back policy: a screen registers one when back does more than pop it (#352).
    val backActions = remember { mutableStateMapOf<Destination, () -> Unit>() }
    val density = LocalDensity.current
    val viewModelStoreDecorator: ViewModelStoreNavEntryDecorator<NavKey> = if (entryStoreProvider == null) {
        rememberViewModelStoreNavEntryDecorator()
    } else {
        rememberViewModelStoreNavEntryDecorator(entryStoreProvider)
    }

    fun clearToTabs() {
        while (backStack.size > 1) backStack.removeLastOrNull()
    }

    // Navigation 3 owns system back (#352): a screen with custom back (brew steps, discarding a dirty recipe) registers
    // it; every other screen just pops itself, guarded so a second gesture during the exit transition cannot empty
    // the stack.
    fun handleBack() {
        val top = backStack.lastOrNull() ?: return
        val action = backActions[top]
        if (action != null) action() else backStack.popEntry(top)
    }

    LaunchedEffect(shortcut) {
        val requested = shortcut?.destination ?: return@LaunchedEffect
        val open = openBrew.value
        if (
            open?.running() == true ||
            requested is ShortcutDestination.Brew && requested.mode == (backStack.lastOrNull() as? Destination.Brew)?.mode
        ) {
            onShortcutConsumed()
            return@LaunchedEffect
        }

        open?.close()
        clearToTabs()
        when (requested) {
            is ShortcutDestination.Brew -> {
                backStack.add(Destination.Brew(requested.mode))
                onShortcutConsumed()
            }
            ShortcutDestination.Home -> shortcutTab = Tab.HOME
            ShortcutDestination.Brews -> shortcutTab = Tab.BREWS
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { handleBack() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            viewModelStoreDecorator,
        ),
        entryProvider = entryProvider {
            entry<Destination.Tabs> {
                Tabs(
                    viewModelFactory, actions, versionName, shortcutTab, settingsServer, settingsScale, settingsDiagnostics,
                    onShortcutTabConsumed = { shortcutTab = null; onShortcutConsumed() },
                    onBrew = { backStack.add(Destination.Brew(it)) },
                    onOpen = { backStack.add(Destination.Detail(it)) },
                    onRate = { backStack.add(Destination.Rating(it)) },
                    onRecipe = { backStack.add(Destination.Recipe(it)) },
                    onCoffee = { backStack.add(Destination.Coffee(it)) },
                    onSettings = { backStack.add(Destination.Settings(it)) },
                )
            }
            entry<Destination.Brew> { shown ->
                val extras = MutableCreationExtras(LocalViewModelStoreOwner.current.defaultCreationExtras()).apply { set(BrewViewModel.MODE, shown.mode) }
                val brew: BrewViewModel = viewModel(key = "brew-${shown.mode.name}", factory = viewModelFactory, extras = extras)
                val close = remember(brew, shown) {
                    var ended = false
                    {
                        // Idempotent: a second close or system back during the exit transition must not end the brew
                        // again or pop the entry underneath (#352).
                        if (!ended) {
                            ended = true
                            brew.end()
                        }
                        backStack.popEntry(shown)
                        openBrew.value = null
                    }
                }
                LaunchedEffect(brew) { brew.begin() }
                DisposableEffect(brew) {
                    val open = OpenBrew(shown.mode, running = { brew.uiState.value.phase == BrewPhase.BREWING || brew.uiState.value.phase == BrewPhase.PAUSED }, close = close)
                    openBrew.value = open
                    onDispose { if (openBrew.value === open) openBrew.value = null }
                }
                CompositionLocalProvider(LocalSystemBack provides rememberSystemBack(shown, backActions)) {
                    BrewScreen(brew, onClose = close, onRate = { backStack.add(Destination.Rating(it)) })
                }
            }
            entry<Destination.Detail> { shown ->
                val extras = MutableCreationExtras(LocalViewModelStoreOwner.current.defaultCreationExtras()).apply { set(BrewDetailViewModel.BREW_ID, shown.id) }
                BrewDetailScreen(
                    viewModel(key = "detail-${shown.id}", factory = viewModelFactory, extras = extras),
                    onRate = { backStack.add(Destination.Rating(it)) },
                    onClose = { backStack.popEntry(shown) },
                )
            }
            entry<Destination.Rating> { shown ->
                val extras = MutableCreationExtras(LocalViewModelStoreOwner.current.defaultCreationExtras()).apply { set(BrewRatingViewModel.BREW_ID, shown.id) }
                BrewRatingScreen(
                    viewModel(key = "rating-${shown.id}", factory = viewModelFactory, extras = extras),
                    onClose = { backStack.popEntry(shown) },
                    // The suggestion takes the rating's place, so Back from it does not return to the form.
                    onSuggest = {
                        backStack.add(Destination.Suggestion(shown.id))
                        backStack.remove(shown)
                    },
                )
            }
            entry<Destination.Suggestion> { shown ->
                val extras = MutableCreationExtras(LocalViewModelStoreOwner.current.defaultCreationExtras()).apply { set(BrewSuggestionViewModel.BREW_ID, shown.id) }
                BrewSuggestionScreen(
                    viewModel(key = "suggestion-${shown.id}", factory = viewModelFactory, extras = extras),
                    onEditAdjustment = { bagId, recipeId ->
                        backStack.add(Destination.Adjustment(bagId, recipeId))
                        backStack.remove(shown)
                    },
                    onClose = { backStack.popEntry(shown) },
                )
            }
            entry<Destination.Coffee> { shown ->
                val extras = MutableCreationExtras(LocalViewModelStoreOwner.current.defaultCreationExtras()).apply { set(CoffeeBagDetailViewModel.BAG_ID, shown.id) }
                CoffeeBagDetailScreen(
                    viewModel(key = "coffee-${shown.id}", factory = viewModelFactory, extras = extras),
                    viewModel(factory = viewModelFactory),
                    onOpenBrew = { backStack.add(Destination.Detail(it)) },
                    onOpenAdjustment = { backStack.add(Destination.Adjustment(shown.id, it)) },
                    onClose = { backStack.popEntry(shown) },
                )
            }
            entry<Destination.Adjustment> { shown ->
                val extras = MutableCreationExtras(LocalViewModelStoreOwner.current.defaultCreationExtras()).apply {
                    set(CoffeeAdjustmentViewModel.TARGET, CoffeeAdjustmentViewModel.Target(shown.bagId, shown.recipeId))
                }
                CoffeeAdjustmentScreen(
                    viewModel(key = "adjustment-${shown.bagId}-${shown.recipeId}", factory = viewModelFactory, extras = extras),
                    onClose = { backStack.popEntry(shown) },
                )
            }
            entry<Destination.Recipe> { shown ->
                val extras = MutableCreationExtras(LocalViewModelStoreOwner.current.defaultCreationExtras()).apply { set(RecipeEditorViewModel.TARGET, shown.target) }
                CompositionLocalProvider(LocalSystemBack provides rememberSystemBack(shown, backActions)) {
                    RecipeEditorScreen(viewModel(key = "recipe-${shown.target}", factory = viewModelFactory, extras = extras), onClose = { backStack.popEntry(shown) })
                }
            }
            entry<Destination.Settings> { shown ->
                SettingsDetailScreen(
                    shown.detail,
                    settingsServer,
                    settingsScale,
                    settingsDiagnostics,
                    actions,
                    onClose = { backStack.popEntry(shown) },
                )
            }
        },
        transitionSpec = { Motion.sharedAxisX(forward = true, density) },
        popTransitionSpec = { Motion.sharedAxisX(forward = false, density) },
        predictivePopTransitionSpec = { Motion.sharedAxisX(forward = false, density) },
    )
}

/**
 * The brew screen on top of the tabs (#116): its [mode], whether it is [running], and how to [close] it. A launcher
 * shortcut consults it before ending a brew or reopening a mode that is already on screen.
 */
private class OpenBrew(val mode: BrewMode, val running: () -> Boolean, val close: () -> Unit)

/** The registrar a screen uses to claim system back for [key]; stable per entry so registration does not churn (#352). */
@Composable
private fun rememberSystemBack(
    key: Destination,
    backActions: MutableMap<Destination, () -> Unit>,
): ((() -> Unit)?) -> Unit = remember(key, backActions) {
    { action -> if (action == null) backActions.remove(key) else backActions[key] = action }
}

/** Each entry's key is saved by Navigation 3 across rotation and process recreation. */
@Serializable
internal sealed interface Destination : NavKey {
    @Serializable data object Tabs : Destination
    @Serializable data class Brew(val mode: BrewMode) : Destination
    @Serializable data class Detail(val id: String) : Destination
    @Serializable data class Rating(val id: String) : Destination
    @Serializable data class Suggestion(val id: String) : Destination
    @Serializable data class Recipe(val target: RecipeEditorViewModel.Target) : Destination
    @Serializable data class Coffee(val id: String) : Destination
    @Serializable data class Adjustment(val bagId: String, val recipeId: String) : Destination
    @Serializable data class Settings(val detail: SettingsDetail) : Destination
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Tabs(
    viewModelFactory: ViewModelProvider.Factory,
    actions: AppActions,
    versionName: String,
    shortcutTab: Tab?,
    settingsServer: ServerViewModel,
    settingsScale: ScaleStatusViewModel,
    settingsDiagnostics: CaptureViewModel,
    onShortcutTabConsumed: () -> Unit,
    onBrew: (BrewMode) -> Unit,
    onOpen: (String) -> Unit,
    onRate: (String) -> Unit,
    onRecipe: (RecipeEditorViewModel.Target) -> Unit,
    onCoffee: (String) -> Unit,
    onSettings: (SettingsDetail) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    LaunchedEffect(shortcutTab) {
        shortcutTab?.let { tab = it; onShortcutTabConsumed() }
    }
    // The Home one, so the bar and the screen share a connection state.
    val scale: HomeViewModel = viewModel(factory = viewModelFactory)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (tab == Tab.SETTINGS) tab.label else R.string.app_name),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                },
                actions = {
                    if (tab != Tab.SETTINGS) {
                        ScaleStatusIcon(scale.uiState.collectAsStateWithLifecycle().value.scale, Modifier.padding(end = 12.dp))
                    }
                },
            )
        },
        bottomBar = {
            ShortNavigationBar {
                Tab.entries.forEach { entry ->
                    ShortNavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(painterResource(entry.icon), contentDescription = null) },
                        label = { Text(stringResource(entry.label)) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding).consumeWindowInsets(padding)
        AnimatedContent(tab, transitionSpec = { Motion.fadeThrough() }, label = "tab") { shown ->
            when (shown) {
                Tab.HOME -> HomeScreen(scale, actions, onBrew, modifier)
                Tab.RECIPES -> RecipesScreen(
                    viewModel(factory = viewModelFactory),
                    onOpen = { onRecipe(RecipeEditorViewModel.Target.Edit(it.id)) },
                    onNew = { onRecipe(RecipeEditorViewModel.Target.New(it)) },
                    modifier,
                )
                Tab.COFFEES -> CoffeesScreen(viewModel(factory = viewModelFactory), onOpen = onCoffee, modifier = modifier)
                Tab.BREWS -> BrewsScreen(viewModel(factory = viewModelFactory), actions, onOpen = { onOpen(it.id) }, onRate = { onRate(it.id) }, onOpenSettings = { tab = Tab.SETTINGS }, modifier = modifier)
                Tab.SETTINGS -> SettingsScreen(
                    settingsServer,
                    settingsScale,
                    viewModel(factory = viewModelFactory),
                    settingsDiagnostics,
                    actions,
                    versionName,
                    onSettings,
                    modifier,
                )
            }
        }
    }
}

private fun androidx.lifecycle.ViewModelStoreOwner?.defaultCreationExtras(): CreationExtras =
    (this as? HasDefaultViewModelProviderFactory)?.defaultViewModelCreationExtras ?: CreationExtras.Empty
