package com.minekube.craftless.driver.runtime

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverClientSnapshot
import com.minekube.craftless.driver.api.DriverEvent
import com.minekube.craftless.driver.api.DriverEventType
import com.minekube.craftless.driver.api.DriverOperationAdapters
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.api.DriverSession
import com.minekube.craftless.protocol.ClientState
import com.minekube.craftless.protocol.RuntimeCapabilityGraph

class BackendDriverSession(
    override val clientId: String,
    private val backend: DriverBackend,
) : DriverSession {
    private var state = ClientState.RUNNING
    private val events =
        mutableListOf(
            DriverEvent(
                type = DriverEventType.CLIENT_CREATED,
                client = clientId,
                message = "created client $clientId",
            ),
        )

    override fun snapshot(): DriverClientSnapshot = DriverClientSnapshot(id = clientId, state = state)

    override fun connect(target: ConnectionTarget): DriverClientSnapshot {
        require(target.host.isNotBlank()) { "connection host is required" }
        require(target.port in 1..65535) { "connection port must be between 1 and 65535" }
        val result = backend.connect(clientId, target)
        require(result.action == DriverBackendAction.CONNECT) { "backend returned ${result.action} for connect" }
        if (result.observed) {
            state = ClientState.CONNECTED
            events +=
                DriverEvent(
                    type = DriverEventType.CLIENT_CONNECTED,
                    client = clientId,
                    message = result.message ?: "connected $clientId to ${target.host}:${target.port}",
                )
        }
        return snapshot()
    }

    override fun actions(): List<DriverActionDescriptor> = backend.actions(clientId)

    override fun runtimeMetadata(): DriverRuntimeMetadata = backend.runtimeMetadata(clientId)

    override fun runtimeGraph(): RuntimeCapabilityGraph = backend.runtimeGraph(clientId)

    override fun operationAdapters(): DriverOperationAdapters = backend.operationAdapters(clientId)

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult {
        require(invocation.action.isNotBlank()) { "action is required" }
        val result = backend.invoke(clientId, invocation)
        result.toDriverEvent(clientId)?.let { events += it }
        return result
    }

    override fun stop(): DriverClientSnapshot {
        val result = backend.stop(clientId)
        require(result.action == DriverBackendAction.STOP) { "backend returned ${result.action} for stop" }
        state = ClientState.STOPPED
        events +=
            DriverEvent(
                type = DriverEventType.CLIENT_STOPPED,
                client = clientId,
                message = result.message ?: "stopped client $clientId",
            )
        return snapshot()
    }

    override fun events(): List<DriverEvent> = events.toList()
}

interface DriverBackend {
    fun connect(
        clientId: String,
        target: ConnectionTarget,
    ): DriverBackendResult

    fun actions(clientId: String): List<DriverActionDescriptor> = emptyList()

    fun runtimeMetadata(clientId: String): DriverRuntimeMetadata = DriverRuntimeMetadata.runtimeAdapter()

    fun runtimeGraph(clientId: String): RuntimeCapabilityGraph = RuntimeCapabilityGraph(clientId = clientId)

    fun operationAdapters(clientId: String): DriverOperationAdapters = DriverOperationAdapters.empty()

    fun invoke(
        clientId: String,
        invocation: DriverActionInvocation,
    ): DriverActionResult =
        DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.UNSUPPORTED,
            message = "unsupported action ${invocation.action}",
        )

    fun stop(clientId: String): DriverBackendResult
}

data class DriverBackendResult(
    val action: DriverBackendAction,
    val message: String? = null,
    val observed: Boolean = true,
)

enum class DriverBackendAction {
    CONNECT,
    STOP,
}

private fun DriverActionResult.toDriverEvent(clientId: String): DriverEvent? {
    if (message == null) {
        return null
    }
    if (status != DriverActionStatus.ACCEPTED) {
        return DriverEvent(
            type = DriverEventType.ERROR,
            client = clientId,
            message = message,
        )
    }

    return DriverEvent(
        type = eventType ?: return null,
        client = clientId,
        message = message,
    )
}
