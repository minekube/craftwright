package com.minekube.craftwright.driver.runtime

import com.minekube.craftwright.bridge.hmc.ClientAction
import com.minekube.craftwright.bridge.hmc.HmcBridgeBackend
import com.minekube.craftwright.bridge.hmc.MoveIntent
import com.minekube.craftwright.driver.api.ChatCommand
import com.minekube.craftwright.driver.api.ConnectionTarget
import com.minekube.craftwright.driver.api.DriverCapabilityInvocation
import com.minekube.craftwright.driver.api.DriverCapabilityResult
import com.minekube.craftwright.driver.api.DriverCapabilityStatus

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

    override fun invoke(clientId: String, invocation: DriverCapabilityInvocation): DriverCapabilityResult {
        if (invocation.capability != "player.move") {
            return DriverCapabilityResult(
                capability = invocation.capability,
                status = DriverCapabilityStatus.UNSUPPORTED,
                message = "unsupported bridge capability ${invocation.capability}",
            )
        }
        val intent = when {
            invocation.arguments["backward"]?.toBooleanStrictOrNull() == true -> MoveIntent.BACKWARD
            invocation.arguments["left"]?.toBooleanStrictOrNull() == true -> MoveIntent.LEFT
            invocation.arguments["right"]?.toBooleanStrictOrNull() == true -> MoveIntent.RIGHT
            else -> MoveIntent.FORWARD
        }
        val ticks = invocation.arguments["ticks"]?.toIntOrNull() ?: 20
        val result = bridge.move(clientId, intent, ticks)
        require(result.action == ClientAction.MOVE) { "bridge returned ${result.action} for move" }
        return DriverCapabilityResult(
            capability = invocation.capability,
            status = DriverCapabilityStatus.ACCEPTED,
            message = result.publicDescription,
        )
    }
}
