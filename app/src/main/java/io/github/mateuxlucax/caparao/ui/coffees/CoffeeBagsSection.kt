package io.github.mateuxlucax.caparao.ui.coffees

import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.CoffeeBag
import io.github.mateuxlucax.caparao.ui.components.BodyText
import io.github.mateuxlucax.caparao.ui.format.Format
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

/** The Settings card: how many bags are open, and the way to the coffees screen. */
@Composable
fun CoffeeBagsSection(viewModel: CoffeeBagsViewModel, onOpen: () -> Unit) {
    val bags by viewModel.bags.collectAsStateWithLifecycle()
    val open = bags?.count { !it.finished } ?: return
    BodyText(if (open == 0) stringResource(R.string.coffees_empty) else pluralStringResource(R.plurals.coffees_open, open, open))
    FilledTonalButton(onClick = onOpen, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.coffees_manage)) }
}

/** The coffees screen: open bags with what is left and the days since the roast, then the finished ones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoffeesScreen(viewModel: CoffeeBagsViewModel, onClose: () -> Unit) {
    val bags by viewModel.bags.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.section_coffees)) },
                navigationIcon = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onClose) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.close)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = viewModel::new) { Text(stringResource(R.string.coffee_add)) }
        },
    ) { padding ->
        val all = bags ?: return@Scaffold
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (all.isEmpty()) BodyText(stringResource(R.string.coffees_empty))
            all.forEach { bag -> BagRow(bag, viewModel.now()) { viewModel.edit(bag) } }
        }
    }
    editing?.let { BagDialog(it, viewModel) }
}

@Composable
private fun BagRow(bag: CoffeeBag, now: Long, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            listOfNotNull(bag.name, bag.roaster).joinToString(" · "),
            style = MaterialTheme.typography.titleMedium,
            color = if (bag.finished) dim else MaterialTheme.colorScheme.onSurface,
        )
        val details = buildList {
            if (bag.finished) add(stringResource(R.string.coffee_finished)) else add(stringResource(R.string.coffee_left, Format.grams(bag.leftGrams, locale), Format.grams(bag.weightGrams, locale)))
            bag.daysSinceRoast(now)?.let { add(pluralStringResource(R.plurals.coffee_roasted_days, it.toInt(), it.toInt())) }
            bag.origin?.let { add(it) }
        }
        Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = dim)
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BagDialog(form: BagForm, viewModel: CoffeeBagsViewModel) {
    var picking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = viewModel::cancel,
        title = { Text(stringResource(if (form.id == null) R.string.coffee_add else R.string.coffee_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(form.name, R.string.coffee_name) { viewModel.change(form.copy(name = it.take(80))) }
                Field(form.roaster, R.string.coffee_roaster) { viewModel.change(form.copy(roaster = it.take(80))) }
                Field(form.origin, R.string.coffee_origin) { viewModel.change(form.copy(origin = it.take(120))) }
                Field(form.weight, R.string.coffee_weight, KeyboardType.Decimal) { viewModel.change(form.copy(weight = it.take(6))) }
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                    Text(form.roastedAt?.let { stringResource(R.string.coffee_roasted_on, day(it)) } ?: stringResource(R.string.coffee_roast_date))
                }
                Field(form.notes, R.string.brew_notes) { viewModel.change(form.copy(notes = it.take(500))) }
                if (form.id != null) {
                    Row(Modifier.fillMaxWidth().clickable { viewModel.change(form.copy(finished = !form.finished)) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(form.finished, null)
                        Text(stringResource(R.string.coffee_mark_finished), Modifier.padding(start = 8.dp))
                    }
                    TextButton(onClick = viewModel::delete, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::save, enabled = form.valid) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = viewModel::cancel) { Text(stringResource(R.string.cancel)) } },
    )
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

/** The date picker gives UTC midnight. */
private fun day(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))
