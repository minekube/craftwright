package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.protocol.NavigationGoal
import com.minekube.craftless.protocol.NavigationTaskState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReflectiveFabricPathfinderBackendTest {
    @Test
    fun `probe is available only when every internal pathfinder handle exists`() {
        val complete = RecordingPathfinderProbe()

        assertTrue(ReflectiveFabricPathfinderBackend(probe = complete).available())

        assertFalse(ReflectiveFabricPathfinderBackend(probe = complete.copy(provider = null)).available())
        assertFalse(ReflectiveFabricPathfinderBackend(probe = complete.copy(primaryClient = null)).available())
        assertFalse(ReflectiveFabricPathfinderBackend(probe = complete.copy(customGoalProcess = null)).available())
        assertFalse(ReflectiveFabricPathfinderBackend(probe = complete.copy(goalFactory = null)).available())
        assertFalse(ReflectiveFabricPathfinderBackend(probe = complete.copy(startGoal = null)).available())
        assertFalse(ReflectiveFabricPathfinderBackend(probe = complete.copy(stopNavigation = null)).available())
    }

    @Test
    fun `reflection backend plans follows stops and keeps public evidence craftless owned`() {
        val probe = RecordingPathfinderProbe()
        val backend =
            ReflectiveFabricPathfinderBackend(
                probe = probe,
                nextPlanId = { "navigation.plan.reflective.0001" },
            )
        val goal =
            NavigationGoal(
                kind = "block",
                position = mapOf("x" to 10.0, "y" to 65.0, "z" to -4.0),
            )

        val plan = backend.plan(goal)
        val follow = backend.follow(plan.id)
        val stop = backend.stop()
        val publicEvidence = (listOf(plan.status, follow, stop) + backend.events()).joinToString()

        assertEquals("navigation.plan.reflective.0001", plan.id)
        assertEquals(NavigationTaskState.PENDING, plan.status.state)
        assertEquals(NavigationTaskState.SUCCEEDED, follow.state)
        assertEquals(NavigationTaskState.CANCELLED, stop.state)
        assertEquals(listOf("goal:10:65:-4", "start:goal:10:65:-4", "stop"), probe.calls)
        assertFalse(publicEvidence.contains("baritone", ignoreCase = true))
        assertFalse(publicEvidence.contains("swarmbot", ignoreCase = true))
        assertFalse(publicEvidence.contains("net.minecraft", ignoreCase = true))
    }

    @Test
    fun `reflection backend fails follow when path completion cannot be observed`() {
        val backend =
            ReflectiveFabricPathfinderBackend(
                probe = RecordingPathfinderProbe(pathingActive = null),
                nextPlanId = { "navigation.plan.reflective.0001" },
            )
        val goal =
            NavigationGoal(
                kind = "block",
                position = mapOf("x" to 10.0, "y" to 65.0, "z" to -4.0),
            )

        val plan = backend.plan(goal)
        val follow = backend.follow(plan.id)

        assertEquals(NavigationTaskState.FAILED, follow.state)
        assertEquals("navigation-completion-unobservable", follow.message)
    }

    @Test
    fun `reflection backend reports unavailable without leaking private class names`() {
        val backend = ReflectiveFabricPathfinderBackend(probe = RecordingPathfinderProbe(provider = null))
        val goal =
            NavigationGoal(
                kind = "block",
                position = mapOf("x" to 1.0, "y" to 64.0, "z" to 1.0),
            )

        val plan = backend.plan(goal)
        val publicEvidence = (listOf(plan.status) + backend.events()).joinToString()

        assertEquals(NavigationTaskState.FAILED, plan.status.state)
        assertEquals("pathfinder-probe-unavailable", plan.status.message)
        assertFalse(publicEvidence.contains("baritone", ignoreCase = true))
        assertFalse(publicEvidence.contains("swarmbot", ignoreCase = true))
        assertFalse(publicEvidence.contains("net.minecraft", ignoreCase = true))
    }
}

private data class RecordingPathfinderProbe(
    val calls: MutableList<String> = mutableListOf(),
    val provider: Any? = Any(),
    val primaryClient: Any? = Any(),
    val customGoalProcess: Any? = Any(),
    val goalFactory: ((Int, Int, Int) -> Any)? = { x, y, z ->
        "goal:$x:$y:$z".also { calls += it }
    },
    val startGoal: ((Any, Any) -> Unit)? = { _, goal ->
        calls += "start:$goal"
    },
    val stopNavigation: ((Any) -> Unit)? = {
        calls += "stop"
    },
    val pathingActive: (() -> Boolean)? = alternatingPathingState(),
) : ReflectiveFabricPathfinderProbe {
    override fun inspect(): ReflectiveFabricPathfinderHandles =
        ReflectiveFabricPathfinderHandles(
            provider = provider,
            primaryClient = primaryClient,
            customGoalProcess = customGoalProcess,
            goalFactory = goalFactory,
            startGoal = startGoal,
            stopNavigation = stopNavigation,
            pathingActive = pathingActive,
        )
}

private fun alternatingPathingState(): () -> Boolean {
    var calls = 0
    return {
        calls += 1
        calls == 1
    }
}
