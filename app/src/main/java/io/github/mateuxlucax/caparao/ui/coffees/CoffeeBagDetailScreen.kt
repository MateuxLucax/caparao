package io.github.mateuxlucax.caparao.ui.coffees
import io.github.mateuxlucax.caparao.ui.components.wholeGrams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.CoffeeBag
import io.github.mateuxlucax.caparao.ui.components.icon
import io.github.mateuxlucax.caparao.ui.components.label
import io.github.mateuxlucax.caparao.ui.format.Format
import java.util.Locale

/**
 * One coffee bag (#290, C4): what is left, the brews that used it, finishing it, and editing or deleting it. Editing
 * opens the same sheet as Add; deleting asks first (C5), and the brews keep the coffee's name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoffeeBagDetailScreen(
    viewModel: CoffeeBagDetailViewModel,
    bagsViewModel: CoffeeBagsViewModel,
    onOpenBrew: (String) -> Unit,
    onClose: () -> Unit,
) {
    val bag by viewModel.bag.collectAsStateWithLifecycle()
    val brews by viewModel.brews.collectAsStateWithLifecycle()
    val editing by bagsViewModel.editing.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    Scaffold(
        topBar = {
            TopAppBar(
                title = { bag?.let { Text(listOfNotNull(it.name, it.roaster).joinToString(" · "), style = MaterialTheme.typography.titleLargeEmphasized) } },
                navigationIcon = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onClose) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.close)) }
                },
            )
        },
    ) { padding ->
        val current = bag ?: return@Scaffold
        Column(
            Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Roast(current)
            if (current.finished) {
                Text(stringResource(R.string.coffee_finished), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    stringResource(R.string.coffee_left, wholeGrams(current.leftGrams, locale), wholeGrams(current.weightGrams, locale)),
                    style = MaterialTheme.typography.bodyLarge,
                )
                LinearProgressIndicator(progress = { (current.leftGrams / current.weightGrams).toFloat() }, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.setFinished(!current.finished) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(if (current.finished) R.string.coffee_mark_open else R.string.coffee_mark_finished))
                }
                OutlinedButton(onClick = { bagsViewModel.edit(current) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.edit)) }
            }
            Text(stringResource(R.string.tab_brews), style = MaterialTheme.typography.titleMedium)
            if (brews.isEmpty()) {
                Text(stringResource(R.string.coffee_no_brews), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            brews.forEach { brew -> BrewLine(brew, locale) { onOpenBrew(brew.id) } }
            TextButton(onClick = { confirmDelete = true }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    editing?.let { CoffeeBagSheet(it, bagsViewModel) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
            title = { Text(stringResource(R.string.delete_coffee_title)) },
            text = { Text(stringResource(R.string.delete_coffee_text)) },
            confirmButton = {
                TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmDelete = false; viewModel.delete(); onClose() }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Where and when it was roasted, and how long ago. */
@Composable
private fun Roast(bag: CoffeeBag) {
    val now = remember { System.currentTimeMillis() }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val details = buildList {
        bag.origin?.let { add(it) }
        bag.roastedAt?.let { add(stringResource(R.string.coffee_roasted_on, roastDay(it))) }
        bag.daysSinceRoast(now)?.let { add(pluralStringResource(R.plurals.coffee_roasted_days, it.toInt(), it.toInt())) }
    }
    if (details.isNotEmpty()) {
        Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = dim)
    }
}

/** A brew that used the bag: its mode, when, the dose and yield, and its rating. Tapping it opens the brew. */
@Composable
private fun BrewLine(brew: Brew, locale: Locale, onClick: () -> Unit) {
    val g = stringResource(R.string.unit_grams)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(brew.mode.icon), contentDescription = null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(brew.mode.label) + " · " + Format.dateTime(brew.startedAt, locale),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${Format.grams(brew.doseGrams, locale)} $g → ${Format.grams(brew.yieldGrams, locale)} $g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            brew.rating?.let { Text(stringResource(R.string.rating_stars, it), style = MaterialTheme.typography.bodySmall) }
        }
    }
}
