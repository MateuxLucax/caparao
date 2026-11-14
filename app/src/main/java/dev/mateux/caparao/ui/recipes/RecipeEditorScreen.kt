package dev.mateux.caparao.ui.recipes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mateux.caparao.R
import dev.mateux.caparao.model.BrewMode
import dev.mateux.caparao.model.StepKind
import dev.mateux.caparao.ui.components.BrewModeGroup
import dev.mateux.caparao.ui.components.RecipeForm
import dev.mateux.caparao.ui.components.RecipeForm.Field
import dev.mateux.caparao.ui.components.StepForm
import dev.mateux.caparao.ui.components.SystemBack
import dev.mateux.caparao.ui.components.label
import dev.mateux.caparao.ui.format.Format


/** Creates or edits a recipe (wireframe 2); closes after saving or deleting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditorScreen(viewModel: RecipeEditorViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) { if (state.closed) onClose() }
    // An untouched editor closes at once; one with changes asks before losing them (#181).
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val close = { if (state.dirty) confirmDiscard = true else onClose() }
    SystemBack(close)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmDiscard = false; onClose() }) { Text(stringResource(R.string.discard)) } },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
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
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = close) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.close)) }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = { confirmDelete = true }) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.delete))
                        }
                    }
                    Button(shapes = ButtonDefaults.shapes(), onClick = viewModel::save, modifier = Modifier.padding(end = 12.dp)) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        val form = state.form ?: return@Scaffold
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Form(form, state.errors, state.isNew, viewModel)
        }
    }
}

@Composable
private fun Form(form: RecipeForm, errors: Set<Field>, isNew: Boolean, viewModel: RecipeEditorViewModel) {
    val locale = LocalConfiguration.current.locales[0]
    val cell = Modifier.height(CellHeight)
    // The mode decides the fields, so it is chosen once, when the recipe is created.
    if (isNew) {
        BrewModeGroup(form.mode, { mode ->
            viewModel.edit { RecipeForm.new(mode).copy(name = it.name, dose = it.dose, grind = it.grind, temp = it.temp, notes = it.notes) }
        })
    }
    Cell(form.name, { v -> viewModel.edit { it.copy(name = v) } }, R.string.recipe_name, Field.NAME in errors, CellShape, Modifier.fillMaxWidth().height(64.dp), input = Input.TEXT, textStyle = MaterialTheme.typography.titleLarge)
    if (Field.NAME in errors) ErrorText(R.string.required)
    Method(form, Field.METHOD in errors, viewModel)
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Cell(form.dose, { v -> viewModel.edit { it.copy(dose = v) } }, R.string.recipe_dose, Field.DOSE in errors, CellShape, cell.weight(1f), suffix = stringResource(R.string.unit_grams))
        Cell(form.ratio, { v -> viewModel.edit { it.copy(ratio = v) } }, R.string.recipe_ratio, Field.RATIO in errors, CellShape, cell.weight(1f), prefix = "1:")
        // Computed from the two beside it, so it reads as a result, not a field.
        CellBox(
            when (form.mode) {
                BrewMode.ESPRESSO -> R.string.recipe_yield
                BrewMode.FILTER, BrewMode.FRENCH_PRESS -> R.string.recipe_water
            },
            false,
            CellShape,
            cell.weight(1f),
            container = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                form.targetGrams?.let { Format.grams(it, locale) + " " + stringResource(R.string.unit_grams) } ?: "–",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
            )
        }
    }
    if (Field.DOSE in errors) ErrorText(R.string.recipe_error_dose)
    if (Field.RATIO in errors) ErrorText(R.string.recipe_error_ratio)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GrindCell(form.grind, Field.GRIND in errors, { v -> viewModel.edit { it.copy(grind = v) } }, cell.weight(1f))
        Cell(form.temp, { v -> viewModel.edit { it.copy(temp = v) } }, R.string.recipe_temp_short, Field.TEMP in errors, CellShape, cell.weight(1f), suffix = stringResource(R.string.unit_celsius))
        if (form.mode != BrewMode.FRENCH_PRESS) {
            CellBox(if (form.mode == BrewMode.ESPRESSO) R.string.recipe_shot_time else R.string.recipe_ideal_time, Field.WINDOW in errors, CellShape, cell.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CellInput(form.minTime, { v -> viewModel.edit { it.copy(minTime = v) } }, Input.CLOCK, Modifier.width(IntrinsicSize.Min).widthIn(min = 16.dp), stringResource(R.string.recipe_window_min))
                    Text("–", Modifier.padding(horizontal = 2.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CellInput(form.maxTime, { v -> viewModel.edit { it.copy(maxTime = v) } }, Input.CLOCK, Modifier.weight(1f), stringResource(R.string.recipe_window_max))
                }
            }
        }
    }
    if (Field.GRIND in errors) ErrorText(R.string.recipe_error_grind)
    if (Field.TEMP in errors) ErrorText(R.string.recipe_error_temp)
    if (Field.WINDOW in errors) ErrorText(if (form.mode == BrewMode.ESPRESSO) R.string.recipe_error_window else R.string.recipe_error_total_time)
    if (form.mode != BrewMode.ESPRESSO) {
        Spacer(Modifier.height(8.dp))
        Steps(form, Field.STEPS in errors, viewModel)
    }
    Spacer(Modifier.height(8.dp))
    Cell(form.notes, { v -> viewModel.edit { it.copy(notes = v) } }, R.string.recipe_notes, false, CellShape, Modifier.fillMaxWidth().heightIn(min = CellHeight), input = Input.TEXT, singleLine = false)
}

// Chips only: the last one opens a field for a method the list does not have.
@Composable
private fun Method(form: RecipeForm, error: Boolean, viewModel: RecipeEditorViewModel) {
    val methods = RecipeForm.METHODS.getValue(form.mode)
    var custom by rememberSaveable(form.mode) { mutableStateOf(form.method.isNotBlank() && form.method !in methods) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        methods.forEach { method ->
            FilterChip(
                selected = !custom && form.method == method,
                onClick = { custom = false; viewModel.edit { it.copy(method = method) } },
                label = { Text(method) },
            )
        }
        FilterChip(
            selected = custom,
            onClick = { if (!custom) { custom = true; viewModel.edit { it.copy(method = "") } } },
            label = { Text(stringResource(R.string.method_other)) },
        )
    }
    if (custom) {
        Cell(form.method, { v -> viewModel.edit { it.copy(method = v) } }, R.string.recipe_method, error, CellShape, Modifier.fillMaxWidth().height(CellHeight), input = Input.TEXT)
    }
    if (error) ErrorText(R.string.required)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Steps(form: RecipeForm, error: Boolean, viewModel: RecipeEditorViewModel) {
    val steps = form.steps
    // The step whose sheet is open; the list itself stays one row per step.
    var open by rememberSaveable { mutableStateOf(-1) }
    Text(stringResource(R.string.recipe_steps), style = MaterialTheme.typography.titleMedium)
    Timeline(form)
    // The step being dragged by its handle and how far it is from its place, in pixels.
    var dragging by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }
    var rowHeight by remember { mutableStateOf(0) }
    val gap = with(LocalDensity.current) { 2.dp.toPx() }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        steps.forEachIndexed { index, step ->
            val current by rememberUpdatedState(index)
            val count by rememberUpdatedState(steps.size)
            StepSummary(
                index,
                step,
                error,
                groupShape(index, steps.size),
                Modifier
                    .onSizeChanged { rowHeight = it.height }
                    .zIndex(if (dragging == index) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging == index) dragOffset else 0f },
                handle = Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragging = current; dragOffset = 0f },
                        onDragEnd = { dragging = -1; dragOffset = 0f },
                        onDragCancel = { dragging = -1; dragOffset = 0f },
                    ) { change, amount ->
                        change.consume()
                        dragOffset += amount.y
                        val slot = rowHeight + gap
                        // Past half a row, the step trades places with its neighbour and the finger keeps holding it.
                        if (dragOffset > slot / 2 && dragging < count - 1) {
                            viewModel.moveStep(dragging, dragging + 1); dragging += 1; dragOffset -= slot
                        } else if (dragOffset < -slot / 2 && dragging > 0) {
                            viewModel.moveStep(dragging, dragging - 1); dragging -= 1; dragOffset += slot
                        }
                    }
                },
                onMoveUp = if (index > 0) ({ viewModel.moveStep(index, index - 1) }) else null,
                onMoveDown = if (index < steps.lastIndex) ({ viewModel.moveStep(index, index + 1) }) else null,
                onRemove = { viewModel.removeStep(index) },
            ) { open = index }
        }
    }
    if (error) ErrorText(if (steps.isEmpty()) R.string.recipe_error_no_steps else R.string.recipe_error_steps)
    FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { viewModel.addStep(); open = steps.size }) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null)
        Text(stringResource(R.string.add_step), Modifier.padding(start = 8.dp))
    }
    steps.getOrNull(open)?.let { step ->
        ModalBottomSheet(onDismissRequest = { open = -1 }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            StepSheet(
                open,
                step,
                error,
                stepKindsFor(form.mode),
                onChange = { viewModel.updateStep(open, it) },
                onRemove = { val index = open; open = -1; viewModel.removeStep(index) },
                onMoveUp = if (open > 0) ({ viewModel.moveStep(open, open - 1); open -= 1 }) else null,
                onMoveDown = if (open < steps.lastIndex) ({ viewModel.moveStep(open, open + 1); open += 1 }) else null,
                onDone = { open = -1 },
            )
        }
    }
}

// The fields of one step: what kind it is, how far it pours, when it starts and, for a pour, how fast.
@Composable
private fun StepSheet(
    index: Int,
    step: StepForm,
    error: Boolean,
    allowedKinds: List<StepKind>,
    onChange: (StepForm) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDone: () -> Unit,
) {
    val cell = Modifier.height(CellHeight)
    Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.step_sheet_title, index + 1), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            // Reordering without dragging: the same move the handle makes, one place at a time.
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { onMoveUp?.invoke() }, enabled = onMoveUp != null) {
                Icon(painterResource(R.drawable.ic_arrow_upward), contentDescription = stringResource(R.string.step_move_up))
            }
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { onMoveDown?.invoke() }, enabled = onMoveDown != null) {
                Icon(painterResource(R.drawable.ic_arrow_downward), contentDescription = stringResource(R.string.step_move_down))
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            allowedKinds.forEach { kind ->
                FilterChip(selected = step.kind == kind, onClick = { onChange(step.copy(kind = kind)) }, label = { Text(stringResource(kind.label)) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (step.kind.pours) {
                Cell(step.weight, { onChange(step.copy(weight = it)) }, R.string.step_weight, error, CellShape, cell.weight(1f), suffix = stringResource(R.string.unit_grams))
            }
            Cell(step.start, { onChange(step.copy(start = it)) }, R.string.step_start, error, CellShape, cell.weight(1f), input = Input.CLOCK)
            if (step.kind.pours) {
                Cell(step.flowTarget, { onChange(step.copy(flowTarget = it)) }, R.string.step_flow_target, error, CellShape, cell.weight(1f), suffix = stringResource(R.string.unit_grams_per_second))
            }
        }
        Text(stringResource(R.string.recipe_steps_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (error) ErrorText(R.string.recipe_error_steps)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onRemove, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(R.string.remove_step))
            }
            Button(shapes = ButtonDefaults.shapes(), onClick = onDone) { Text(stringResource(R.string.done)) }
        }
    }
}

// Rows of one list read as one group: full corners outside, small ones between rows.
private fun groupShape(index: Int, size: Int): Shape {
    val outer = CornerSize(16.dp)
    val inner = CornerSize(4.dp)
    val top = if (index == 0) outer else inner
    val bottom = if (index == size - 1) outer else inner
    return RoundedCornerShape(top, top, bottom, bottom)
}

/** Where each step starts, and where the brew ends: the ideal time when it is later than the last step. */
internal fun timelineMarks(form: RecipeForm): List<Long> {
    var last = 0L
    val starts = form.steps.map { step -> (RecipeForm.clock(step.start) ?: last).coerceAtLeast(last).also { last = it } }
    val end = RecipeForm.clock(form.maxTime)?.takeIf { it > last }
    return (starts + listOfNotNull(end)).distinct()
}

// The steps as one bar, each as wide as it lasts, with its start time under it.
@Composable
private fun Timeline(form: RecipeForm) {
    val marks = timelineMarks(form)
    if (marks.size < 2) return
    val colors = MaterialTheme.colorScheme
    val total = (marks.last() - marks.first()).toFloat()
    Column(Modifier.clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            marks.zipWithNext().forEachIndexed { index, (from, to) ->
                // A short step keeps a visible sliver.
                Box(Modifier.weight(((to - from) / total).coerceAtLeast(0.06f)).fillMaxHeight().background(if (index % 2 == 0) colors.primary else colors.tertiary, CircleShape))
            }
        }
        Box(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                marks.zipWithNext().forEach { (from, to) ->
                    Text(
                        RecipeForm.clockText(from),
                        Modifier.weight(((to - from) / total).coerceAtLeast(0.06f)),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        softWrap = false,
                    )
                }
            }
            Text(RecipeForm.clockText(marks.last()), Modifier.align(Alignment.CenterEnd).background(colors.surface).padding(start = 4.dp), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}

// One row per step: its number, its kind and what it asks for. Tapping it opens its sheet.
@Composable
private fun StepSummary(
    index: Int,
    step: StepForm,
    error: Boolean,
    shape: Shape,
    modifier: Modifier,
    handle: Modifier,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onRemove: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val summary = buildList {
        if (step.kind.pours && step.weight.isNotBlank()) add(stringResource(R.string.step_summary_weight, step.weight))
        RecipeForm.clock(step.start)?.let { add(RecipeForm.clockText(it)) }
        if (step.kind.pours && step.flowTarget.isNotBlank()) add(step.flowTarget + " " + stringResource(R.string.unit_grams_per_second))
    }.joinToString(" · ")
    val moveUp = stringResource(R.string.step_move_up)
    val moveDown = stringResource(R.string.step_move_down)
    val remove = stringResource(R.string.remove_step)
    Row(
        modifier.fillMaxWidth().heightIn(min = 60.dp).clip(shape).background(colors.surfaceContainer).clickable(onClick = onClick)
            // TalkBack reorders and removes from the row's actions, without the drag.
            .semantics {
                customActions = listOfNotNull(
                    onMoveUp?.let { CustomAccessibilityAction(moveUp) { it(); true } },
                    onMoveDown?.let { CustomAccessibilityAction(moveDown) { it(); true } },
                    CustomAccessibilityAction(remove) { onRemove(); true },
                )
            }
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).background(if (error) colors.errorContainer else colors.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Text((index + 1).toString(), style = MaterialTheme.typography.labelLarge, color = if (error) colors.onErrorContainer else colors.onPrimaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(step.kind.label), style = MaterialTheme.typography.titleMedium)
            if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1)
        }
        // Decorative for TalkBack: the row's actions do the same.
        Box(handle.size(48.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_drag_handle), contentDescription = null, tint = colors.onSurfaceVariant)
        }
    }
}

private val CellHeight = 56.dp
private val CellShape = RoundedCornerShape(12.dp)

private enum class Input { TEXT, DECIMAL, INTEGER, CLOCK }

@Composable
private fun GrindCell(value: String, isError: Boolean, onValue: (String) -> Unit, modifier: Modifier) {
    val valueNumber = value.toIntOrNull()
    val label = stringResource(R.string.recipe_grind)
    val decrease = stringResource(R.string.recipe_grind_decrease)
    val increase = stringResource(R.string.recipe_grind_increase)
    CellInput(value, onValue, Input.INTEGER, modifier, description = label) { inner ->
        CellBox(R.string.recipe_grind, isError, CellShape, Modifier.fillMaxSize()) {
            // The number sits where the other cells' values do; − and + share the right edge.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { inner() }
                IconButton(
                    shapes = IconButtonDefaults.shapes(),
                    onClick = { valueNumber?.let { onValue((it - 1).toString()) } },
                    modifier = Modifier.size(32.dp).semantics { contentDescription = decrease },
                    enabled = valueNumber != null,
                ) { Text("−", style = MaterialTheme.typography.titleMedium) }
                IconButton(
                    shapes = IconButtonDefaults.shapes(),
                    onClick = { onValue(((valueNumber ?: 0) + 1).toString()) },
                    modifier = Modifier.size(32.dp).semantics { contentDescription = increase },
                ) { Text("+", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

// A filled field without the indicator line, its label small inside, so fields sit side by side as one compact row.
@Composable
private fun Cell(
    value: String,
    onValue: (String) -> Unit,
    label: Int,
    isError: Boolean,
    shape: Shape,
    modifier: Modifier,
    prefix: String? = null,
    suffix: String? = null,
    input: Input = Input.DECIMAL,
    singleLine: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val colors = MaterialTheme.colorScheme
    // The label sits in the decoration, so the field is announced and found by it.
    CellInput(value, onValue, input, modifier, singleLine = singleLine, textStyle = textStyle) { inner ->
        CellBox(label, isError, shape, if (singleLine) Modifier.fillMaxSize() else Modifier.fillMaxWidth().heightIn(min = CellHeight)) {
            Row(verticalAlignment = Alignment.Bottom) {
                prefix?.let { Text(it, style = textStyle, color = colors.onSurfaceVariant) }
                Box(Modifier.weight(1f)) { inner() }
                suffix?.let { Text(it, style = textStyle, color = colors.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun CellBox(
    label: Int,
    isError: Boolean,
    shape: Shape,
    modifier: Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.background(if (isError) colors.errorContainer else container, shape).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = if (isError) colors.onErrorContainer else colors.onSurfaceVariant, maxLines = 1)
        content()
    }
}

@Composable
private fun CellInput(
    value: String,
    onValue: (String) -> Unit,
    input: Input,
    modifier: Modifier,
    description: String? = null,
    singleLine: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    decoration: @Composable (@Composable () -> Unit) -> Unit = { it() },
) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        decorationBox = decoration,
        value = value,
        onValueChange = { v ->
            onValue(
                when (input) {
                    Input.TEXT -> v.take(80)
                    Input.CLOCK -> v.filter { it.isDigit() || it == ':' }.take(5)
                    Input.INTEGER -> v.filterIndexed { index, char -> char.isDigit() || (index == 0 && char == '-') }.take(11)
                    Input.DECIMAL -> v.filter { it.isDigit() || it == ',' || it == '.' }.take(6)
                },
            )
        },
        modifier = if (description != null) modifier.semantics { contentDescription = description } else modifier,
        singleLine = singleLine,
        textStyle = textStyle.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = when (input) {
            Input.TEXT -> KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            Input.CLOCK -> KeyboardOptions(keyboardType = KeyboardType.Number)
            Input.INTEGER -> KeyboardOptions(keyboardType = KeyboardType.Number)
            Input.DECIMAL -> KeyboardOptions(keyboardType = KeyboardType.Decimal)
        },
    )
}

@Composable
private fun ErrorText(id: Int) = Text(stringResource(id), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

/** The step kinds the editor offers: a French press stirs and steeps; a filter does neither. */
internal fun stepKindsFor(mode: BrewMode): List<StepKind> =
    StepKind.entries.filter { (it != StepKind.STIR && it != StepKind.PRESS) || mode == BrewMode.FRENCH_PRESS }

