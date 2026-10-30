package io.github.mateuxlucax.caparao.ui.recipes

import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.BrewMode
import io.github.mateuxlucax.caparao.model.StepKind
import io.github.mateuxlucax.caparao.ui.components.BrewModeGroup
import io.github.mateuxlucax.caparao.ui.components.label
import io.github.mateuxlucax.caparao.ui.format.Format
import io.github.mateuxlucax.caparao.ui.recipes.RecipeForm.Field

/** Creates or edits a recipe (wireframe 2); closes after saving or deleting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditorScreen(viewModel: RecipeEditorViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onClose() }
    BackHandler(onBack = onClose)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
            title = { Text(stringResource(R.string.delete_recipe_title)) },
            text = { Text(stringResource(R.string.delete_recipe_text)) },
            confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmDelete = false; viewModel.delete() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.new_recipe else R.string.edit_recipe), style = MaterialTheme.typography.titleLargeEmphasized) },
                navigationIcon = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onClose) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.close)) }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = { confirmDelete = true }) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val form = state.form ?: return@Scaffold
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Form(form, state.errors, state.isNew, viewModel)
            Button(shapes = ButtonDefaults.shapes(), onClick = viewModel::save, modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight)) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
private fun Form(form: RecipeForm, errors: Set<Field>, isNew: Boolean, viewModel: RecipeEditorViewModel) {
    val locale = LocalConfiguration.current.locales[0]
    // The mode decides the fields, so it is chosen once, when the recipe is created.
    if (isNew) {
        BrewModeGroup(form.mode, { mode ->
            viewModel.edit { RecipeForm.new(mode).copy(name = it.name, dose = it.dose, grind = it.grind, temp = it.temp, notes = it.notes) }
        })
    }
    TextField(form.name, { v -> viewModel.edit { it.copy(name = v) } }, R.string.recipe_name, Field.NAME in errors, R.string.required, text = true)
    TextField(form.method, { v -> viewModel.edit { it.copy(method = v) } }, R.string.recipe_method, Field.METHOD in errors, R.string.required, text = true)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RecipeForm.METHODS.getValue(form.mode).forEach { method ->
            FilledTonalButton(
                    onClick = { viewModel.edit { it.copy(method = method) } },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.heightIn(min = ButtonDefaults.ExtraSmallContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.ExtraSmallContainerHeight),
                ) { Text(method) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextField(form.dose, { v -> viewModel.edit { it.copy(dose = v) } }, R.string.recipe_dose, Field.DOSE in errors, R.string.recipe_error_dose, Modifier.weight(1f), suffix = stringResource(R.string.unit_grams))
        TextField(form.ratio, { v -> viewModel.edit { it.copy(ratio = v) } }, R.string.recipe_ratio, Field.RATIO in errors, R.string.recipe_error_ratio, Modifier.weight(1f), prefix = "1:")
    }
    form.targetGrams?.let {
        Text(
            stringResource(if (form.mode == BrewMode.FILTER) R.string.recipe_target_water else R.string.recipe_target_yield, Format.grams(it, locale)),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextField(form.grind, { v -> viewModel.edit { it.copy(grind = v) } }, R.string.recipe_grind, false, R.string.required, Modifier.weight(1f), text = true)
        TextField(form.temp, { v -> viewModel.edit { it.copy(temp = v) } }, R.string.recipe_temp, Field.TEMP in errors, R.string.recipe_error_temp, Modifier.weight(1f), suffix = stringResource(R.string.unit_celsius))
    }
    when (form.mode) {
        BrewMode.ESPRESSO -> {
            Text(stringResource(R.string.recipe_window), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val s = stringResource(R.string.unit_seconds)
                TextField(form.minSeconds, { v -> viewModel.edit { it.copy(minSeconds = v) } }, R.string.recipe_window_min, Field.WINDOW in errors, null, Modifier.weight(1f), suffix = s)
                TextField(form.maxSeconds, { v -> viewModel.edit { it.copy(maxSeconds = v) } }, R.string.recipe_window_max, Field.WINDOW in errors, null, Modifier.weight(1f), suffix = s)
            }
            if (Field.WINDOW in errors) ErrorText(R.string.recipe_error_window)
        }
        BrewMode.FILTER -> Steps(form.steps, Field.STEPS in errors, viewModel)
    }
    TextField(form.notes, { v -> viewModel.edit { it.copy(notes = v) } }, R.string.recipe_notes, false, R.string.required, text = true, singleLine = false)
}

@Composable
private fun Steps(steps: List<StepForm>, error: Boolean, viewModel: RecipeEditorViewModel) {
    Text(stringResource(R.string.recipe_steps), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.recipe_steps_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    steps.forEachIndexed { index, step ->
        StepRow(step, error, { viewModel.updateStep(index, it) }, { viewModel.removeStep(index) })
    }
    if (error) ErrorText(if (steps.isEmpty()) R.string.recipe_error_no_steps else R.string.recipe_error_steps)
    OutlinedButton(shapes = ButtonDefaults.shapes(), onClick = viewModel::addStep) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null)
        Text(stringResource(R.string.add_step), Modifier.padding(start = 8.dp))
    }
}

// One connected group per step, like a button group: kind, weight, start and remove share a height, 2dp gaps, small
// inner corners and full outer ones.
@Composable
private fun StepRow(step: StepForm, error: Boolean, onChange: (StepForm) -> Unit, onRemove: () -> Unit) {
    val inner = CornerSize(8.dp)
    val outer = CornerSize(50)
    val start = RoundedCornerShape(outer, inner, inner, outer)
    val middle = RoundedCornerShape(inner)
    val end = RoundedCornerShape(inner, outer, outer, inner)
    Row(Modifier.fillMaxWidth().height(StepHeight), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        KindPicker(step.kind, start, Modifier.width(104.dp).fillMaxHeight()) { onChange(step.copy(kind = it)) }
        if (step.kind.pours) {
            StepField(step.weight, { onChange(step.copy(weight = it)) }, R.string.step_weight, error, middle, Modifier.weight(1f), suffix = stringResource(R.string.unit_grams))
        } else {
            Box(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer, middle))
        }
        StepField(step.start, { onChange(step.copy(start = it)) }, R.string.step_start, error, middle, Modifier.weight(1f), clock = true)
        FilledTonalIconButton(onClick = onRemove, shape = end, modifier = Modifier.width(48.dp).fillMaxHeight()) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.remove_step))
        }
    }
}

private val StepHeight = 56.dp

@Composable
private fun KindPicker(kind: StepKind, shape: Shape, modifier: Modifier, onPick: (StepKind) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        FilledTonalButton(onClick = { open = true }, shape = shape, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.fillMaxSize()) {
            Text(stringResource(kind.label), maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
            StepKind.entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(stringResource(entry.label)) },
                    onClick = { open = false; onPick(entry) },
                    leadingIcon = if (entry == kind) {
                        { Icon(painterResource(R.drawable.ic_check), contentDescription = null) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

// A filled field without the indicator line, its label small inside, so it reads as one segment of the step group.
@Composable
private fun StepField(
    value: String,
    onValue: (String) -> Unit,
    label: Int,
    isError: Boolean,
    shape: Shape,
    modifier: Modifier,
    suffix: String? = null,
    clock: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.bodyLarge
    BasicTextField(
        value = value,
        onValueChange = { v -> onValue(if (clock) v.filter { it.isDigit() || it == ':' }.take(5) else v.filter { it.isDigit() || it == ',' || it == '.' }.take(6)) },
        modifier = modifier.fillMaxHeight(),
        singleLine = true,
        textStyle = textStyle.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = if (clock) KeyboardType.Number else KeyboardType.Decimal),
    ) { inner ->
        Column(
            Modifier.fillMaxSize().background(if (isError) colors.errorContainer else colors.surfaceContainerHigh, shape).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = if (isError) colors.onErrorContainer else colors.onSurfaceVariant, maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) { inner() }
                suffix?.let { Text(it, style = textStyle, color = colors.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun TextField(
    value: String,
    onValue: (String) -> Unit,
    label: Int,
    isError: Boolean,
    errorText: Int?,
    modifier: Modifier = Modifier.fillMaxWidth(),
    prefix: String? = null,
    suffix: String? = null,
    text: Boolean = false,
    clock: Boolean = false,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v ->
            onValue(
                when {
                    text -> v.take(80)
                    clock -> v.filter { it.isDigit() || it == ':' }.take(5)
                    else -> v.filter { it.isDigit() || it == ',' || it == '.' }.take(6)
                },
            )
        },
        modifier = modifier,
        label = { Text(stringResource(label), maxLines = 1) },
        prefix = prefix?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        isError = isError,
        supportingText = if (isError && errorText != null) ({ Text(stringResource(errorText)) }) else null,
        singleLine = singleLine,
        keyboardOptions = when {
            text -> KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            clock -> KeyboardOptions(keyboardType = KeyboardType.Number)
            else -> KeyboardOptions(keyboardType = KeyboardType.Decimal)
        },
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
private fun ErrorText(id: Int) = Text(stringResource(id), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

val StepKind.label: Int
    get() = when (this) {
        StepKind.BLOOM -> R.string.step_bloom
        StepKind.POUR -> R.string.step_pour
        StepKind.WAIT -> R.string.step_wait
        StepKind.SWIRL -> R.string.step_swirl
    }
