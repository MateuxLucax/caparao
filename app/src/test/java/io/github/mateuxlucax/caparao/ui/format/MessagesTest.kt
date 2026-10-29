package io.github.mateuxlucax.caparao.ui.format

import io.github.mateuxlucax.caparao.model.ScaleError
import io.github.mateuxlucax.caparao.model.ScaleState
import io.github.mateuxlucax.caparao.model.SyncError
import org.junit.Assert.assertEquals
import org.junit.Test

/** A copy-pasted branch would show one error's text for another. */
class MessagesTest {
    private fun <T> assertDistinct(values: List<T>) = assertEquals(values.size, values.toSet().size)

    @Test
    fun everyScaleErrorHasItsOwnTitleAndHint() {
        assertDistinct(ScaleError.entries.map { it.title })
        assertDistinct(ScaleError.entries.map { it.hint })
    }

    @Test
    fun aFailedScaleIsLabelledWithItsError() {
        ScaleError.entries.forEach { assertEquals(it.title, ScaleState.Failed(it, retrying = false).label) }
        assertDistinct(listOf(ScaleState.Idle, ScaleState.Searching, ScaleState.Connecting).map { it.label })
    }

    @Test
    fun everySyncErrorKindHasItsOwnMessage() {
        assertDistinct(SyncError.Kind.entries.map { it.message })
    }
}
