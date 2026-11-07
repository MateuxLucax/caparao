package io.github.mateuxlucax.caparao.ui.brews

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.CoffeeBag
import io.github.mateuxlucax.caparao.ui.theme.animationsOff

/**
 * A brew's notes as typed (#105), on the save screen and in the brew detail. The coffee is a bag (#291): its name is kept
 * with the link, and typing a bag's exact name links it again.
 */
data class NotesForm(
    val coffee: String = "",
    /** The bag the coffee links to; null when the name is free text. */
    val bagId: String? = null,
    val grind: String = "",
    val temp: String = "",
    /** 1–5, null when not rated. */
    val rating: Int? = null,
    val notes: String = "",
) {
    /** The temperature is empty or 1–100 °C. */
    val valid: Boolean get() = temp.isBlank() || temp.trim().toIntOrNull() in 1..100

    /** The coffee name typed as [text]; it links to the open bag of that name, if any. */
    fun withCoffee(text: String, bags: List<CoffeeBag>) = copy(coffee = text, bagId = bags.find { it.name == text }?.id)

    /** The coffee is [bag]. */
    fun withBag(bag: CoffeeBag) = copy(coffee = bag.name, bagId = bag.id)

    /** [brew] with these notes; blank fields become null. Call only when [valid]. */
    fun applyTo(brew: Brew) = brew.copy(
        coffee = coffee.trim().ifEmpty { null },
        bagId = bagId.takeIf { coffee.isNotBlank() },
        grind = grind.trim().ifEmpty { null },
        waterTempCelsius = temp.trim().toIntOrNull(),
        rating = rating,
        notes = notes.trim().ifEmpty { null },
    )

    companion object {
        /** [brew]'s notes, with the coffee and bag it was saved with. */
        fun of(brew: Brew) = NotesForm(
            coffee = brew.coffee.orEmpty(),
            bagId = brew.bagId,
            grind = brew.grind.orEmpty(),
            temp = brew.waterTempCelsius?.toString().orEmpty(),
            rating = brew.rating,
            notes = brew.notes.orEmpty(),
        )
    }
}

/** Coffee (the open bags to pick), grind, temperature, a 1–5 rating and notes. */
@Composable
fun BrewNotesFields(form: NotesForm, bags: List<CoffeeBag>, onChange: (NotesForm) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Rating(form.rating) { onChange(form.copy(rating = it)) }
        OutlinedTextField(
            form.coffee, { onChange(form.withCoffee(it.take(80), bags)) }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.brew_coffee)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = MaterialTheme.shapes.medium,
        )
        val others = bags.filter { it.id != form.bagId }
        if (others.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                others.forEach { bag ->
                    FilledTonalButton(
                        onClick = { onChange(form.withBag(bag)) },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.heightIn(min = ButtonDefaults.ExtraSmallContainerHeight),
                        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.ExtraSmallContainerHeight),
                    ) { Text(bag.name) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                form.grind, { onChange(form.copy(grind = it.take(40))) }, Modifier.weight(1f),
                label = { Text(stringResource(R.string.recipe_grind)) }, singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
            OutlinedTextField(
                form.temp, { onChange(form.copy(temp = it.filter(Char::isDigit).take(3))) }, Modifier.weight(1f),
                label = { Text(stringResource(R.string.recipe_temp), maxLines = 1) }, singleLine = true,
                suffix = { Text(stringResource(R.string.unit_celsius)) }, isError = !form.valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.medium,
            )
        }
        OutlinedTextField(
            form.notes, { onChange(form.copy(notes = it.take(1000))) }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.recipe_notes)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = MaterialTheme.shapes.medium,
        )
    }
}

/** Five stars; tapping the current rating clears it. */
@Composable
private fun Rating(rating: Int?, onRate: (Int?) -> Unit) {
    val still = animationsOff()
    val haptic = LocalHapticFeedback.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.brew_rating), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        (1..5).forEach { star ->
            val filled = rating != null && star <= rating
            // A star that fills or empties springs from a little smaller back to its resting size; the tap gives a tick.
            val pop = remember { Animatable(1f) }
            val shown = remember { mutableStateOf(filled) }
            LaunchedEffect(filled) {
                if (shown.value != filled) {
                    shown.value = filled
                    if (!still) {
                        pop.snapTo(0.8f)
                        pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                    }
                }
            }
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRate(if (rating == star) null else star)
            }) {
                Icon(
                    painterResource(if (filled) R.drawable.ic_star else R.drawable.ic_star_outline),
                    contentDescription = stringResource(R.string.rating_stars, star),
                    modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
