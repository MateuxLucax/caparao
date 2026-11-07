package io.github.mateuxlucax.caparao.ui.coffees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.mateuxlucax.caparao.data.local.BrewRepository
import io.github.mateuxlucax.caparao.data.local.CoffeeBagRepository
import io.github.mateuxlucax.caparao.model.Brew
import io.github.mateuxlucax.caparao.model.CoffeeBag
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One coffee bag: what is left, the brews that used it, and finishing or deleting it (#290, C4 and C5). */
class CoffeeBagDetailViewModel(
    private val id: String,
    private val bags: CoffeeBagRepository,
    brews: BrewRepository,
) : ViewModel() {
    /** Null while loading, and once the bag is deleted. */
    val bag: StateFlow<CoffeeBag?> = bags.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val brews: StateFlow<List<Brew>> = brews.forBag(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFinished(finished: Boolean) {
        val current = bag.value ?: return
        viewModelScope.launch { bags.save(current.copy(finished = finished)) }
    }

    /** The brews keep their coffee name; the screen closes once this is called. */
    fun delete() {
        viewModelScope.launch { bags.delete(id) }
    }

    companion object {
        val BAG_ID = object : CreationExtras.Key<String> {}
    }
}
