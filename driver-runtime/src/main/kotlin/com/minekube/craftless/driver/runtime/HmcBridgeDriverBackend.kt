package com.minekube.craftless.driver.runtime

import com.minekube.craftless.bridge.hmc.ClientAction
import com.minekube.craftless.bridge.hmc.HmcBridgeBackend
import com.minekube.craftless.bridge.hmc.MoveIntent
import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionArgument
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverEventType
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.api.booleanArgument
import com.minekube.craftless.driver.api.intArgument
import com.minekube.craftless.driver.api.requireChatMessage
import com.minekube.craftless.driver.api.stringArgument

class HmcBridgeDriverBackend(
    private val bridge: HmcBridgeBackend,
) : DriverBackend {
    override fun connect(clientId: String, target: ConnectionTarget): DriverBackendResult {
        val result = bridge.connect(clientId, "${target.host}:${target.port}")
        require(result.action == ClientAction.CONNECT) { "driver backend returned ${result.action} for connect" }
        return DriverBackendResult(DriverBackendAction.CONNECT, result.publicDescription)
    }

    override fun stop(clientId: String): DriverBackendResult {
        val result = bridge.stop(clientId)
        require(result.action == ClientAction.STOP) { "driver backend returned ${result.action} for stop" }
        return DriverBackendResult(DriverBackendAction.STOP, result.publicDescription)
    }

    override fun actions(clientId: String): List<DriverActionDescriptor> =
        listOf(
            bridgePlayerMoveActionDescriptor(),
            bridgePlayerChatActionDescriptor(),
        )

    override fun runtimeMetadata(clientId: String): DriverRuntimeMetadata =
        DriverRuntimeMetadata(
            driver = "craftless-driver-bridge",
            permissionsFingerprint = "bridge-evidence",
        )

    override fun invoke(clientId: String, invocation: DriverActionInvocation): DriverActionResult {
        if (invocation.action == "player.chat") {
            val message = requireChatMessage(
                requireNotNull(invocation.arguments.stringArgument("message")) { "message is required" },
            )
            val result = bridge.chat(clientId, message)
            require(result.action == ClientAction.CHAT) { "driver backend returned ${result.action} for chat" }
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.ACCEPTED,
                message = message,
                eventType = DriverEventType.CHAT,
            )
        }
        if (invocation.action != "player.move") {
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.UNSUPPORTED,
                message = "unsupported action ${invocation.action}",
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
        require(result.action == ClientAction.MOVE) { "driver backend returned ${result.action} for move" }
        return DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            message = result.publicDescription,
            eventType = DriverEventType.MOVEMENT,
        )
    }
}

private fun bridgePlayerMoveActionDescriptor(): DriverActionDescriptor =
    DriverActionDescriptor(
        id = "player.move",
        schemaVersion = "1",
        arguments = mapOf(
            "forward" to DriverActionArgument("boolean"),
            "backward" to DriverActionArgument("boolean"),
            "left" to DriverActionArgument("boolean"),
            "right" to DriverActionArgument("boolean"),
            "ticks" to DriverActionArgument("integer"),
        ),
    )

private fun bridgePlayerChatActionDescriptor(): DriverActionDescriptor =
    DriverActionDescriptor(
        id = "player.chat",
        schemaVersion = "1",
        arguments = mapOf(
            "message" to DriverActionArgument("string", required = true),
        ),
    )
