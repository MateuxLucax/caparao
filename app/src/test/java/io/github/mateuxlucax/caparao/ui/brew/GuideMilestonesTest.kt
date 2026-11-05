package io.github.mateuxlucax.caparao.ui.brew

import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.BrewMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideMilestonesTest {
    private val espresso = guideMilestones(BrewMode.ESPRESSO)!!
    private val filter = guideMilestones(BrewMode.FILTER)!!

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
    fun filterDeclaresRecipeBeansPrepareBrewAndDoneMilestones() {
        assertEquals(
            listOf(R.string.milestone_recipe, R.string.milestone_beans, R.string.milestone_prepare, R.string.milestone_brew, R.string.milestone_done),
            filter.map { it.name },
        )
        listOf(GuideStage.TARE, GuideStage.POUR, GuideStage.BREWING_FILTER).forEach {
            assertEquals(3, milestoneIndex(filter, it))
        }
        assertEquals(4, milestoneIndex(filter, GuideStage.DONE))
        assertEquals(
            3,
            milestoneIndex(filter, guideStage(BrewMode.FILTER, BrewStep.LIVE, io.github.mateuxlucax.caparao.brew.BrewPhase.PAUSED)),
        )
        guideStages(BrewMode.FILTER).forEach { stage ->
            assertEquals("$stage belongs to one filter milestone", 1, filter.count { stage in it.stages })
        }
    }

    @Test
    fun frenchPressKeepsItsCountedBarForNow() {
        assertNull(guideMilestones(BrewMode.FRENCH_PRESS))
    }

    @Test
    fun filterPreparationIsCountedBetweenBeansAndTareWithoutChangingFrenchPress() {
        assertEquals(
            listOf(GuideStage.DOSE, GuideStage.BEANS, GuideStage.PREPARE_FILTER, GuideStage.TARE, GuideStage.POUR, GuideStage.BREWING_FILTER, GuideStage.DONE),
            guideStages(BrewMode.FILTER),
        )
        assertEquals(GuideStage.PREPARE_FILTER, guideStage(BrewMode.FILTER, BrewStep.PREPARE_PORTAFILTER, io.github.mateuxlucax.caparao.brew.BrewPhase.WAITING_FOR_CUP))
        assertEquals(
            listOf(GuideStage.DOSE, GuideStage.BEANS, GuideStage.PREPARE_FRENCH_PRESS, GuideStage.TARE, GuideStage.POUR, GuideStage.BREWING_FRENCH_PRESS, GuideStage.DONE),
            guideStages(BrewMode.FRENCH_PRESS),
        )
        assertEquals(GuideStage.PREPARE_FRENCH_PRESS, guideStage(BrewMode.FRENCH_PRESS, BrewStep.PREPARE_PORTAFILTER, io.github.mateuxlucax.caparao.brew.BrewPhase.WAITING_FOR_CUP))
        assertEquals(GuideStage.PREPARE_PORTAFILTER, guideStage(BrewMode.ESPRESSO, BrewStep.PREPARE_PORTAFILTER, io.github.mateuxlucax.caparao.brew.BrewPhase.WAITING_FOR_CUP))
    }
}
