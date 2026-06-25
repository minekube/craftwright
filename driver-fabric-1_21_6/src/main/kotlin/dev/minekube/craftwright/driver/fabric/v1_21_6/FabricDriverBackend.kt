package dev.minekube.craftwright.driver.fabric.v1_21_6

import dev.minekube.craftwright.driver.api.ChatCommand
import dev.minekube.craftwright.driver.api.ConnectionTarget
import dev.minekube.craftwright.driver.runtime.DriverBackend
import dev.minekube.craftwright.driver.runtime.DriverBackendAction
import dev.minekube.craftwright.driver.runtime.DriverBackendPlayer
import dev.minekube.craftwright.driver.runtime.DriverBackendResult

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
