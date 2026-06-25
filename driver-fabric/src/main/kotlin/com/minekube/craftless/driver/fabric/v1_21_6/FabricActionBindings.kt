package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.driver.api.DriverActionArgument
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverEventType
import com.minekube.craftless.driver.api.booleanArgument
import com.minekube.craftless.driver.api.intArgument
import com.minekube.craftless.driver.api.requireChatMessage
import com.minekube.craftless.driver.api.stringArgument
import net.minecraft.client.MinecraftClient
import net.minecraft.util.PlayerInput

internal interface FabricActionBinding {
    val descriptor: DriverActionDescriptor

    fun invoke(
        clientId: String,
        invocation: DriverActionInvocation,
        context: FabricActionContext,
    ): DriverActionResult
}

internal data class FabricActionContext(
    val modeId: String,
    val gateway: FabricClientGateway?,
    val record: (String) -> Unit,
) {
    fun executeOnClient(action: MinecraftClient.() -> Unit) {
        gateway?.executeOnClient(action)
    }
}

internal fun defaultFabricActionBindings(): List<FabricActionBinding> =
    listOf(
        FabricPlayerMoveActionBinding,
        FabricPlayerChatActionBinding,
    )

private object FabricPlayerChatActionBinding : FabricActionBinding {
    override val descriptor: DriverActionDescriptor =
        DriverActionDescriptor(
            id = "player.chat",
            schemaVersion = "1",
            arguments = mapOf(
                "message" to DriverActionArgument("string", required = true),
            ),
        )

    override fun invoke(
        clientId: String,
        invocation: DriverActionInvocation,
        context: FabricActionContext,
    ): DriverActionResult {
        val message = requireNotNull(invocation.arguments.stringArgument("message")) { "message is required" }
        requireChatMessage(message)
        context.record("chat $clientId $message")
        context.executeOnClient {
            val networkHandler = requireNotNull(networkHandler) { "client is not connected to a server" }
            networkHandler.sendChatMessage(message)
        }
        return DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            message = message,
            eventType = DriverEventType.CHAT,
        )
    }
}

private object FabricPlayerMoveActionBinding : FabricActionBinding {
    override val descriptor: DriverActionDescriptor =
        DriverActionDescriptor(
            id = "player.move",
            schemaVersion = "1",
            arguments = mapOf(
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

    override fun invoke(
        clientId: String,
        invocation: DriverActionInvocation,
        context: FabricActionContext,
    ): DriverActionResult {
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
        require(intent.ticks > 0) { "movement ticks must be positive" }
        context.executeOnClient {
            val player = requireNotNull(player) { "client is not connected to a server" }
            player.input.playerInput = PlayerInput(
                intent.forward,
                intent.backward,
                intent.left,
                intent.right,
                intent.jump,
                intent.sneak,
                intent.sprint,
            )
        }
        return DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            message = "fabric ${context.modeId} action ${invocation.action} accepted",
            eventType = DriverEventType.MOVEMENT,
        )
    }
}

private data class FabricMovementIntent(
    val forward: Boolean = false,
    val backward: Boolean = false,
    val left: Boolean = false,
    val right: Boolean = false,
    val jump: Boolean = false,
    val sneak: Boolean = false,
    val sprint: Boolean = false,
    val ticks: Int = 1,
)
