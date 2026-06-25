package com.minekube.craftwright.driver.fabric.v1_21_6

import com.minekube.craftwright.driver.api.ChatCommand
import com.minekube.craftwright.driver.api.ConnectionTarget
import com.minekube.craftwright.driver.api.DriverCapabilityDescriptor
import com.minekube.craftwright.driver.api.DriverCapabilityInvocation
import com.minekube.craftwright.driver.api.DriverCapabilityResult
import com.minekube.craftwright.driver.api.DriverCapabilityStatus
import com.minekube.craftwright.driver.api.booleanArgument
import com.minekube.craftwright.driver.api.intArgument
import com.minekube.craftwright.driver.api.stringArgument
import com.minekube.craftwright.driver.runtime.DriverBackend
import com.minekube.craftwright.driver.runtime.DriverBackendAction
import com.minekube.craftwright.driver.runtime.DriverBackendPlayer
import com.minekube.craftwright.driver.runtime.DriverBackendResult

class FabricDriverBackend private constructor(
    private val mode: Mode,
    private val gateway: FabricClientGateway?,
) : DriverBackend {
    private val events = mutableListOf<String>()

    override fun connect(clientId: String, target: ConnectionTarget): DriverBackendResult {
        require(target.host.isNotBlank()) { "connection host is required" }
        require(target.port in 1..65535) { "connection port must be between 1 and 65535" }
        record("connect $clientId ${target.host}:${target.port}")
        gateway?.execute {
            gateway.connect(target)
        }
        return DriverBackendResult(DriverBackendAction.CONNECT, "fabric ${mode.id} connect requested")
    }

    override fun sendChat(clientId: String, command: ChatCommand): DriverBackendResult {
        require(command.message.isNotBlank()) { "chat message is required" }
        record("chat $clientId ${command.message}")
        gateway?.execute {
            val message = command.message
            if (message.startsWith("/")) {
                gateway.sendCommand(message.removePrefix("/"))
            } else {
                gateway.sendChat(message)
            }
        }
        return DriverBackendResult(DriverBackendAction.CHAT, command.message)
    }

    override fun player(clientId: String): DriverBackendPlayer? =
        gateway?.player()?.let { player ->
            DriverBackendPlayer(
                name = player.name,
                state = player.state,
                position = player.position,
            )
        }

    override fun capabilities(clientId: String): List<DriverCapabilityDescriptor> =
        listOf(
            DriverCapabilityDescriptor.playerMove(),
            DriverCapabilityDescriptor.playerChat(),
        )

    override fun invoke(clientId: String, invocation: DriverCapabilityInvocation): DriverCapabilityResult {
        require(invocation.capability.isNotBlank()) { "capability is required" }
        if (invocation.capability == "player.chat") {
            val message = requireNotNull(invocation.arguments.stringArgument("message")) { "message is required" }
            val result = sendChat(clientId, ChatCommand(message))
            return DriverCapabilityResult(
                capability = invocation.capability,
                status = DriverCapabilityStatus.ACCEPTED,
                message = result.message,
            )
        }
        if (invocation.capability != "player.move") {
            return DriverCapabilityResult(
                capability = invocation.capability,
                status = DriverCapabilityStatus.UNSUPPORTED,
                message = "unsupported Fabric capability ${invocation.capability}",
            )
        }
        val intent = FabricMovementIntent(
            forward = invocation.arguments.booleanArgument("forward"),
            backward = invocation.arguments.booleanArgument("backward"),
            left = invocation.arguments.booleanArgument("left"),
            right = invocation.arguments.booleanArgument("right"),
            jump = invocation.arguments.booleanArgument("jump"),
            sneak = invocation.arguments.booleanArgument("sneak"),
            sprint = invocation.arguments.booleanArgument("sprint"),
            ticks = invocation.arguments.intArgument("ticks") ?: 1,
        )
        gateway?.execute {
            gateway.move(intent)
        }
        return DriverCapabilityResult(
            capability = invocation.capability,
            status = DriverCapabilityStatus.ACCEPTED,
            message = "fabric ${mode.id} capability ${invocation.capability} accepted",
        )
    }

    override fun stop(clientId: String): DriverBackendResult {
        record("stop $clientId")
        gateway?.execute {
            gateway.stop()
        }
        return DriverBackendResult(DriverBackendAction.STOP, "fabric ${mode.id} stop requested")
    }

    fun events(): List<String> = events.toList()

    private fun record(event: String) {
        events += event
    }

    private enum class Mode(val id: String) {
        PLACEHOLDER("placeholder"),
        REAL_CLIENT("real-client"),
    }

    companion object {
        @Volatile
        private var installed: FabricDriverBackend? = null

        fun placeholder(): FabricDriverBackend = FabricDriverBackend(Mode.PLACEHOLDER, gateway = null)

        fun real(gateway: FabricClientGateway = MinecraftFabricClientGateway()): FabricDriverBackend =
            FabricDriverBackend(Mode.REAL_CLIENT, gateway)

        fun install(backend: FabricDriverBackend) {
            installed = backend
        }

        fun current(): FabricDriverBackend =
            installed ?: placeholder().also(::install)
    }
}
