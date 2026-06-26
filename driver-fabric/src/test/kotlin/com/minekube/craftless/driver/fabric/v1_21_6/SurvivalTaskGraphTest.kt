package com.minekube.craftless.driver.fabric.v1_21_6

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SurvivalTaskGraphTest {
    @Test
    fun `honest cow hunt task graph requires observations and legitimate actions`() {
        val graph = SurvivalTaskGraph.honestCowHunt()
        val stepIds = graph.steps.map { it.id }
        val allText = graph.toString()

        assertEquals("observe.logs", graph.steps.first().id)
        assertTrue("navigation.plan" in stepIds)
        assertTrue("inventory.query" in stepIds)
        assertTrue("craft.weapon" in stepIds)
        assertTrue("observe.entity" in stepIds)
        assertTrue("combat.attack-entity" in stepIds)
        assertFalse(allText.contains("give", ignoreCase = true))
        assertFalse(allText.contains("kill.cow", ignoreCase = true))
        assertFalse(allText.contains("server-side item", ignoreCase = true))
    }
}
