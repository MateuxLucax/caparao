package io.github.mateuxlucax.caparao.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** One store per destination, cleared when it leaves and kept while it is still on the way back (#310). */
class DestinationStoresTest {
    private var cleared = 0

    private val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CountingViewModel { cleared++ } as T
    }

    private fun DestinationStores.open(key: String) = ViewModelProvider(owner(key), factory).get(CountingViewModel::class.java)

    @Test
    fun theSameDestinationKeepsOneStoreAndAnotherGetsItsOwn() {
        val stores = DestinationStores()
        assertSame(stores.owner("detail-brew-1"), stores.owner("detail-brew-1"))
        assertNotSame(stores.owner("detail-brew-1"), stores.owner("detail-brew-2"))
    }

    @Test
    fun leavingADestinationClearsItsViewModelsAndReopeningGetsFreshOnes() {
        val stores = DestinationStores()
        val first = stores.open("detail-brew-1")
        assertEquals(0, cleared)
        assertTrue(stores.holds("detail-brew-1"))

        stores.release("detail-brew-1")

        assertEquals(1, cleared)
        assertFalse(stores.holds("detail-brew-1"))
        val reopened = stores.open("detail-brew-1")
        assertEquals(1, cleared)
        assertNotSame(first, reopened)
    }

    @Test
    fun aDestinationStillOpenWhenAnotherLeavesKeepsItsViewModels() {
        val stores = DestinationStores()
        stores.open("coffee-bag-1")
        stores.open("detail-brew-1")

        stores.release("detail-brew-1")

        assertEquals(1, cleared)
        assertTrue(stores.holds("coffee-bag-1"))
        assertSame(stores.owner("coffee-bag-1"), stores.owner("coffee-bag-1"))
    }

    @Test
    fun finishingTheActivityClearsEveryOpenDestination() {
        val stores = DestinationStores()
        stores.open("coffee-bag-1")
        stores.open("detail-brew-1")
        // ComponentActivity clears its ViewModelStore when it finishes; the registry's onCleared does the rest.
        val activity = ViewModelStore()
        activity.put(DestinationStores.KEY, stores)

        activity.clear()

        assertEquals(2, cleared)
        assertFalse(stores.holds("coffee-bag-1"))
        assertFalse(stores.holds("detail-brew-1"))
    }

    private class CountingViewModel(private val onClosed: () -> Unit) : ViewModel() {
        override fun onCleared() = onClosed()
    }
}
