package io.github.mateuxlucax.caparao.ui.brew

import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.BrewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideMilestonesTest {
    private val espresso = guideMilestones(BrewMode.ESPRESSO)!!

    @Test
    fun espressoDeclaresFiveNamedMilestonesInOrder() {
        assertEquals(
            listOf(R.string.milestone_recipe, R.string.milestone_beans, R.string.milestone_portafilter, R.string.milestone_shot, R.string.milestone_done),
            espresso.map { it.name },
        )
    }

    @Test
    fun cupTareFirstDropAndExtractingAreAllTheShotMilestone() {
        listOf(GuideStage.CUP, GuideStage.TARE, GuideStage.FIRST_DROP, GuideStage.BREWING_ESPRESSO).forEach {
            assertEquals(3, milestoneIndex(espresso, it))
        }
        assertEquals(0, milestoneIndex(espresso, GuideStage.DOSE))
        assertEquals(1, milestoneIndex(espresso, GuideStage.BEANS))
        assertEquals(2, milestoneIndex(espresso, GuideStage.PREPARE_PORTAFILTER))
        assertEquals(4, milestoneIndex(espresso, GuideStage.DONE))
    }

    @Test
    fun everyEspressoStageBelongsToExactlyOneMilestone() {
        guideStages(BrewMode.ESPRESSO).forEach { stage ->
            assertEquals(1, espresso.count { stage in it.stages })
        }
    }

    @Test
    fun filterAndFrenchPressKeepTheirCountedBarForNow() {
        assertNull(guideMilestones(BrewMode.FILTER))
        assertNull(guideMilestones(BrewMode.FRENCH_PRESS))
    }
}
