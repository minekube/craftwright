package com.minekube.craftwright.driver.runtime

import com.minekube.craftwright.bridge.hmc.ClientAction
import com.minekube.craftwright.bridge.hmc.HmcBridgeBackend
import com.minekube.craftwright.bridge.hmc.MoveIntent
import com.minekube.craftwright.driver.api.ConnectionTarget
import com.minekube.craftwright.driver.api.DriverActionDescriptor
import com.minekube.craftwright.driver.api.DriverActionInvocation
import com.minekube.craftwright.driver.api.DriverActionResult
import com.minekube.craftwright.driver.api.DriverActionStatus
import com.minekube.craftwright.driver.api.DriverRuntimeMetadata
import com.minekube.craftwright.driver.api.booleanArgument
import com.minekube.craftwright.driver.api.intArgument
import com.minekube.craftwright.driver.api.stringArgument

class HmcBridgeDriverBackend(
    private val bridge: HmcBridgeBackend,
) : DriverBackend {
    override fun connect(clientId: String, target: ConnectionTarget): DriverBackendResult {
        val result = bridge.connect(clientId, "${target.host}:${target.port}")
        require(result.action == ClientAction.CONNECT) { "bridge returned ${result.action} for connect" }
        return DriverBackendResult(DriverBackendAction.CONNECT, result.publicDescription)
    }

    override fun stop(clientId: String): DriverBackendResult {
        val result = bridge.stop(clientId)
        require(result.action == ClientAction.STOP) { "bridge returned ${result.action} for stop" }
        return DriverBackendResult(DriverBackendAction.STOP, result.publicDescription)
    }

    override fun actions(clientId: String): List<DriverActionDescriptor> =
        listOf(
            DriverActionDescriptor.playerMove(),
            DriverActionDescriptor.playerChat(),
        )

    override fun runtimeMetadata(clientId: String): DriverRuntimeMetadata =
        DriverRuntimeMetadata(
            driver = "craftwright-driver-bridge",
            permissionsFingerprint = "bridge-evidence",
        )

    override fun invoke(clientId: String, invocation: DriverActionInvocation): DriverActionResult {
        if (invocation.action == "player.chat") {
            val message = requireNotNull(invocation.arguments.stringArgument("message")) { "message is required" }
            val result = bridge.chat(clientId, message)
            require(result.action == ClientAction.CHAT) { "bridge returned ${result.action} for chat" }
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.ACCEPTED,
                message = message,
            )
        }
        if (invocation.action != "player.move") {
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.UNSUPPORTED,
                message = "unsupported bridge action ${invocation.action}",
            )
        }
        val intent = when {
            invocation.arguments.booleanArgument("backward") -> MoveIntent.BACKWARD
            invocation.arguments.booleanArgument("left") -> MoveIntent.LEFT
            invocation.arguments.booleanArgument("right") -> MoveIntent.RIGHT
            else -> MoveIntent.FORWARD
        }
        val ticks = invocation.arguments.intArgument("ticks") ?: 20
        val result = bridge.move(clientId, intent, ticks)
        require(result.action == ClientAction.MOVE) { "bridge returned ${result.action} for move" }
        return DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            message = result.publicDescription,
        )
    }
}
