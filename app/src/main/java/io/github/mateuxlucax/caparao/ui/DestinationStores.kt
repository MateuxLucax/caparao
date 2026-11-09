package io.github.mateuxlucax.caparao.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * One [ViewModelStore] per open destination (#310), so a screen's ViewModels are cleared when it leaves instead of
 * living until the activity finishes. The activity holds this registry, so a rotation keeps every open destination's
 * ViewModels, and clearing the activity clears whatever is still open.
 */
internal class DestinationStores : ViewModel() {
    private val owners = mutableMapOf<String, DestinationOwner>()

    /** The owner of [key], created the first time the destination opens and reused until it is released. */
    fun owner(key: String): ViewModelStoreOwner = owners.getOrPut(key) { DestinationOwner() }

    /** Whether [key] is still open; a released destination gets a fresh owner and fresh ViewModels next time. */
    fun holds(key: String): Boolean = key in owners

    /** The open store of [key], or null once the destination has left; a test reads a screen's state here. */
    fun viewModels(key: String): ViewModelStore? = owners[key]?.viewModelStore

    /** The destination left the screen: clear its ViewModels (each [ViewModel.onCleared] runs) and forget the store. */
    fun release(key: String) {
        owners.remove(key)?.viewModelStore?.clear()
    }

    override fun onCleared() {
        owners.values.forEach { it.viewModelStore.clear() }
        owners.clear()
    }

    private class DestinationOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    companion object {
        /** The activity's ViewModel key, fixed so a test can read the registry back. */
        const val KEY = "destination-stores"
    }
}
