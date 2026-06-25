package com.minekube.craftwright.driver.api

import com.minekube.craftwright.protocol.ClientState
import kotlinx.serialization.Serializable
import java.time.Instant

interface DriverSession {
    val clientId: String

    fun snapshot(): DriverClientSnapshot

    fun connect(target: ConnectionTarget): DriverClientSnapshot

    fun sendChat(command: ChatCommand): DriverEvent

    fun player(): PlayerSnapshot

    fun invoke(invocation: DriverCapabilityInvocation): DriverCapabilityResult

    fun stop(): DriverClientSnapshot

    fun events(): List<DriverEvent>
}

@Serializable
data class DriverClientSnapshot(
    val id: String,
    val state: ClientState,
)

@Serializable
data class ConnectionTarget(
    val host: String,
    val port: Int,
)

@Serializable
data class ChatCommand(
    val message: String,
)

@Serializable
data class DriverCapabilityInvocation(
    val capability: String,
    val arguments: Map<String, String> = emptyMap(),
)

@Serializable
data class DriverCapabilityResult(
    val capability: String,
    val status: DriverCapabilityStatus,
    val message: String? = null,
)

@Serializable
enum class DriverCapabilityStatus {
    ACCEPTED,
    UNSUPPORTED,
    FAILED,
}

@Serializable
data class PlayerPosition(
    val x: Double,
    val y: Double,
    val z: Double,
)

@Serializable
data class PlayerSnapshot(
    val id: String,
    val name: String,
    val state: ClientState,
    val position: PlayerPosition,
)

@Serializable
data class DriverEvent(
    val type: DriverEventType,
    val client: String,
    val message: String? = null,
    val time: String = Instant.now().toString(),
)

@Serializable
enum class DriverEventType {
    CLIENT_CREATED,
    CLIENT_CONNECTED,
    CHAT,
    CLIENT_STOPPED,
}

class FakeDriverSession(
    override val clientId: String,
    private val profileName: String,
) : DriverSession {
    private var state = ClientState.RUNNING
    private val events = mutableListOf(
        DriverEvent(
            type = DriverEventType.CLIENT_CREATED,
            client = clientId,
            message = "created client $clientId",
        )
    )

    override fun snapshot(): DriverClientSnapshot =
        DriverClientSnapshot(id = clientId, state = state)

    override fun connect(target: ConnectionTarget): DriverClientSnapshot {
        require(target.host.isNotBlank()) { "connection host is required" }
        require(target.port in 1..65535) { "connection port must be between 1 and 65535" }
        state = ClientState.CONNECTED
        events += DriverEvent(
            type = DriverEventType.CLIENT_CONNECTED,
            client = clientId,
            message = "connected $clientId to ${target.host}:${target.port}",
        )
        return snapshot()
    }

    override fun sendChat(command: ChatCommand): DriverEvent {
        require(command.message.isNotBlank()) { "chat message is required" }
        val event = DriverEvent(
            type = DriverEventType.CHAT,
            client = clientId,
            message = command.message,
        )
        events += event
        return event
    }

    override fun player(): PlayerSnapshot =
        PlayerSnapshot(
            id = clientId,
            name = profileName,
            state = state,
            position = PlayerPosition(0.0, 0.0, 0.0),
        )

    override fun invoke(invocation: DriverCapabilityInvocation): DriverCapabilityResult {
        require(invocation.capability.isNotBlank()) { "capability is required" }
        return DriverCapabilityResult(
            capability = invocation.capability,
            status = DriverCapabilityStatus.ACCEPTED,
            message = "accepted ${invocation.capability} for $clientId",
        )
    }

    override fun stop(): DriverClientSnapshot {
        state = ClientState.STOPPED
        events += DriverEvent(
            type = DriverEventType.CLIENT_STOPPED,
            client = clientId,
            message = "stopped client $clientId",
        )
        return snapshot()
    }

    override fun events(): List<DriverEvent> =
        events.toList()
}
