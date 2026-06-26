package com.minekube.craftless.testkit

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionArgument
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverClientSnapshot
import com.minekube.craftless.driver.api.DriverEvent
import com.minekube.craftless.driver.api.DriverEventType
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.api.DriverSession
import com.minekube.craftless.driver.api.intArgument
import com.minekube.craftless.driver.api.requireChatMessage
import com.minekube.craftless.driver.api.stringArgument
import com.minekube.craftless.protocol.ClientState
import com.minekube.craftless.protocol.RuntimeAvailability
import com.minekube.craftless.protocol.RuntimeCapabilityGraph
import com.minekube.craftless.protocol.RuntimeOperationNode
import com.minekube.craftless.protocol.RuntimeResourceNode
import com.minekube.craftless.protocol.RuntimeSchema

class FakeDriverSession(
    override val clientId: String,
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
        state = ClientState.CONNECTED
        events +=
            DriverEvent(
                type = DriverEventType.CLIENT_CONNECTED,
                client = clientId,
                message = "connected $clientId to ${target.host}:${target.port}",
            )
        return snapshot()
    }

    override fun actions(): List<DriverActionDescriptor> =
        listOf(
            fakePlayerMoveActionDescriptor(),
            fakePlayerChatActionDescriptor(),
        )

    override fun runtimeMetadata(): DriverRuntimeMetadata = fakeDriverRuntimeMetadata()

    override fun runtimeGraph(): RuntimeCapabilityGraph =
        RuntimeCapabilityGraph(
            clientId = clientId,
            resources = listOf(RuntimeResourceNode("player", RuntimeAvailability.available())),
            operations =
                listOf(
                    RuntimeOperationNode(
                        id = "player.chat",
                        resource = "player",
                        adapter = "fake.chat",
                        arguments = mapOf("message" to RuntimeSchema("string")),
                        availability = RuntimeAvailability.available(),
                    ),
                    RuntimeOperationNode(
                        id = "player.move",
                        resource = "player",
                        adapter = "fake.move",
                        arguments = mapOf("ticks" to RuntimeSchema("integer")),
                        availability = RuntimeAvailability.available(),
                    ),
                ),
        )

    override fun invoke(invocation: DriverActionInvocation): DriverActionResult {
        require(invocation.action.isNotBlank()) { "action is required" }
        val result =
            when (invocation.action) {
                "player.chat" -> {
                    val message = requireNotNull(invocation.arguments.stringArgument("message")) { "message is required" }
                    val event = recordChat(message)
                    DriverActionResult(
                        action = invocation.action,
                        status = DriverActionStatus.ACCEPTED,
                        message = event.message,
                        eventType = event.type,
                    )
                }

                "player.move" -> {
                    val ticks = invocation.arguments.intArgument("ticks") ?: 1
                    require(ticks > 0) { "movement ticks must be positive" }
                    val event = recordMovement("accepted ${invocation.action} for $clientId")
                    DriverActionResult(
                        action = invocation.action,
                        status = DriverActionStatus.ACCEPTED,
                        message = event.message,
                        eventType = event.type,
                    )
                }

                else ->
                    DriverActionResult(
                        action = invocation.action,
                        status = DriverActionStatus.UNSUPPORTED,
                        message = "unsupported fake action ${invocation.action}",
                    )
            }
        if (result.status != DriverActionStatus.ACCEPTED && result.message != null) {
            events +=
                DriverEvent(
                    type = DriverEventType.ERROR,
                    client = clientId,
                    message = result.message,
                )
        }
        return result
    }

    override fun stop(): DriverClientSnapshot {
        state = ClientState.STOPPED
        events +=
            DriverEvent(
                type = DriverEventType.CLIENT_STOPPED,
                client = clientId,
                message = "stopped client $clientId",
            )
        return snapshot()
    }

    override fun events(): List<DriverEvent> = events.toList()

    private fun recordChat(message: String): DriverEvent {
        requireChatMessage(message)
        val event =
            DriverEvent(
                type = DriverEventType.CHAT,
                client = clientId,
                message = message,
            )
        events += event
        return event
    }

    private fun recordMovement(message: String): DriverEvent {
        val event =
            DriverEvent(
                type = DriverEventType.MOVEMENT,
                client = clientId,
                message = message,
            )
        events += event
        return event
    }
}

fun fakeDriverRuntimeMetadata(): DriverRuntimeMetadata =
    DriverRuntimeMetadata(
        driver = "craftless-fake",
        permissionsFingerprint = "local-fake",
    )

private fun fakePlayerMoveActionDescriptor(): DriverActionDescriptor =
    DriverActionDescriptor(
        id = "player.move",
        schemaVersion = "1",
        arguments =
            mapOf(
                "forward" to DriverActionArgument("boolean"),
                "backward" to DriverActionArgument("boolean"),
                "left" to DriverActionArgument("boolean"),
                "right" to DriverActionArgument("boolean"),
                "jump" to DriverActionArgument("boolean"),
                "sneak" to DriverActionArgument("boolean"),
                "sprint" to DriverActionArgument("boolean"),
                "ticks" to DriverActionArgument("integer"),
            ),
    )

private fun fakePlayerChatActionDescriptor(): DriverActionDescriptor =
    DriverActionDescriptor(
        id = "player.chat",
        schemaVersion = "1",
        arguments =
            mapOf(
                "message" to DriverActionArgument("string", required = true),
            ),
    )
