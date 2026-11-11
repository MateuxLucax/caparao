package io.github.mateuxlucax.caparao.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.brew.BrewPhase
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.ui.brew.BrewScreen
import io.github.mateuxlucax.caparao.ui.brew.BrewViewModel
import io.github.mateuxlucax.caparao.ui.brews.BrewDetailScreen
import io.github.mateuxlucax.caparao.ui.brews.BrewDetailViewModel
import io.github.mateuxlucax.caparao.ui.brews.BrewsScreen
import io.github.mateuxlucax.caparao.ui.brews.CompareScreen
import io.github.mateuxlucax.caparao.ui.brews.CompareViewModel
import io.github.mateuxlucax.caparao.ui.brews.TrendKey
import io.github.mateuxlucax.caparao.ui.brews.TrendsScreen
import io.github.mateuxlucax.caparao.ui.brews.TrendsViewModel
import io.github.mateuxlucax.caparao.ui.coffees.CoffeeBagDetailScreen
import io.github.mateuxlucax.caparao.ui.coffees.CoffeeBagDetailViewModel
import io.github.mateuxlucax.caparao.ui.coffees.CoffeeBagsViewModel
import io.github.mateuxlucax.caparao.ui.coffees.CoffeesScreen
import io.github.mateuxlucax.caparao.ui.components.ScaleStatusIcon
import io.github.mateuxlucax.caparao.ui.home.HomeScreen
import io.github.mateuxlucax.caparao.ui.home.HomeViewModel
import io.github.mateuxlucax.caparao.ui.launcher.LauncherShortcut
import io.github.mateuxlucax.caparao.ui.launcher.ShortcutDestination
import io.github.mateuxlucax.caparao.ui.onboarding.OnboardingScreen
import io.github.mateuxlucax.caparao.ui.recipes.RecipeEditorScreen
import io.github.mateuxlucax.caparao.ui.recipes.RecipeEditorViewModel
import io.github.mateuxlucax.caparao.ui.recipes.RecipesScreen
import io.github.mateuxlucax.caparao.ui.settings.SettingsScreen
import io.github.mateuxlucax.caparao.ui.theme.Motion

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
    onboardingDone: Boolean,
    actions: AppActions,
    versionName: String,
    shortcut: LauncherShortcut? = null,
    onShortcutConsumed: () -> Unit = {},
) {
    var onboarded by rememberSaveable { mutableStateOf(onboardingDone) }
    if (!onboarded) {
        OnboardingScreen(viewModel(factory = viewModelFactory), viewModel(key = "server-onboarding", factory = viewModelFactory), actions) {
            onboarded = true
        }
    } else {
        Main(viewModelFactory, actions, versionName, shortcut, onShortcutConsumed)
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
) {
    var brewing by rememberSaveable { mutableStateOf<BrewMode?>(null) }
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    // A coffee bag open over the tabs (#290); a brew opened from it goes on top and comes back to it.
    var coffee by rememberSaveable { mutableStateOf<String?>(null) }
    // "edit:<id>" or "new:<mode>", saveable as a string.
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    // Comma-joined ids and TrendKey.encode(), so both survive a rotation; they open over a saved brew or the tabs.
    var comparing by rememberSaveable { mutableStateOf<String?>(null) }
    var trends by rememberSaveable { mutableStateOf<String?>(null) }
    // Here and not in Tabs, which leaves the composition while a brew is open: closing it goes back to the same tab.
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    // The brew screen on top of the tabs, registered by it: a shortcut compares against it before closing anything.
    // Dropped when that screen goes away, by close() or by leaving the composition, but never over a newer
    // registration: AnimatedContent still keeps the outgoing brew composed during the transition.
    val openBrew = remember { mutableStateOf<OpenBrew?>(null) }
    // Each destination's ViewModels, held by the activity so a rotation keeps them; a destination that leaves for
    // good is cleared once its exit transition ends (#310).
    val stores = viewModel<DestinationStores>(key = DestinationStores.KEY)
    // What closing the current screen goes back to, deepest first: a destination still on this path stays open (and
    // keeps its ViewModels) while a deeper one is on screen, and is released only when it is not here any more.
    val openDestinations = remember {
        {
            buildList {
                add(Destination.Tabs)
                coffee?.let { add(Destination.Coffee(it)) }
                brewing?.let { add(Destination.Brew(it)) }
                opened?.let { add(Destination.Detail(it)) }
                comparing?.let { add(Destination.Compare(it.split(','))) }
                trends?.let { add(Destination.Trends(TrendKey.decode(it))) }
                editing?.let { add(Destination.Recipe(it)) }
            }
        }
    }
    LaunchedEffect(shortcut) {
        val requested = shortcut?.destination ?: return@LaunchedEffect
        val open = openBrew.value
        // A brew in progress is never interrupted: the shortcut is consumed and the brew stays, whichever one it was.
        if (open?.running() == true) {
            onShortcutConsumed()
            return@LaunchedEffect
        }
        // The open mode keeps its screen, its ViewModel and its scale listener instead of being ended and reopened.
        if (open != null && requested == ShortcutDestination.Brew(open.mode)) {
            onShortcutConsumed()
            return@LaunchedEffect
        }
        opened = null
        editing = null
        comparing = null
        trends = null
        if (open != null) open.close() else brewing = null
        when (requested) {
            is ShortcutDestination.Brew -> brewing = requested.mode
            ShortcutDestination.Brews -> tab = Tab.BREWS
            ShortcutDestination.Home -> tab = Tab.HOME
        }
        onShortcutConsumed()
    }
    val destination = editing?.let { Destination.Recipe(it) }
        ?: trends?.let { Destination.Trends(TrendKey.decode(it)) }
        ?: comparing?.let { Destination.Compare(it.split(',')) }
        ?: opened?.let { Destination.Detail(it) } ?: brewing?.let { Destination.Brew(it) }
        ?: coffee?.let { Destination.Coffee(it) } ?: Destination.Tabs
    val density = LocalDensity.current
    // Deeper destinations come in from the right and go back out to the left, whichever screen they open over.
    AnimatedContent(destination, transitionSpec = { Motion.sharedAxisX(targetState.depth > initialState.depth, density) }, label = "screen") { shown ->
        val key = shown.storeKey
        CompositionLocalProvider(LocalViewModelStoreOwner provides stores.owner(key)) {
            // Runs when the exit transition has finished and the screen is gone, not while it is merely hidden under a
            // deeper one: the destination then still sits on the path to the tabs (#310).
            DisposableEffect(shown) {
                onDispose { if (shown !in openDestinations()) stores.release(key) }
            }
            when (shown) {
                is Destination.Detail -> {
                    val extras = MutableCreationExtras(LocalViewModelStoreOwner.current.defaultCreationExtras()).apply { set(BrewDetailViewModel.BREW_ID, shown.id) }
                    BrewDetailScreen(
                        viewModel(key = "detail-${shown.id}", factory = viewModelFactory, extras = extras),
                        onClose = { opened = null },
                        onCompare = { comparing = it.joinToString(",") },
                        onTrends = { trends = it.encode() },
                    )
                }
                is Destination.Compare -> {
                    val extras = MutableCreationExtras(LocalViewModelStoreOwner.current.defaultCreationExtras()).apply { set(CompareViewModel.BREW_IDS, shown.ids) }
                    CompareScreen(viewModel(key = "compare-${shown.ids.joinToString(",")}", factory = viewModelFactory, extras = extras)) { comparing = null }
                }
                is Destination.Trends -> {
                    val extras = MutableCreationExtras(LocalViewModelStoreOwner.current.defaultCreationExtras()).apply { set(TrendsViewModel.KEY, shown.key) }
                    TrendsScreen(viewModel(key = "trends-${shown.key.encode()}", factory = viewModelFactory, extras = extras)) { trends = null }
                }
                is Destination.Brew -> {
                    val extras = MutableCreationExtras(LocalViewModelStoreOwner.current.defaultCreationExtras()).apply { set(BrewViewModel.MODE, shown.mode) }
                    // One ViewModel per mode, begun when the screen opens and ended when it closes, so a closed brew never keeps
                    // listening to the scale; a rotation keeps the brew going.
                    val brew: BrewViewModel = viewModel(key = "brew-${shown.mode.name}", factory = viewModelFactory, extras = extras)
                    val close = remember(brew) {
                        {
                            brew.end()
                            brewing = null
                            openBrew.value = null
                        }
                    }
                    LaunchedEffect(brew) { brew.begin() }
                    // Registers this brew screen for the shortcut above. Its disposal drops the entry only while it is
                    // still the current one: a newer brew registers during the transition, before the outgoing screen
                    // goes away.
                    DisposableEffect(brew) {
                        val open = OpenBrew(shown.mode, running = { brew.uiState.value.phase == BrewPhase.BREWING || brew.uiState.value.phase == BrewPhase.PAUSED }, close = close)
                        openBrew.value = open
                        onDispose { if (openBrew.value === open) openBrew.value = null }
                    }
                    BrewScreen(brew, onClose = close)
                }
                is Destination.Coffee -> {
                    val extras = MutableCreationExtras(LocalViewModelStoreOwner.current.defaultCreationExtras()).apply { set(CoffeeBagDetailViewModel.BAG_ID, shown.id) }
                    CoffeeBagDetailScreen(
                        viewModel(key = "coffee-${shown.id}", factory = viewModelFactory, extras = extras),
                        viewModel(factory = viewModelFactory),
                        onOpenBrew = { opened = it },
                        onClose = { coffee = null },
                    )
                }
                is Destination.Recipe -> {
                    val target = shown.key.split(':', limit = 2).let { (kind, value) ->
                        if (kind == "edit") RecipeEditorViewModel.Target.Edit(value) else RecipeEditorViewModel.Target.New(BrewMode.valueOf(value))
                    }
                    val extras = MutableCreationExtras(LocalViewModelStoreOwner.current.defaultCreationExtras()).apply { set(RecipeEditorViewModel.TARGET, target) }
                    RecipeEditorScreen(viewModel(key = "recipe-${shown.key}", factory = viewModelFactory, extras = extras)) { editing = null }
                }
                Destination.Tabs -> Tabs(
                    viewModelFactory, actions, versionName, tab,
                    onTab = { tab = it },
                    onBrew = { brewing = it },
                    onOpen = { opened = it },
                    onRecipe = { editing = it },
                    onCoffee = { coffee = it },
                    onCompare = { comparing = it.joinToString(",") },
                )
            }
        }
    }
}

/**
 * The brew screen on top of the tabs (#116): its [mode], whether it is [running], and how to [close] it. A launcher
 * shortcut consults it before ending a brew or reopening a mode that is already on screen.
 */
private class OpenBrew(val mode: BrewMode, val running: () -> Boolean, val close: () -> Unit)

/** What fills the screen under [Main]. */
internal sealed interface Destination {
    data object Tabs : Destination
    data class Brew(val mode: BrewMode) : Destination
    data class Detail(val id: String) : Destination
    data class Compare(val ids: List<String>) : Destination
    data class Trends(val key: TrendKey) : Destination
    data class Recipe(val key: String) : Destination
    data class Coffee(val id: String) : Destination
}

/**
 * The key of this destination's [DestinationStores] entry, one per open screen: the same brew, bag or recipe reopened
 * later gets a fresh entry, while a destination still on the back path keeps the one it has (#310).
 */
internal val Destination.storeKey: String
    get() = when (this) {
        Destination.Tabs -> "tabs"
        is Destination.Brew -> "brew-${mode.name}"
        is Destination.Detail -> "detail-$id"
        is Destination.Compare -> "compare-${ids.joinToString(",")}"
        is Destination.Trends -> "trends-${key.encode()}"
        is Destination.Recipe -> "recipe-$key"
        is Destination.Coffee -> "coffee-$id"
    }

/**
 * How far a destination is from the tabs: Compare and Trends open over a saved brew, so going from them back to the brew
 * is a step back (R2), not a step forward.
 */
internal val Destination.depth: Int
    get() = when (this) {
        Destination.Tabs -> 0
        is Destination.Compare, is Destination.Trends -> 2
        else -> 1
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Tabs(
    viewModelFactory: ViewModelProvider.Factory,
    actions: AppActions,
    versionName: String,
    tab: Tab,
    onTab: (Tab) -> Unit,
    onBrew: (BrewMode) -> Unit,
    onOpen: (String) -> Unit,
    onRecipe: (String) -> Unit,
    onCoffee: (String) -> Unit,
    onCompare: (List<String>) -> Unit,
) {
    // The Home one, so the bar and the screen share a connection state.
    val scale: HomeViewModel = viewModel(factory = viewModelFactory)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLargeEmphasized) },
                actions = { ScaleStatusIcon(scale.uiState.collectAsStateWithLifecycle().value.scale, Modifier.padding(end = 12.dp)) },
            )
        },
        bottomBar = {
            ShortNavigationBar {
                Tab.entries.forEach { entry ->
                    ShortNavigationBarItem(
                        selected = tab == entry,
                        onClick = { onTab(entry) },
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
                    onOpen = { onRecipe("edit:${it.id}") },
                    onNew = { onRecipe("new:${it.name}") },
                    modifier,
                )
                Tab.COFFEES -> CoffeesScreen(viewModel(factory = viewModelFactory), onOpen = onCoffee, modifier = modifier)
                Tab.BREWS -> BrewsScreen(viewModel(factory = viewModelFactory), actions, onOpen = { onOpen(it.id) }, onOpenSettings = { onTab(Tab.SETTINGS) }, onCompare = onCompare, modifier = modifier)
                Tab.SETTINGS -> SettingsScreen(
                    viewModel(key = "server-settings", factory = viewModelFactory),
                    viewModel(factory = viewModelFactory),
                    viewModel(factory = viewModelFactory),
                    viewModel(factory = viewModelFactory),
                    viewModel(factory = viewModelFactory),
                    actions,
                    versionName,
                    modifier,
                )
            }
        }
    }
}

private fun androidx.lifecycle.ViewModelStoreOwner?.defaultCreationExtras(): CreationExtras =
    (this as? HasDefaultViewModelProviderFactory)?.defaultViewModelCreationExtras ?: CreationExtras.Empty
