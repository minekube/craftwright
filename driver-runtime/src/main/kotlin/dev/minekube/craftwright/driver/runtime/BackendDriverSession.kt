package dev.minekube.craftwright.driver.runtime

import dev.minekube.craftwright.driver.api.ChatCommand
import dev.minekube.craftwright.driver.api.ConnectionTarget
import dev.minekube.craftwright.driver.api.DriverClientSnapshot
import dev.minekube.craftwright.driver.api.DriverEvent
import dev.minekube.craftwright.driver.api.DriverEventType
import dev.minekube.craftwright.driver.api.DriverSession
import dev.minekube.craftwright.driver.api.PlayerSnapshot
import dev.minekube.craftwright.protocol.ClientState

class BackendDriverSession(
    override val clientId: String,
    private val profileName: String,
    private val backend: DriverBackend,
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
        val result = backend.connect(clientId, target)
        require(result.action == DriverBackendAction.CONNECT) { "backend returned ${result.action} for connect" }
        state = ClientState.CONNECTED
        events += DriverEvent(
            type = DriverEventType.CLIENT_CONNECTED,
            client = clientId,
            message = result.message ?: "connected $clientId to ${target.host}:${target.port}",
        )
        return snapshot()
    }

    override fun sendChat(command: ChatCommand): DriverEvent {
        require(command.message.isNotBlank()) { "chat message is required" }
        val result = backend.sendChat(clientId, command)
        require(result.action == DriverBackendAction.CHAT) { "backend returned ${result.action} for chat" }
        val event = DriverEvent(
            type = DriverEventType.CHAT,
            client = clientId,
            message = result.message ?: command.message,
        )
        events += event
        return event
    }

    override fun player(): PlayerSnapshot =
        backend.player(clientId)?.let { observed ->
            PlayerSnapshot(
                id = clientId,
                name = observed.name,
                state = observed.state,
            )
        } ?: PlayerSnapshot(
            id = clientId,
            name = profileName,
            state = state,
        )

    override fun stop(): DriverClientSnapshot {
        val result = backend.stop(clientId)
        require(result.action == DriverBackendAction.STOP) { "backend returned ${result.action} for stop" }
        state = ClientState.STOPPED
        events += DriverEvent(
            type = DriverEventType.CLIENT_STOPPED,
            client = clientId,
            message = result.message ?: "stopped client $clientId",
        )
        return snapshot()
    }

    override fun events(): List<DriverEvent> =
        events.toList()
}

interface DriverBackend {
    fun connect(clientId: String, target: ConnectionTarget): DriverBackendResult

    fun sendChat(clientId: String, command: ChatCommand): DriverBackendResult

    fun player(clientId: String): DriverBackendPlayer? = null

    fun stop(clientId: String): DriverBackendResult
}

data class DriverBackendPlayer(
    val name: String,
    val state: ClientState,
)

data class DriverBackendResult(
    val action: DriverBackendAction,
    val message: String? = null,
)

enum class DriverBackendAction {
    CONNECT,
    CHAT,
    STOP,
}
