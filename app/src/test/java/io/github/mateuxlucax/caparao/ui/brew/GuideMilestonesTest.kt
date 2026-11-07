package io.github.mateuxlucax.caparao.ui.brew

import io.github.mateuxlucax.caparao.R
import io.github.mateuxlucax.caparao.model.BrewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class GuideMilestonesTest {
    private val espresso = guideMilestones(BrewMode.ESPRESSO)!!
    private val filter = guideMilestones(BrewMode.FILTER)!!

    @Test
    fun espressoDeclaresSixNamedMilestonesInOrder() {
        assertEquals(
            listOf(R.string.milestone_recipe, R.string.milestone_coffee, R.string.milestone_beans, R.string.milestone_portafilter, R.string.milestone_shot, R.string.milestone_done),
            espresso.map { it.name },
        )
    }

    @Test
    fun cupTareFirstDropAndExtractingAreAllTheShotMilestone() {
        listOf(GuideStage.CUP, GuideStage.TARE, GuideStage.FIRST_DROP, GuideStage.BREWING_ESPRESSO).forEach {
            assertEquals(4, milestoneIndex(espresso, it))
        }
        assertEquals(0, milestoneIndex(espresso, GuideStage.DOSE))
        assertEquals(1, milestoneIndex(espresso, GuideStage.COFFEE))
        assertEquals(2, milestoneIndex(espresso, GuideStage.BEANS))
        assertEquals(3, milestoneIndex(espresso, GuideStage.PREPARE_PORTAFILTER))
        assertEquals(5, milestoneIndex(espresso, GuideStage.DONE))
    }

    @Test
    fun everyEspressoStageBelongsToExactlyOneMilestone() {
        guideStages(BrewMode.ESPRESSO).forEach { stage ->
            assertEquals(1, espresso.count { stage in it.stages })
        }
    }

    @Test
    fun filterDeclaresRecipeCoffeeBeansPrepareBrewAndDoneMilestones() {
        assertEquals(
            listOf(R.string.milestone_recipe, R.string.milestone_coffee, R.string.milestone_beans, R.string.milestone_prepare, R.string.milestone_brew, R.string.milestone_done),
            filter.map { it.name },
        )
        listOf(GuideStage.TARE, GuideStage.POUR, GuideStage.BREWING_FILTER).forEach {
            assertEquals(4, milestoneIndex(filter, it))
        }
        assertEquals(5, milestoneIndex(filter, GuideStage.DONE))
        assertEquals(
            4,
            milestoneIndex(filter, guideStage(BrewMode.FILTER, BrewStep.LIVE, io.github.mateuxlucax.caparao.brew.BrewPhase.PAUSED)),
        )
        guideStages(BrewMode.FILTER).forEach { stage ->
            assertEquals("$stage belongs to one filter milestone", 1, filter.count { stage in it.stages })
        }
    }

    @Test
    fun frenchPressDeclaresRecipeCoffeeBeansPrepareBrewServeAndDoneMilestones() {
        val press = guideMilestones(BrewMode.FRENCH_PRESS)
        assertEquals(
            listOf(R.string.milestone_recipe, R.string.milestone_coffee, R.string.milestone_beans, R.string.milestone_prepare_press, R.string.milestone_brew, R.string.milestone_serve, R.string.milestone_done),
            press.map { it.name },
        )
        listOf(GuideStage.TARE, GuideStage.POUR, GuideStage.BREWING_FRENCH_PRESS).forEach {
            assertEquals(4, milestoneIndex(press, it))
        }
        assertEquals(5, milestoneIndex(press, GuideStage.SERVE))
        assertEquals(6, milestoneIndex(press, GuideStage.DONE))
        guideStages(BrewMode.FRENCH_PRESS).forEach { stage ->
            assertEquals("$stage belongs to one press milestone", 1, press.count { stage in it.stages })
        }
    }

    @Test
    fun frenchPressServesOnlyOnceTheStepsAreDone() {
        val brewing = io.github.mateuxlucax.caparao.brew.BrewPhase.BREWING
        assertEquals(GuideStage.BREWING_FRENCH_PRESS, guideStage(BrewMode.FRENCH_PRESS, BrewStep.LIVE, brewing))
        assertEquals(GuideStage.SERVE, guideStage(BrewMode.FRENCH_PRESS, BrewStep.LIVE, brewing, serving = true))
        assertEquals(GuideStage.BREWING_FILTER, guideStage(BrewMode.FILTER, BrewStep.LIVE, brewing, serving = true))
    }

    @Test
    fun filterPreparationIsCountedBetweenBeansAndTareAndFrenchPressHasItsOwn() {
        assertEquals(
            listOf(GuideStage.DOSE, GuideStage.COFFEE, GuideStage.BEANS, GuideStage.PREPARE_FILTER, GuideStage.TARE, GuideStage.POUR, GuideStage.BREWING_FILTER, GuideStage.DONE),
            guideStages(BrewMode.FILTER),
        )
        assertEquals(GuideStage.PREPARE_FILTER, guideStage(BrewMode.FILTER, BrewStep.PREPARE_PORTAFILTER, io.github.mateuxlucax.caparao.brew.BrewPhase.WAITING_FOR_CUP))
        assertEquals(
            listOf(GuideStage.DOSE, GuideStage.COFFEE, GuideStage.BEANS, GuideStage.PREPARE_FRENCH_PRESS, GuideStage.TARE, GuideStage.POUR, GuideStage.BREWING_FRENCH_PRESS, GuideStage.SERVE, GuideStage.DONE),
            guideStages(BrewMode.FRENCH_PRESS),
        )
        assertEquals(GuideStage.PREPARE_FRENCH_PRESS, guideStage(BrewMode.FRENCH_PRESS, BrewStep.PREPARE_PORTAFILTER, io.github.mateuxlucax.caparao.brew.BrewPhase.WAITING_FOR_CUP))
        assertEquals(GuideStage.PREPARE_PORTAFILTER, guideStage(BrewMode.ESPRESSO, BrewStep.PREPARE_PORTAFILTER, io.github.mateuxlucax.caparao.brew.BrewPhase.WAITING_FOR_CUP))
    }
}
