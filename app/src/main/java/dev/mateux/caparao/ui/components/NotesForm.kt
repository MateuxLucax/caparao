package dev.mateux.caparao.ui.components

import dev.mateux.caparao.model.Brew
import dev.mateux.caparao.model.CoffeeBag

/** A brew's editable notes, shared by its summary and detail screens. */
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
