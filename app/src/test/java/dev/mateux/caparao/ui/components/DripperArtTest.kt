package dev.mateux.caparao.ui.components

import dev.mateux.caparao.ui.components.DripperArt.Part
import org.junit.Assert.assertEquals
import org.junit.Test

class DripperArtTest {
    @Test
    fun checkedCountRevealsPartsInOrderAndUncheckingRemovesTheLastPart() {
        assertEquals(emptyList<Part>(), DripperArt.visibleParts(0))
        assertEquals(listOf(Part.SERVER), DripperArt.visibleParts(1))
        assertEquals(DripperArt.parts, DripperArt.visibleParts(5))
        assertEquals(DripperArt.visibleParts(4), DripperArt.visibleParts(5).dropLast(1))
    }
}
