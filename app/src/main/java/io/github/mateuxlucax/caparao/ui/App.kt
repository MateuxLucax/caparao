package io.github.mateuxlucax.caparao.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.data.scale.ScaleSource
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
import io.github.mateuxlucax.caparao.ui.components.ScaleStatusIcon
import io.github.mateuxlucax.caparao.ui.home.HomeScreen
import io.github.mateuxlucax.caparao.ui.home.HomeViewModel
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
    BREWS(R.string.tab_brews, R.drawable.ic_receipt_long),
    SETTINGS(R.string.tab_settings, R.drawable.ic_settings),
}

/**
 * Onboarding on first launch, then the four tabs under a top bar with the name and the scale connection.
 * The scale is connected only while the app is visible (docs/adr/0008).
 */
@Composable
fun App(
    viewModelFactory: ViewModelProvider.Factory,
    scale: ScaleSource,
    onboardingDone: Boolean,
    actions: AppActions,
    versionName: String,
) {
    LifecycleStartEffect(scale) {
        scale.start()
        onStopOrDispose { scale.stop() }
    }
    // Back from turning Bluetooth on or granting the permission (dialogs that only pause the app): try again.
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
        Main(viewModelFactory, actions, versionName)
    }
}

/** The tabs, or a brew on top of them while one is open. */
@Composable
private fun Main(viewModelFactory: ViewModelProvider.Factory, actions: AppActions, versionName: String) {
    var brewing by rememberSaveable { mutableStateOf<BrewMode?>(null) }
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    // "edit:<id>" or "new:<mode>", saveable as a string.
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    // Comma-joined ids and TrendKey.encode(), so both survive a rotation; they open over a saved brew or the tabs.
    var comparing by rememberSaveable { mutableStateOf<String?>(null) }
    var trends by rememberSaveable { mutableStateOf<String?>(null) }
    // Here and not in Tabs, which leaves the composition while a brew is open: closing it goes back to the same tab.
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val destination = editing?.let { Destination.Recipe(it) }
        ?: trends?.let { Destination.Trends(TrendKey.decode(it)) }
        ?: comparing?.let { Destination.Compare(it.split(',')) }
        ?: opened?.let { Destination.Detail(it) } ?: brewing?.let { Destination.Brew(it) } ?: Destination.Tabs
    val density = LocalDensity.current
    // A brew or a saved brew comes in from the right over the tabs and goes back out the same way.
    AnimatedContent(destination, transitionSpec = { Motion.sharedAxisX(targetState != Destination.Tabs, density) }, label = "screen") { shown ->
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
                LaunchedEffect(brew) { brew.begin() }
                BrewScreen(brew) {
                    brew.end()
                    brewing = null
                }
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
                onCompare = { comparing = it.joinToString(",") },
            )
        }
    }
}

/** What fills the screen under [Main]. */
private sealed interface Destination {
    data object Tabs : Destination
    data class Brew(val mode: BrewMode) : Destination
    data class Detail(val id: String) : Destination
    data class Compare(val ids: List<String>) : Destination
    data class Trends(val key: TrendKey) : Destination
    data class Recipe(val key: String) : Destination
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
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
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
                Tab.BREWS -> BrewsScreen(viewModel(factory = viewModelFactory), actions, onOpen = { onOpen(it.id) }, onOpenSettings = { onTab(Tab.SETTINGS) }, onCompare = onCompare, modifier = modifier)
                Tab.SETTINGS -> SettingsScreen(
                    viewModel(key = "server-settings", factory = viewModelFactory),
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
