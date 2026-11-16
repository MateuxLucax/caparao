package dev.mateux.caparao.ui.coffees

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import dev.mateux.caparao.R
import dev.mateux.caparao.ui.components.wholeGrams
import dev.mateux.caparao.ui.components.SystemBack
import dev.mateux.caparao.model.CoffeeBag
import dev.mateux.caparao.model.BagLabelReader
import dev.mateux.caparao.model.LabelLine
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import dev.mateux.caparao.ui.components.EmptyState
import dev.mateux.caparao.ui.format.Format
import dev.mateux.caparao.ui.theme.RowTitle
import dev.mateux.caparao.ui.theme.SectionHeading
import dev.mateux.caparao.ui.theme.RowDetail
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
fun CoffeesScreen(viewModel: CoffeeBagsViewModel, onOpen: (String) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val bags by viewModel.bags.collectAsStateWithLifecycle()
    var showFinished by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        bags?.let { all ->
            val now = viewModel.now()
            val open = all.filter { !it.finished }
            val finished = all.filter { it.finished }
            LazyColumn(
                Modifier.fillMaxSize().testTag("coffees-list"),
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
        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.coffee_add)) }
    }
}

@Composable
private fun BagRow(bag: CoffeeBag, now: Long, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("coffee-row-${bag.id}"), shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                listOfNotNull(bag.name, bag.roaster).joinToString(" · "),
                style = MaterialTheme.typography.titleMedium.merge(RowTitle),
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
                // No "0 brews": pt-BR counts 0 as one, and a new bag says nothing by having none.
                if (bag.brewCount > 0) add(pluralStringResource(R.plurals.coffee_brew_count, bag.brewCount, bag.brewCount))
                bag.lastBrewedAt?.let { add(stringResource(R.string.coffee_last_brew, Format.dateTime(it, locale))) }
                bag.averageRating?.let { add(stringResource(R.string.coffee_average_rating, Format.ratio(it, locale))) }
            }
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodyMedium.merge(RowDetail), color = dim)
        }
    }
}

/**
 * Adds or edits a bag. Closing with other edits asks first, like the recipe editor (#181).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CoffeeEditorScreen(viewModel: CoffeeEditorViewModel, bagsViewModel: CoffeeBagsViewModel, onClose: () -> Unit) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val bags by bagsViewModel.bags.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var tastingNote by rememberSaveable(form.id) { mutableStateOf("") }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val labelDraft by viewModel.labelDraft.collectAsStateWithLifecycle()
    var assigning by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var photoPath by rememberSaveable { mutableStateOf<String?>(null) }
    val photo = remember(photoPath) { photoPath?.let(::File) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val captured = photo
        if (success && captured != null) scope.launch {
            try {
                val defaults = listOf(R.string.tasting_caramel, R.string.tasting_chocolate, R.string.tasting_citrus,
                    R.string.tasting_red_fruit, R.string.tasting_floral, R.string.tasting_nuts).map(context::getString)
                viewModel.readLabel(captured, bags.orEmpty().mapNotNull(CoffeeBag::roaster), defaults)
            } finally {
                captured.delete()
                photoPath = null
            }
        } else { captured?.delete(); photoPath = null }
    }
    fun takeLabelPhoto() {
        val file = File(context.cacheDir, "label-photos/${System.nanoTime()}.jpg").apply { parentFile?.mkdirs() }
        photoPath = file.absolutePath
        camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.label-photo", file))
    }
    val close = { if (viewModel.dirty) confirmDiscard = true else onClose() }
    SystemBack(close)
    Box(Modifier.fillMaxSize().testTag("coffee-bag-editor")) {
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmDiscard = false; onClose() }) { Text(stringResource(R.string.discard)) } },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    assigning?.let { candidate -> AlertDialog(
        onDismissRequest = { assigning = null },
        title = { Text(candidate) },
        text = { Column { listOf(R.string.coffee_name, R.string.coffee_roaster, R.string.coffee_origin, R.string.coffee_tasting_notes).forEachIndexed { index, label ->
            TextButton(onClick = { viewModel.assignLabelCandidate(candidate, LabelDestination.entries[index]); assigning = null }) { Text(stringResource(label)) }
        } } },
        confirmButton = { TextButton(onClick = { assigning = null }) { Text(stringResource(R.string.cancel)) } },
    ) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (form.sourceBagId != null) R.string.coffee_new_bag else if (form.id == null) R.string.coffee_add else R.string.coffee_edit), style = MaterialTheme.typography.titleLargeEmphasized) },
                navigationIcon = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = close) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.close))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { val saved = form.withNote(tastingNote); viewModel.change(saved); bagsViewModel.save(saved); onClose() },
                        enabled = form.valid,
                    ) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { takeLabelPhoto() }, modifier = Modifier.fillMaxWidth().testTag("coffee-label-camera"), shapes = ButtonDefaults.shapes()) {
                Text(stringResource(if (labelDraft.hasRead) R.string.coffee_read_another_side else R.string.coffee_read_label))
            }
            labelDraft.message?.let { Text(stringResource(if (it == LabelMessage.NOTHING_NEW) R.string.coffee_label_nothing_new else R.string.coffee_label_could_not_read)) }
            if (labelDraft.candidates.isNotEmpty()) {
                Text(stringResource(R.string.coffee_label_review), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { labelDraft.candidates.forEach { candidate ->
                    FilterChip(selected = false, onClick = { assigning = candidate }, label = { Text(candidate) }, modifier = Modifier.testTag("coffee-label-$candidate"))
                } }
            }
            Text(stringResource(R.string.coffee_section), style = MaterialTheme.typography.titleMedium.merge(SectionHeading))
            Field(form.name, R.string.coffee_name) { viewModel.change(form.copy(name = it.take(80))) }
            Field(
                form.roaster, R.string.coffee_roaster,
                suggestions = coffeeFieldSuggestions(bags.orEmpty(), form.roaster, form.id, CoffeeBag::roaster),
                onSuggestion = { viewModel.change(form.copy(roaster = it)) },
            ) { viewModel.change(form.copy(roaster = it.take(80))) }
            Field(form.origin, R.string.coffee_origin,
                suggestions = coffeeFieldSuggestions(bags.orEmpty(), form.origin, form.id, CoffeeBag::origin),
                onSuggestion = { viewModel.change(form.copy(origin = it)) },
            ) { viewModel.change(form.copy(origin = it.take(120))) }
            Text(stringResource(R.string.coffee_package_section), style = MaterialTheme.typography.titleMedium.merge(SectionHeading))
            OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
                Text(form.roastedAt?.let { stringResource(R.string.coffee_roasted_on, roastDay(it)) } ?: stringResource(R.string.coffee_roast_date))
            }
            Field(form.weight, R.string.coffee_weight, KeyboardType.Decimal) { viewModel.change(form.copy(weight = it.take(6))) }
            if (form.sourceBagId != null) {
                if (form.copiedAdjustmentCount > 0) Text(pluralStringResource(R.plurals.coffee_adjustments_copied, form.copiedAdjustmentCount, form.copiedAdjustmentCount))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Switch(checked = form.finishSource, onCheckedChange = { viewModel.change(form.copy(finishSource = it)) })
                    Text(stringResource(R.string.coffee_finish_old_bag))
                }
            }
            Text(stringResource(R.string.coffee_tasting_notes_section), style = MaterialTheme.typography.titleMedium.merge(SectionHeading))
            run {
                OutlinedTextField(
                    value = tastingNote,
                    onValueChange = { tastingNote = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.coffee_tasting_notes)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val added = form.withNote(tastingNote)
                                if (added !== form) {
                                    viewModel.change(added)
                                    tastingNote = ""
                                }
                            },
                            enabled = tastingNote.isNotBlank() && form.tastingNotes.size < 6,
                        ) { Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.coffee_add_tasting_note)) }
                    },
                )
                if (form.tastingNotes.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        form.tastingNotes.forEach { note ->
                            InputChip(
                                selected = true,
                                onClick = { viewModel.change(form.copy(tastingNotes = form.tastingNotes.filterNot { it == note })) },
                                label = { Text(note) },
                                trailingIcon = {
                                    Icon(
                                        painterResource(R.drawable.ic_close),
                                        contentDescription = stringResource(R.string.coffee_remove_tasting_note, note),
                                    )
                                },
                            )
                        }
                    }
                }
                val defaults = listOf(
                    R.string.tasting_caramel, R.string.tasting_chocolate, R.string.tasting_citrus,
                    R.string.tasting_red_fruit, R.string.tasting_floral, R.string.tasting_nuts,
                ).map { stringResource(it) }
                val suggestions = coffeeNoteSuggestions(bags.orEmpty(), defaults, form.tastingNotes, form.id)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    suggestions.forEach { value ->
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.change(form.withNote(value)) },
                            label = { Text(value) },
                        )
                    }
                }
            }
            Text(stringResource(R.string.coffee_notes_section), style = MaterialTheme.typography.titleMedium.merge(SectionHeading))
            Field(form.notes, R.string.brew_notes) { viewModel.change(form.copy(notes = it.take(500))) }
        }
    }
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = form.roastedAt ?: bagsViewModel.now())
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
}

@Composable
private fun Field(
    value: String,
    label: Int,
    keyboard: KeyboardType = KeyboardType.Text,
    suggestions: List<String> = emptyList(),
    onSuggestion: (String) -> Unit = {},
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
    )
    if (suggestions.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        suggestions.forEach { suggestion ->
            FilterChip(selected = false, onClick = { onSuggestion(suggestion) }, label = { Text(suggestion) })
        }
    }
}

/** Bag grams as the list shows them: whole grams, e.g. "142 of 250 g left". */

/** The roast day as the date picker gives it (UTC midnight). */
internal fun roastDay(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))
