package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.protocol.NavigationProgressEvent
import com.minekube.craftless.protocol.NavigationTaskRequest
import com.minekube.craftless.protocol.NavigationTaskState
import com.minekube.craftless.protocol.NavigationTaskStatus
import java.util.concurrent.atomic.AtomicInteger

internal interface FabricSurvivalTaskExecutor {
    fun run(request: NavigationTaskRequest): NavigationTaskStatus

    fun status(taskId: String): NavigationTaskStatus

    fun events(): List<NavigationProgressEvent>
}

internal class RecordingSurvivalExecutor(
    private val nextTaskId: () -> String = ::nextSurvivalTaskId,
) : FabricSurvivalTaskExecutor {
    private val statuses = linkedMapOf<String, NavigationTaskStatus>()
    private val progressEvents = mutableListOf<NavigationProgressEvent>()

    override fun run(request: NavigationTaskRequest): NavigationTaskStatus =
        when (request.task) {
            HONEST_COW_HUNT -> startHonestCowHunt()
            else -> unsupportedTaskStatus()
        }

    override fun status(taskId: String): NavigationTaskStatus =
        statuses[taskId]
            ?: NavigationTaskStatus(
                id = taskId,
                state = NavigationTaskState.FAILED,
                message = "unknown-task",
            )

    override fun events(): List<NavigationProgressEvent> = progressEvents.toList()

    private fun startHonestCowHunt(): NavigationTaskStatus {
        val taskId = nextTaskId()
        val status =
            NavigationTaskStatus(
                id = taskId,
                state = NavigationTaskState.RUNNING,
                message = "observing",
            )
        statuses[taskId] = status
        progressEvents +=
            NavigationProgressEvent(
                taskId = taskId,
                type = "task.observe",
                message = "observing-survival-state",
            )
        return status
    }

    private fun unsupportedTaskStatus(): NavigationTaskStatus =
        NavigationTaskStatus(
            id = "task:survival:unsupported:${taskIdCounter.incrementAndGet()}",
            state = NavigationTaskState.FAILED,
            message = "unsupported-task",
        )
}

private fun nextSurvivalTaskId(): String = "task:survival:honest-cow-hunt:%04d".format(taskIdCounter.incrementAndGet())

private const val HONEST_COW_HUNT = "task.survival.honest-cow-hunt"

private val taskIdCounter = AtomicInteger()
