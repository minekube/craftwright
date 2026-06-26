package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.protocol.NavigationTaskRequest
import com.minekube.craftless.protocol.NavigationTaskState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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

        assertEquals(NavigationTaskState.SUCCEEDED, result.state)
        assertEquals(result.id, executor.status(result.id).id)
        assertTrue(executor.events().any { it.type == "task.observe" })
        assertTrue(executor.events().none { it.toString().contains("baritone", ignoreCase = true) })
    }

    @Test
    fun `honest cow hunt fails honestly when no material source is observed`() {
        val executor =
            RecordingSurvivalExecutor(
                observations =
                    StaticSurvivalObservationProvider(
                        FabricSurvivalObservation(
                            materialSources = emptyList(),
                            passiveEntities = listOf(cow()),
                        ),
                    ),
                pathfinderBackend = AvailableSurvivalPathfinderBackend,
            )

        val result = executor.run(NavigationTaskRequest(task = "task.survival.honest-cow-hunt"))

        assertEquals(NavigationTaskState.FAILED, result.state)
        assertEquals("no-material-source", result.message)
    }

    @Test
    fun `honest cow hunt fails honestly when no cow is observed`() {
        val executor =
            RecordingSurvivalExecutor(
                observations =
                    StaticSurvivalObservationProvider(
                        FabricSurvivalObservation(
                            materialSources = listOf(materialSource()),
                            passiveEntities = emptyList(),
                        ),
                    ),
                pathfinderBackend = AvailableSurvivalPathfinderBackend,
            )

        val result = executor.run(NavigationTaskRequest(task = "task.survival.honest-cow-hunt"))

        assertEquals(NavigationTaskState.FAILED, result.state)
        assertEquals("no-cow-observed", result.message)
    }

    @Test
    fun `honest cow hunt fails honestly when pathfinder is unavailable`() {
        val executor =
            RecordingSurvivalExecutor(
                observations =
                    StaticSurvivalObservationProvider(
                        FabricSurvivalObservation(
                            materialSources = listOf(materialSource()),
                            passiveEntities = listOf(cow()),
                        ),
                    ),
                pathfinderBackend = UnavailableFabricPathfinderBackend,
            )

        val result = executor.run(NavigationTaskRequest(task = "task.survival.honest-cow-hunt"))

        assertEquals(NavigationTaskState.FAILED, result.state)
        assertEquals("pathfinder-unavailable", result.message)
    }

    @Test
    fun `honest cow hunt records Craftless progress events without backend names`() {
        val executor =
            RecordingSurvivalExecutor(
                observations =
                    StaticSurvivalObservationProvider(
                        FabricSurvivalObservation(
                            materialSources = listOf(materialSource()),
                            passiveEntities = listOf(cow()),
                            inventory = FabricSurvivalInventory(hasWeapon = false),
                        ),
                    ),
                pathfinderBackend = AvailableSurvivalPathfinderBackend,
                nextTaskId = { "task:survival:honest-cow-hunt:0001" },
            )

        val result = executor.run(NavigationTaskRequest(task = "task.survival.honest-cow-hunt"))

        assertEquals(NavigationTaskState.SUCCEEDED, result.state)
        assertEquals("cow-hunt-complete", result.message)
        assertNotNull(executor.status("task:survival:honest-cow-hunt:0001"))
        assertEquals(
            listOf("task.observe", "task.inventory", "task.navigate"),
            executor.events().map { it.type },
        )
        assertTrue(executor.events().none { it.toString().contains("baritone", ignoreCase = true) })
        assertTrue(executor.events().none { it.toString().contains("swarmbot", ignoreCase = true) })
    }

    @Test
    fun `honest cow hunt executes material crafting and combat ports in order`() {
        val calls = mutableListOf<String>()
        val executor =
            RecordingSurvivalExecutor(
                observations =
                    RecordingSurvivalObservationProvider(
                        calls = calls,
                        observation =
                            FabricSurvivalObservation(
                                materialSources = listOf(materialSource()),
                                passiveEntities = listOf(cow()),
                                inventory = FabricSurvivalInventory(hasWeapon = false),
                            ),
                    ),
                pathfinderBackend = SequencedSurvivalPathfinderBackend(calls),
                executionPorts = RecordingSurvivalExecutionPorts(calls),
                nextTaskId = { "task:survival:honest-cow-hunt:0001" },
            )

        val result = executor.run(NavigationTaskRequest(task = "task.survival.honest-cow-hunt"))

        assertEquals(NavigationTaskState.SUCCEEDED, result.state)
        assertEquals("cow-hunt-complete", result.message)
        assertEquals(
            listOf(
                "observe",
                "plan-material",
                "follow-material",
                "break-material",
                "craft-weapon",
                "equip-weapon",
                "plan-cow",
                "follow-cow",
                "attack-cow",
            ),
            calls,
        )
    }
}

private fun materialSource(): FabricSurvivalMaterialSource =
    FabricSurvivalMaterialSource(
        handle = "resource:survival:material:log:0001",
        position = FabricSurvivalBlockPosition(x = 1, y = 64, z = 1),
    )

private fun cow(): FabricSurvivalEntity =
    FabricSurvivalEntity(
        handle = "resource:survival:entity:cow:0001",
        kind = "cow",
        position = FabricSurvivalBlockPosition(x = 4, y = 64, z = 4),
    )

private object AvailableSurvivalPathfinderBackend : FabricPathfinderBackend {
    override fun available(): Boolean = true

    override fun plan(goal: com.minekube.craftless.protocol.NavigationGoal): FabricPathfinderPlan =
        FabricPathfinderPlan(
            id = "navigation.plan.observation.0001",
            goal = goal,
            status =
                com.minekube.craftless.protocol.NavigationTaskStatus(
                    id = "task:navigation:observation:0001",
                    state = NavigationTaskState.PENDING,
                    message = "planned",
                ),
        )

    override fun follow(planId: String): com.minekube.craftless.protocol.NavigationTaskStatus =
        com.minekube.craftless.protocol.NavigationTaskStatus(
            id = "task:navigation:observation:0001",
            state = NavigationTaskState.RUNNING,
            message = "following",
        )

    override fun stop(): com.minekube.craftless.protocol.NavigationTaskStatus = error("not used by observation tests")

    override fun events(): List<com.minekube.craftless.protocol.NavigationProgressEvent> = emptyList()
}

private class RecordingSurvivalObservationProvider(
    private val calls: MutableList<String>,
    private val observation: FabricSurvivalObservation,
) : FabricSurvivalObservationProvider {
    override fun observe(): FabricSurvivalObservation {
        calls += "observe"
        return observation
    }
}

private class SequencedSurvivalPathfinderBackend(
    private val calls: MutableList<String>,
) : FabricPathfinderBackend {
    private var plans = 0

    override fun available(): Boolean = true

    override fun plan(goal: com.minekube.craftless.protocol.NavigationGoal): FabricPathfinderPlan {
        plans += 1
        val label = if (plans == 1) "material" else "cow"
        calls += "plan-$label"
        return FabricPathfinderPlan(
            id = "navigation.plan.$label.0001",
            goal = goal,
            status =
                com.minekube.craftless.protocol.NavigationTaskStatus(
                    id = "task:navigation:$label:0001",
                    state = NavigationTaskState.PENDING,
                    message = "planned",
                ),
        )
    }

    override fun follow(planId: String): com.minekube.craftless.protocol.NavigationTaskStatus {
        val label = if (planId.contains("material")) "material" else "cow"
        calls += "follow-$label"
        return com.minekube.craftless.protocol.NavigationTaskStatus(
            id = "task:navigation:$label:0001",
            state = NavigationTaskState.RUNNING,
            message = "following",
        )
    }

    override fun stop(): com.minekube.craftless.protocol.NavigationTaskStatus = error("not used by survival execution tests")

    override fun events(): List<com.minekube.craftless.protocol.NavigationProgressEvent> = emptyList()
}

private class RecordingSurvivalExecutionPorts(
    private val calls: MutableList<String>,
) : FabricSurvivalExecutionPorts {
    override fun breakMaterial(target: FabricSurvivalMaterialSource): com.minekube.craftless.protocol.NavigationTaskStatus {
        calls += "break-material"
        return succeeded("break-material")
    }

    override fun craftWeapon(): com.minekube.craftless.protocol.NavigationTaskStatus {
        calls += "craft-weapon"
        return succeeded("craft-weapon")
    }

    override fun equipWeapon(): com.minekube.craftless.protocol.NavigationTaskStatus {
        calls += "equip-weapon"
        return succeeded("equip-weapon")
    }

    override fun attackEntity(target: FabricSurvivalEntity): com.minekube.craftless.protocol.NavigationTaskStatus {
        calls += "attack-cow"
        return succeeded("attack-cow")
    }

    private fun succeeded(action: String): com.minekube.craftless.protocol.NavigationTaskStatus =
        com.minekube.craftless.protocol.NavigationTaskStatus(
            id = "task:survival:$action:0001",
            state = NavigationTaskState.SUCCEEDED,
            message = "$action-complete",
        )
}
