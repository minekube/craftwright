package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.protocol.NavigationGoal
import com.minekube.craftless.protocol.NavigationProgressEvent
import com.minekube.craftless.protocol.NavigationTaskState
import com.minekube.craftless.protocol.NavigationTaskStatus
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicInteger

internal class ReflectiveFabricPathfinderBackend(
    private val gateway: FabricClientGateway? = null,
    private val probe: ReflectiveFabricPathfinderProbe = ClassLoaderReflectiveFabricPathfinderProbe(),
    private val nextPlanId: () -> String = ::nextReflectivePathfinderPlanId,
) : FabricPathfinderBackend {
    private val events = mutableListOf<NavigationProgressEvent>()
    private val plans = mutableMapOf<String, ReflectivePathfinderPlan>()

    override fun available(): Boolean = probe.inspect().available

    override fun plan(goal: NavigationGoal): FabricPathfinderPlan {
        val handles = probe.inspect()
        if (!handles.available) {
            return unavailablePlan(goal)
        }
        val position =
            goal.blockPosition()
                ?: return failedPlan(goal, message = "unsupported-navigation-goal")
        val planId = nextPlanId()
        val taskId = "task:navigation:$planId"
        val goalObject =
            queryOnClient {
                handles.goalFactory?.invoke(position.x, position.y, position.z)
            } ?: return unavailablePlan(goal)
        plans[planId] = ReflectivePathfinderPlan(taskId = taskId, goalObject = goalObject)
        val status =
            NavigationTaskStatus(
                id = taskId,
                state = NavigationTaskState.PENDING,
                message = "planned",
            )
        record(status, "navigation.plan")
        return FabricPathfinderPlan(
            id = planId,
            goal = goal,
            status = status,
        )
    }

    override fun follow(planId: String): NavigationTaskStatus {
        val handles = probe.inspect()
        if (!handles.available) {
            return unavailableStatus()
        }
        val customGoalProcess = handles.customGoalProcess ?: return unavailableStatus()
        val startGoal = handles.startGoal ?: return unavailableStatus()
        val plan =
            plans[planId]
                ?: return NavigationTaskStatus(
                    id = "task:navigation:missing-plan",
                    state = NavigationTaskState.FAILED,
                    message = "missing-plan",
                )
        queryOnClient {
            startGoal(customGoalProcess, plan.goalObject)
        }
        val pathingActive = handles.pathingActive
        if (pathingActive == null) {
            val status =
                NavigationTaskStatus(
                    id = plan.taskId,
                    state = NavigationTaskState.FAILED,
                    message = "navigation-completion-unobservable",
                )
            record(status, "navigation.follow")
            return status
        }
        val completed = waitForPathCompletion(pathingActive)
        val status =
            NavigationTaskStatus(
                id = plan.taskId,
                state =
                    when (completed) {
                        false -> NavigationTaskState.FAILED
                        true -> NavigationTaskState.SUCCEEDED
                    },
                message = if (completed == false) "navigation-timeout" else "following",
            )
        record(status, "navigation.follow")
        return status
    }

    override fun stop(): NavigationTaskStatus {
        val handles = probe.inspect()
        if (!handles.available) {
            return unavailableStatus()
        }
        val customGoalProcess = handles.customGoalProcess ?: return unavailableStatus()
        val stopNavigation = handles.stopNavigation ?: return unavailableStatus()
        queryOnClient {
            stopNavigation(customGoalProcess)
        }
        val taskId = plans.values.lastOrNull()?.taskId ?: "task:navigation:stop"
        val status =
            NavigationTaskStatus(
                id = taskId,
                state = NavigationTaskState.CANCELLED,
                message = "stopped",
            )
        record(status, "navigation.stop")
        return status
    }

    override fun events(): List<NavigationProgressEvent> = events.toList()

    private fun unavailablePlan(goal: NavigationGoal): FabricPathfinderPlan =
        failedPlan(goal = goal, message = PATHFINDER_PROBE_UNAVAILABLE)

    private fun failedPlan(
        goal: NavigationGoal,
        message: String,
    ): FabricPathfinderPlan {
        val status =
            NavigationTaskStatus(
                id = "task:navigation:unavailable",
                state = NavigationTaskState.FAILED,
                message = message,
            )
        record(status, "navigation.plan")
        return FabricPathfinderPlan(
            id = "navigation.plan.unavailable.0001",
            goal = goal,
            status = status,
        )
    }

    private fun unavailableStatus(): NavigationTaskStatus =
        NavigationTaskStatus(
            id = "task:navigation:unavailable",
            state = NavigationTaskState.FAILED,
            message = PATHFINDER_PROBE_UNAVAILABLE,
        )

    private fun record(
        status: NavigationTaskStatus,
        type: String,
    ) {
        events +=
            NavigationProgressEvent(
                taskId = status.id,
                type = type,
                message = status.message ?: type,
            )
    }

    private fun <T> queryOnClient(query: () -> T): T =
        if (gateway == null) {
            query()
        } else {
            gateway.queryOnClient { query() }
        }

    private fun waitForPathCompletion(pathingActive: () -> Boolean): Boolean {
        waitUntil(timeoutMillis = PATHING_START_TIMEOUT_MS) {
            queryOnClient { pathingActive() }
        }
        return waitUntil(timeoutMillis = PATHING_COMPLETION_TIMEOUT_MS) {
            queryOnClient { !pathingActive() }
        }
    }

    private fun waitUntil(
        timeoutMillis: Long,
        pollMillis: Long = PATHING_POLL_INTERVAL_MS,
        condition: () -> Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return true
            }
            Thread.sleep(pollMillis)
        }
        return condition()
    }
}

internal fun interface ReflectiveFabricPathfinderProbe {
    fun inspect(): ReflectiveFabricPathfinderHandles
}

internal data class ReflectiveFabricPathfinderHandles(
    val provider: Any?,
    val primaryClient: Any?,
    val customGoalProcess: Any?,
    val goalFactory: ((Int, Int, Int) -> Any)?,
    val startGoal: ((Any, Any) -> Unit)?,
    val stopNavigation: ((Any) -> Unit)?,
    val pathingActive: (() -> Boolean)? = null,
) {
    val available: Boolean
        get() =
            provider != null &&
                primaryClient != null &&
                customGoalProcess != null &&
                goalFactory != null &&
                startGoal != null &&
                stopNavigation != null
}

internal class ClassLoaderReflectiveFabricPathfinderProbe(
    private val classLoader: ClassLoader = ReflectiveFabricPathfinderBackend::class.java.classLoader,
) : ReflectiveFabricPathfinderProbe {
    override fun inspect(): ReflectiveFabricPathfinderHandles {
        val apiClass = classOrNull(PATHFINDER_API_CLASS)
        val provider = apiClass?.methodOrNull("getProvider", parameterCount = 0)?.invoke(null)
        val primaryClient = provider?.methodOrNull("getPrimaryBaritone", parameterCount = 0)?.invoke(provider)
        val customGoalProcess = primaryClient?.methodOrNull("getCustomGoalProcess", parameterCount = 0)?.invoke(primaryClient)
        val pathingBehavior = primaryClient?.methodOrNull("getPathingBehavior", parameterCount = 0)?.invoke(primaryClient)
        val goalConstructor =
            classOrNull(PATHFINDER_BLOCK_GOAL_CLASS)
                ?.constructors
                ?.firstOrNull { constructor ->
                    constructor.parameterTypes.toList() == listOf(Integer.TYPE, Integer.TYPE, Integer.TYPE)
                }
        val startMethod = customGoalProcess?.methodOrNull("setGoalAndPath", parameterCount = 1)
        val stopMethod = pathingBehavior?.methodOrNull("cancelEverything", parameterCount = 0)
        val pathingMethod = pathingBehavior?.methodOrNull("isPathing", parameterCount = 0)
        return ReflectiveFabricPathfinderHandles(
            provider = provider,
            primaryClient = primaryClient,
            customGoalProcess = customGoalProcess,
            goalFactory =
                goalConstructor?.let { constructor ->
                    { x: Int, y: Int, z: Int -> constructor.newInstance(x, y, z) }
                },
            startGoal =
                startMethod?.let { method ->
                    { process: Any, goal: Any -> method.invoke(process, goal) }
                },
            stopNavigation =
                stopMethod?.let { method ->
                    { _: Any -> method.invoke(pathingBehavior) }
                },
            pathingActive =
                pathingMethod?.let { method ->
                    { method.invoke(pathingBehavior) as Boolean }
                },
        )
    }

    private fun classOrNull(name: String): Class<*>? = runCatching { Class.forName(name, false, classLoader) }.getOrNull()
}

private data class ReflectivePathfinderPlan(
    val taskId: String,
    val goalObject: Any,
)

private data class BlockPosition(
    val x: Int,
    val y: Int,
    val z: Int,
)

private fun NavigationGoal.blockPosition(): BlockPosition? {
    if (kind != "block") {
        return null
    }
    val x = position["x"]?.toInt() ?: return null
    val y = position["y"]?.toInt() ?: return null
    val z = position["z"]?.toInt() ?: return null
    return BlockPosition(x = x, y = y, z = z)
}

private fun Any.methodOrNull(
    name: String,
    parameterCount: Int,
): Method? = javaClass.methods.firstOrNull { it.name == name && it.parameterCount == parameterCount }

private fun Class<*>.methodOrNull(
    name: String,
    parameterCount: Int,
): Method? = methods.firstOrNull { it.name == name && it.parameterCount == parameterCount }

private fun nextReflectivePathfinderPlanId(): String = "navigation.plan.reflective.%04d".format(reflectivePlanCounter.incrementAndGet())

private val reflectivePlanCounter = AtomicInteger()

internal const val PATHFINDER_PROBE_UNAVAILABLE = "pathfinder-probe-unavailable"
private const val PATHING_START_TIMEOUT_MS = 2_000L
private const val PATHING_COMPLETION_TIMEOUT_MS = 45_000L

private const val PATHFINDER_API_CLASS = "baritone.api.BaritoneAPI"
private const val PATHFINDER_BLOCK_GOAL_CLASS = "baritone.api.pathing.goals.GoalBlock"
private const val PATHING_POLL_INTERVAL_MS = 50L
