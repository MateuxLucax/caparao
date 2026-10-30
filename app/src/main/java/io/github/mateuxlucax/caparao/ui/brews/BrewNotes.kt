package io.github.mateuxlucax.caparao.ui.brews

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.Brew

/** A brew's notes as typed (docs/adr/0021-brew-notes.md), on the save screen and in the brew detail. */
data class NotesForm(
    val coffee: String = "",
    val grind: String = "",
    val temp: String = "",
    /** 1–5, null when not rated. */
    val rating: Int? = null,
    val notes: String = "",
) {
    /** The temperature is empty or 1–100 °C. */
    val valid: Boolean get() = temp.isBlank() || temp.trim().toIntOrNull() in 1..100

    /** [brew] with these notes; blank fields become null. Call only when [valid]. */
    fun applyTo(brew: Brew) = brew.copy(
        coffee = coffee.trim().ifEmpty { null },
        grind = grind.trim().ifEmpty { null },
        waterTempCelsius = temp.trim().toIntOrNull(),
        rating = rating,
        notes = notes.trim().ifEmpty { null },
    )

    companion object {
        /** [brew]'s notes; a brew without a coffee yet gets [suggestedCoffee], since a bag lasts many brews. */
        fun of(brew: Brew, suggestedCoffee: String? = null) = NotesForm(
            coffee = brew.coffee ?: suggestedCoffee.orEmpty(),
            grind = brew.grind.orEmpty(),
            temp = brew.waterTempCelsius?.toString().orEmpty(),
            rating = brew.rating,
            notes = brew.notes.orEmpty(),
        )
    }
}

/** Coffee (with past ones to pick), grind, temperature, a 1–5 rating and notes. */
@Composable
fun BrewNotesFields(form: NotesForm, coffees: List<String>, onChange: (NotesForm) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Rating(form.rating) { onChange(form.copy(rating = it)) }
        OutlinedTextField(
            form.coffee, { onChange(form.copy(coffee = it.take(80))) }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.brew_coffee)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        val others = coffees.filter { it != form.coffee }
        if (others.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                others.forEach { coffee -> SuggestionChip(onClick = { onChange(form.copy(coffee = coffee)) }, label = { Text(coffee) }) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                form.grind, { onChange(form.copy(grind = it.take(40))) }, Modifier.weight(1f),
                label = { Text(stringResource(R.string.recipe_grind)) }, singleLine = true,
            )
            OutlinedTextField(
                form.temp, { onChange(form.copy(temp = it.filter(Char::isDigit).take(3))) }, Modifier.weight(1f),
                label = { Text(stringResource(R.string.recipe_temp), maxLines = 1) }, singleLine = true,
                suffix = { Text(stringResource(R.string.unit_celsius)) }, isError = !form.valid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        OutlinedTextField(
            form.notes, { onChange(form.copy(notes = it.take(1000))) }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.recipe_notes)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
}

/** Five stars; tapping the current rating clears it. */
@Composable
private fun Rating(rating: Int?, onRate: (Int?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.brew_rating), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        (1..5).forEach { star ->
            IconButton(onClick = { onRate(if (rating == star) null else star) }) {
                Icon(
                    painterResource(if (rating != null && star <= rating) R.drawable.ic_star else R.drawable.ic_star_outline),
                    contentDescription = stringResource(R.string.rating_stars, star),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
