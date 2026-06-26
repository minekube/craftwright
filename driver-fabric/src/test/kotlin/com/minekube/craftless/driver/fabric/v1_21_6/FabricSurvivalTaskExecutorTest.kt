package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.protocol.NavigationTaskRequest
import com.minekube.craftless.protocol.NavigationTaskState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FabricSurvivalTaskExecutorTest {
    @Test
    fun `executor rejects unknown tasks without static shortcut actions`() {
        val executor = RecordingSurvivalExecutor()

        val result = executor.run(NavigationTaskRequest(task = "task.survival.unknown"))

        assertEquals(NavigationTaskState.FAILED, result.state)
        assertEquals("unsupported-task", result.message)
        assertFalse(result.toString().contains("kill.cow", ignoreCase = true))
        assertFalse(result.toString().contains("craft.sword", ignoreCase = true))
    }

    @Test
    fun `honest cow hunt starts with observable task status and progress`() {
        val executor = RecordingSurvivalExecutor()

        val result = executor.run(NavigationTaskRequest(task = "task.survival.honest-cow-hunt"))

        assertEquals(NavigationTaskState.RUNNING, result.state)
        assertEquals(result.id, executor.status(result.id).id)
        assertTrue(executor.events().any { it.type == "task.observe" })
        assertTrue(executor.events().none { it.toString().contains("baritone", ignoreCase = true) })
    }
}
