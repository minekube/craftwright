package com.minekube.craftless.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

@Serializable
data class NavigationGoal(
    val kind: String,
    val position: Map<String, Double> = emptyMap(),
    val radius: Double? = null,
) {
    init {
        require(kind.isCraftlessActionArgumentName()) { "invalid navigation goal kind $kind" }
        position.keys.forEach { key ->
            require(key in NAVIGATION_POSITION_KEYS) { "invalid navigation goal position key $key" }
        }
        radius?.let { require(it >= 0.0) { "navigation goal radius must not be negative" } }
    }
}

@Serializable
data class NavigationEngineDescriptor(
    val id: String,
    val displayName: String,
    val availability: String = NavigationAvailability.AVAILABLE,
    val reasons: List<String> = emptyList(),
) {
    init {
        require(id.isCraftlessActionId()) { "invalid navigation engine id $id" }
        require(id.startsWith("navigation.")) { "navigation engine id must be Craftless-owned" }
        require(!id.hasRawNavigationBackendName()) { "navigation engine id must not expose backend names" }
        require(displayName.isNotBlank()) { "navigation engine display name is required" }
        require(!displayName.hasRawNavigationBackendName()) {
            "navigation engine display name must not expose backend names"
        }
        require(availability in NavigationAvailability.allowed) { "invalid navigation availability $availability" }
        reasons.forEach { reason ->
            require(reason.isCraftlessActionArgumentName()) { "invalid navigation availability reason $reason" }
        }
    }
}

@Serializable
data class NavigationRouteSegment(
    val index: Int,
    val from: NavigationGoal,
    val to: NavigationGoal,
    val cost: Double,
    val operations: List<String> = emptyList(),
) {
    init {
        require(index >= 0) { "navigation route segment index must not be negative" }
        require(cost >= 0.0) { "navigation route segment cost must not be negative" }
        operations.forEach { operation ->
            require(operation.isCraftlessActionId()) { "invalid navigation route operation $operation" }
            require(!operation.hasRawNavigationBackendName()) { "navigation route operation must not expose backend names" }
        }
    }
}

@Serializable
data class NavigationPlan(
    val id: String,
    val goal: NavigationGoal,
    val engine: String,
    val segments: List<NavigationRouteSegment>,
) {
    init {
        require(id.isCraftlessLiveId()) { "invalid navigation plan id $id" }
        require(id.startsWith("navigation.plan.")) { "navigation plan id must be Craftless-owned" }
        require(engine.isCraftlessActionId()) { "invalid navigation plan engine $engine" }
        require(engine.startsWith("navigation.")) { "navigation plan engine must be Craftless-owned" }
        require(!engine.hasRawNavigationBackendName()) { "navigation plan engine must not expose backend names" }
    }
}

@Serializable
data class NavigationTaskRequest(
    val task: String,
    val args: Map<String, JsonElement> = emptyMap(),
) {
    init {
        require(task.isCraftlessActionId()) { "invalid navigation task id $task" }
        require(task.startsWith("task.")) { "navigation task id must be Craftless-owned" }
        require(!task.hasRawNavigationBackendName()) { "navigation task id must not expose backend names" }
        args.keys.forEach { key ->
            require(key.isCraftlessActionArgumentName()) { "invalid navigation task argument $key" }
        }
    }
}

@Serializable
data class NavigationTaskStatus(
    val id: String,
    val state: String,
    val message: String? = null,
    val data: JsonObject = buildJsonObject {},
) {
    init {
        require(id.isCraftlessLiveId()) { "invalid navigation task status id $id" }
        require(state in NavigationTaskState.allowed) { "invalid navigation task state $state" }
        require(message == null || message.isNotBlank()) { "navigation task status message must not be blank" }
    }
}

@Serializable
data class NavigationProgressEvent(
    val taskId: String,
    val type: String,
    val message: String,
    val payload: Map<String, JsonElement> = emptyMap(),
) {
    init {
        require(taskId.isCraftlessLiveId()) { "invalid navigation progress task id $taskId" }
        require(type.isCraftlessActionId()) { "invalid navigation progress event type $type" }
        require(type.startsWith("task.") || type.startsWith("navigation.")) {
            "navigation progress event type must be Craftless-owned"
        }
        require(!type.hasRawNavigationBackendName()) { "navigation progress event type must not expose backend names" }
        require(message.isNotBlank()) { "navigation progress event message is required" }
        payload.keys.forEach { key ->
            require(key.isCraftlessActionArgumentName()) { "invalid navigation progress payload key $key" }
        }
    }
}

object NavigationAvailability {
    const val AVAILABLE = "available"
    const val UNAVAILABLE = "unavailable"

    val allowed: Set<String> = setOf(AVAILABLE, UNAVAILABLE)
}

object NavigationTaskState {
    const val PENDING = "pending"
    const val RUNNING = "running"
    const val SUCCEEDED = "succeeded"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"

    val allowed: Set<String> = setOf(PENDING, RUNNING, SUCCEEDED, FAILED, CANCELLED)
}

private val NAVIGATION_POSITION_KEYS = setOf("x", "y", "z", "yaw", "pitch")

private fun String.hasRawNavigationBackendName(): Boolean =
    contains("baritone", ignoreCase = true) ||
        contains("swarmbot", ignoreCase = true) ||
        contains("minecraft", ignoreCase = true)

private fun String.isCraftlessLiveId(): Boolean = matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}"))
