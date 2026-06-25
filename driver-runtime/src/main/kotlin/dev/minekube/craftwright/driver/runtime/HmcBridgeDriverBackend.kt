package dev.minekube.craftwright.driver.runtime

import dev.minekube.craftwright.bridge.hmc.ClientAction
import dev.minekube.craftwright.bridge.hmc.HmcBridgeBackend
import dev.minekube.craftwright.driver.api.ChatCommand
import dev.minekube.craftwright.driver.api.ConnectionTarget

class HmcBridgeDriverBackend(
    private val bridge: HmcBridgeBackend,
) : DriverBackend {
    override fun connect(clientId: String, target: ConnectionTarget): DriverBackendResult {
        val result = bridge.connect(clientId, "${target.host}:${target.port}")
        require(result.action == ClientAction.CONNECT) { "bridge returned ${result.action} for connect" }
        return DriverBackendResult(DriverBackendAction.CONNECT, result.publicDescription)
    }

    override fun sendChat(clientId: String, command: ChatCommand): DriverBackendResult {
        val result = bridge.chat(clientId, command.message)
        require(result.action == ClientAction.CHAT) { "bridge returned ${result.action} for chat" }
        return DriverBackendResult(DriverBackendAction.CHAT, command.message)
    }

    override fun stop(clientId: String): DriverBackendResult {
        val result = bridge.stop(clientId)
        require(result.action == ClientAction.STOP) { "bridge returned ${result.action} for stop" }
        return DriverBackendResult(DriverBackendAction.STOP, result.publicDescription)
    }
}
