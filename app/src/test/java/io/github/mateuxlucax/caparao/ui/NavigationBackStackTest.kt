package io.github.mateuxlucax.caparao.ui

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

/** Closing a screen must not pop the entry underneath it or leave Navigation 3 with an empty stack (#352). */
class NavigationBackStackTest {
    private fun stack(vararg keys: NavKey) = keys.toMutableList()

    @Test
    fun popsItsOwnEntryWhenItIsTheTop() {
        val backStack = stack(Destination.Tabs, Destination.Detail("b1"))
        backStack.popEntry(Destination.Detail("b1"))
        assertEquals(listOf<NavKey>(Destination.Tabs), backStack)
    }

    @Test
    fun aSecondCloseDuringTheExitTransitionDoesNotPopTheTabs() {
        val backStack = stack(Destination.Tabs, Destination.Detail("b1"))
        backStack.popEntry(Destination.Detail("b1"))
        backStack.popEntry(Destination.Detail("b1"))
        assertEquals(listOf<NavKey>(Destination.Tabs), backStack)
    }

    @Test
    fun neverEmptiesTheBackStack() {
        val backStack = stack(Destination.Tabs)
        backStack.popEntry(Destination.Tabs)
        assertEquals(listOf<NavKey>(Destination.Tabs), backStack)
    }

    @Test
    fun leavesAnEntryThatIsNoLongerOnTop() {
        val backStack = stack(Destination.Tabs, Destination.Coffee("bag1"), Destination.Detail("b1"))
        backStack.popEntry(Destination.Coffee("bag1"))
        assertEquals(listOf<NavKey>(Destination.Tabs, Destination.Coffee("bag1"), Destination.Detail("b1")), backStack)
    }
}
