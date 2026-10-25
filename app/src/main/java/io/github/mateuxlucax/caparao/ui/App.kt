package io.github.mateuxlucax.caparao.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
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
import io.github.mateuxlucax.caparao.ui.components.Logo
import io.github.mateuxlucax.caparao.ui.home.HomeScreen
import io.github.mateuxlucax.caparao.ui.onboarding.OnboardingScreen
import io.github.mateuxlucax.caparao.ui.settings.SettingsScreen

/** The bottom-bar tabs, in order. */
enum class Tab(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    HOME(R.string.tab_home, R.drawable.ic_home),
    BREWS(R.string.tab_brews, R.drawable.ic_receipt_long),
    SETTINGS(R.string.tab_settings, R.drawable.ic_settings),
}

/**
 * Onboarding on first launch, then the three tabs under a top bar with the logo on the left and the name on the right.
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
    val mode = brewing
    val openedId = opened
    if (openedId != null) {
        val extras = MutableCreationExtras(LocalViewModelStoreOwner.current.defaultCreationExtras()).apply { set(BrewDetailViewModel.BREW_ID, openedId) }
        BrewDetailScreen(viewModel(key = "detail-$openedId", factory = viewModelFactory, extras = extras)) { opened = null }
    } else if (mode != null) {
        val extras = MutableCreationExtras(LocalViewModelStoreOwner.current.defaultCreationExtras()).apply { set(BrewViewModel.MODE, mode) }
        // One ViewModel per mode, begun when the screen opens and ended when it closes, so a closed brew never keeps
        // listening to the scale; a rotation keeps the brew going.
        val brew: BrewViewModel = viewModel(key = "brew-${mode.name}", factory = viewModelFactory, extras = extras)
        LaunchedEffect(brew) { brew.begin() }
        BrewScreen(brew) {
            brew.end()
            brewing = null
        }
    } else {
        Tabs(viewModelFactory, actions, versionName, onBrew = { brewing = it }, onOpen = { opened = it })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Tabs(
    viewModelFactory: ViewModelProvider.Factory,
    actions: AppActions,
    versionName: String,
    onBrew: (BrewMode) -> Unit,
    onOpen: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { Logo(36.dp, Modifier.padding(start = 12.dp)) },
                actions = {
                    Text(
                        stringResource(R.string.app_name),
                        Modifier.padding(end = 16.dp),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
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
        when (tab) {
            Tab.HOME -> HomeScreen(viewModel(factory = viewModelFactory), actions, onBrew, modifier)
            Tab.BREWS -> BrewsScreen(viewModel(factory = viewModelFactory), actions, onOpen = { onOpen(it.id) }, onOpenSettings = { tab = Tab.SETTINGS }, modifier)
            Tab.SETTINGS -> SettingsScreen(viewModel(key = "server-settings", factory = viewModelFactory), actions, versionName, modifier)
        }
    }
}

private fun androidx.lifecycle.ViewModelStoreOwner?.defaultCreationExtras(): CreationExtras =
    (this as? HasDefaultViewModelProviderFactory)?.defaultViewModelCreationExtras ?: CreationExtras.Empty
