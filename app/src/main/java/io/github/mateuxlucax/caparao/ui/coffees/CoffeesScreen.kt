package io.github.mateuxlucax.caparao.ui.coffees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.CoffeeBag
import io.github.mateuxlucax.caparao.ui.components.EmptyState
import io.github.mateuxlucax.caparao.ui.format.Format
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The Coffees tab (#289, C1 and C2): open bags first, each with what is left, the roast's age and what its brews
 * say; the finished bags sit under a button, collapsed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoffeesScreen(viewModel: CoffeeBagsViewModel, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val bags by viewModel.bags.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    var showFinished by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        bags?.let { all ->
            val now = viewModel.now()
            val open = all.filter { !it.finished }
            val finished = all.filter { it.finished }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (all.isEmpty()) {
                    item(key = "empty") { EmptyState(R.drawable.ic_coffee, stringResource(R.string.coffees_empty), Modifier.padding(top = 48.dp)) }
                }
                items(open, key = { it.id }) { bag -> BagRow(bag, now) { onOpen(bag.id) } }
                if (finished.isNotEmpty()) {
                    item(key = "finished") {
                        TextButton(onClick = { showFinished = !showFinished }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.coffees_finished_count, finished.size))
                        }
                    }
                    if (showFinished) items(finished, key = { it.id }) { bag -> BagRow(bag, now) { onOpen(bag.id) } }
                }
            }
        }
        MediumExtendedFloatingActionButton(
            onClick = viewModel::new,
            icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
            text = { Text(stringResource(R.string.coffee_add)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }
    editing?.let { CoffeeBagSheet(it, viewModel) }
}

@Composable
private fun BagRow(bag: CoffeeBag, now: Long, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                listOfNotNull(bag.name, bag.roaster).joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
                color = if (bag.finished) dim else MaterialTheme.colorScheme.onSurface,
            )
            if (bag.finished) {
                Text(stringResource(R.string.coffee_finished), style = MaterialTheme.typography.bodyMedium, color = dim)
            } else {
                Text(
                    stringResource(R.string.coffee_left, wholeGrams(bag.leftGrams, locale), wholeGrams(bag.weightGrams, locale)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LinearProgressIndicator(progress = { (bag.leftGrams / bag.weightGrams).toFloat() }, modifier = Modifier.fillMaxWidth())
            }
            val details = buildList {
                bag.daysSinceRoast(now)?.let { add(pluralStringResource(R.plurals.coffee_roasted_days, it.toInt(), it.toInt())) }
                add(pluralStringResource(R.plurals.coffee_brew_count, bag.brewCount, bag.brewCount))
                bag.lastBrewedAt?.let { add(stringResource(R.string.coffee_last_brew, Format.dateTime(it, locale))) }
                bag.averageRating?.let { add(stringResource(R.string.coffee_average_rating, Format.ratio(it, locale))) }
            }
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = dim)
        }
    }
}

/**
 * Add or edit a bag (C3, and the edit from C4). Quick add shows the name, roaster, roast date and weight; origin and
 * notes sit under More details. Closing with other edits asks first, like the recipe editor (#181).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoffeeBagSheet(form: BagForm, viewModel: CoffeeBagsViewModel) {
    var picking by remember { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val dirty = rememberUpdatedState(viewModel.dirty(form))
    // Swiping down, the scrim and back all hide the sheet through here; with other edits it stays and asks.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = remember {
            { target: SheetValue ->
                if (target == SheetValue.Hidden && dirty.value) confirmDiscard = true
                target != SheetValue.Hidden || !dirty.value
            }
        },
    )
    val close = { if (viewModel.dirty(form)) confirmDiscard = true else viewModel.cancel() }
    ModalBottomSheet(onDismissRequest = viewModel::cancel, sheetState = sheetState) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(if (form.id == null) R.string.coffee_add else R.string.coffee_edit),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Field(form.name, R.string.coffee_name) { viewModel.change(form.copy(name = it.take(80))) }
            Field(form.roaster, R.string.coffee_roaster) { viewModel.change(form.copy(roaster = it.take(80))) }
            OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                Text(form.roastedAt?.let { stringResource(R.string.coffee_roasted_on, roastDay(it)) } ?: stringResource(R.string.coffee_roast_date))
            }
            Field(form.weight, R.string.coffee_weight, KeyboardType.Decimal) { viewModel.change(form.copy(weight = it.take(6))) }
            var moreDetails by rememberSaveable { mutableStateOf(form.origin.isNotEmpty() || form.notes.isNotEmpty()) }
            if (moreDetails) {
                Field(form.origin, R.string.coffee_origin) { viewModel.change(form.copy(origin = it.take(120))) }
                Field(form.notes, R.string.brew_notes) { viewModel.change(form.copy(notes = it.take(500))) }
            } else {
                TextButton(onClick = { moreDetails = true }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.coffee_more_details)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = close, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = viewModel::save, enabled = form.valid, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.save)) }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmDiscard = false; viewModel.cancel() }) { Text(stringResource(R.string.discard)) } },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = form.roastedAt ?: viewModel.now())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.change(form.copy(roastedAt = state.selectedDateMillis))
                    picking = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun Field(value: String, label: Int, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) =
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
    )

/** Bag grams as the list shows them: whole grams, e.g. "142 of 250 g left". */
internal fun wholeGrams(value: Double, locale: Locale): String = String.format(locale, "%.0f", value)

/** The roast day as the date picker gives it (UTC midnight). */
internal fun roastDay(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))
