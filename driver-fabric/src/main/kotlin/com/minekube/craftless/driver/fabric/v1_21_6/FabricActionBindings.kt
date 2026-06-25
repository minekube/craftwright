package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.driver.api.DriverActionArgument
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionResultDescriptor
import com.minekube.craftless.driver.api.DriverActionResultProperty
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverEventType
import com.minekube.craftless.driver.api.booleanArgument
import com.minekube.craftless.driver.api.intArgument
import com.minekube.craftless.driver.api.requireChatMessage
import com.minekube.craftless.driver.api.stringArgument
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.minecraft.client.MinecraftClient
import net.minecraft.client.input.Input
import net.minecraft.item.ItemStack
import net.minecraft.util.PlayerInput
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d

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

    fun <T> queryOnClient(query: MinecraftClient.() -> T): T {
        val clientGateway = requireNotNull(gateway) { "client gateway is required" }
        return clientGateway.queryOnClient(query)
    }
}

internal fun defaultFabricActionBindings(): List<FabricActionBinding> =
    listOf(
        FabricPlayerMoveActionBinding,
        FabricPlayerChatActionBinding,
    )

internal object FabricInventoryQueryActionBinding : FabricActionBinding {
    override val descriptor: DriverActionDescriptor = fabricInventoryQueryDescriptor()

    override fun invoke(
        clientId: String,
        invocation: DriverActionInvocation,
        context: FabricActionContext,
    ): DriverActionResult {
        val data =
            context.queryOnClient {
                val player = requireNotNull(player) { "client is not connected to a server" }
                player.inventory.toCraftlessInventoryData()
            }
        return DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            message = "fabric ${context.modeId} action ${invocation.action} queried",
            data = data,
        )
    }
}

internal fun fabricInventoryQueryDescriptor(): DriverActionDescriptor =
    DriverActionDescriptor(
        id = "inventory.query",
        schemaVersion = "1",
        result = fabricObjectDataResultDescriptor(),
    )

internal object FabricPlayerRaycastActionBinding : FabricActionBinding {
    override val descriptor: DriverActionDescriptor = fabricRaycastDescriptor()

    override fun invoke(
        clientId: String,
        invocation: DriverActionInvocation,
        context: FabricActionContext,
    ): DriverActionResult {
        val maxDistance = invocation.arguments["max-distance"]?.jsonPrimitive?.doubleOrNull ?: DEFAULT_MAX_DISTANCE
        require(maxDistance > 0.0) { "raycast max-distance must be positive" }
        val includeFluids = invocation.arguments.booleanArgument("include-fluids")
        val data =
            context.queryOnClient {
                val camera = requireNotNull(cameraEntity ?: player) { "client is not connected to a server" }
                camera.raycast(maxDistance, 1.0f, includeFluids).toCraftlessRaycastData()
            }
        return DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            message = "fabric ${context.modeId} action ${invocation.action} queried",
            data = data,
        )
    }

    private const val DEFAULT_MAX_DISTANCE = 5.0
}

internal fun fabricRaycastDescriptor(): DriverActionDescriptor =
    DriverActionDescriptor(
        id = "player.raycast",
        schemaVersion = "1",
        arguments =
            mapOf(
                "max-distance" to DriverActionArgument("number"),
                "include-fluids" to DriverActionArgument("boolean"),
            ),
        result =
            fabricObjectDataResultDescriptor(),
    )

private fun fabricObjectDataResultDescriptor(): DriverActionResultDescriptor =
    DriverActionResultDescriptor(
        properties =
            mapOf(
                "action" to DriverActionResultProperty("string"),
                "status" to DriverActionResultProperty("string"),
                "message" to DriverActionResultProperty("string"),
                "data" to DriverActionResultProperty("object"),
            ),
        required = listOf("action", "status"),
    )

private object FabricPlayerChatActionBinding : FabricActionBinding {
    override val descriptor: DriverActionDescriptor =
        DriverActionDescriptor(
            id = "player.chat",
            schemaVersion = "1",
            arguments =
                mapOf(
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

    override fun invoke(
        clientId: String,
        invocation: DriverActionInvocation,
        context: FabricActionContext,
    ): DriverActionResult {
        val intent =
            FabricMovementIntent(
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
            val originalInput = player.input
            player.input =
                CraftlessMovementInput(
                    delegate = originalInput,
                    movementInput = intent.toPlayerInput(),
                    ticks = intent.ticks,
                    restore = {
                        if (player.input is CraftlessMovementInput) {
                            player.input = originalInput
                        }
                    },
                )
        }
        return DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            message = "fabric ${context.modeId} action ${invocation.action} accepted for ${intent.ticks} tick(s)",
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
) {
    fun toPlayerInput(): PlayerInput = PlayerInput(forward, backward, left, right, jump, sneak, sprint)
}

private class CraftlessMovementInput(
    private val delegate: Input,
    private val movementInput: PlayerInput,
    private var ticks: Int,
    private val restore: () -> Unit,
) : Input() {
    override fun tick() {
        if (ticks <= 0) {
            restore()
            delegate.tick()
            playerInput = delegate.playerInput
            movementVector = delegate.getMovementInput()
            return
        }

        playerInput = movementInput
        movementVector = movementInput.toMovementVector()
        ticks -= 1
    }
}

private fun PlayerInput.toMovementVector(): Vec2f =
    Vec2f(
        movementMultiplier(left, right),
        movementMultiplier(forward, backward),
    ).normalize()

private fun movementMultiplier(
    positive: Boolean,
    negative: Boolean,
): Float =
    when {
        positive == negative -> 0f
        positive -> 1f
        else -> -1f
    }

private fun net.minecraft.entity.player.PlayerInventory.toCraftlessInventoryData(): JsonObject =
    buildJsonObject {
        put("selected-slot", selectedSlot)
        put("slot-count", size())
        put(
            "slots",
            buildJsonArray {
                for (slot in 0 until size()) {
                    add(getStack(slot).toCraftlessSlotData(slot))
                }
            },
        )
    }

private fun ItemStack.toCraftlessSlotData(slot: Int): JsonObject =
    buildJsonObject {
        put("slot", slot)
        put("empty", isEmpty)
        if (!isEmpty) {
            put("count", count)
            put("item-name", name.string)
        }
    }

private fun HitResult.toCraftlessRaycastData(): JsonObject =
    buildJsonObject {
        put("hit", type != HitResult.Type.MISS)
        put("target-kind", type.name.lowercase())
        put("position", pos.toCraftlessJson())
        when (this@toCraftlessRaycastData) {
            is BlockHitResult -> {
                put("block", blockPos.toShortString())
                put("side", side.name.lowercase())
            }
            is EntityHitResult -> {
                put("entity-id", entity.id)
            }
        }
    }

private fun Vec3d.toCraftlessJson(): JsonObject =
    buildJsonObject {
        put("x", x)
        put("y", y)
        put("z", z)
    }
