package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.protocol.NavigationProgressEvent
import com.minekube.craftless.protocol.NavigationTaskRequest
import com.minekube.craftless.protocol.NavigationTaskState
import com.minekube.craftless.protocol.NavigationTaskStatus

internal interface FabricTaskExecutor {
    fun run(request: NavigationTaskRequest): NavigationTaskStatus

    fun status(taskId: String): NavigationTaskStatus

    fun events(): List<NavigationProgressEvent>
}

internal object UnavailableFabricTaskExecutor : FabricTaskExecutor {
    override fun run(request: NavigationTaskRequest): NavigationTaskStatus =
        NavigationTaskStatus(
            id = "task:unavailable",
            state = NavigationTaskState.FAILED,
            message = "task-executor-unavailable",
        )

    override fun status(taskId: String): NavigationTaskStatus =
        NavigationTaskStatus(
            id = taskId,
            state = NavigationTaskState.FAILED,
            message = "unknown-task",
        )

    override fun events(): List<NavigationProgressEvent> = emptyList()
}
