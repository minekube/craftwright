package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.booleanArgument
import com.minekube.craftless.driver.api.intArgument
import com.minekube.craftless.driver.api.stringArgument
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.network.ClientPlayerInteractionManager
import net.minecraft.client.world.ClientWorld
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.registry.tag.BlockTags
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

internal interface FabricExecutionAdapter {
    val operationId: String

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

internal fun defaultFabricExecutionAdapters(): List<FabricExecutionAdapter> =
    listOf(
        FabricPlayerQueryExecutionAdapter,
        FabricPlayerLookExecutionAdapter,
        FabricPlayerRaycastExecutionAdapter,
        FabricPlayerMoveExecutionAdapter,
        FabricPlayerChatExecutionAdapter,
        FabricInventoryQueryExecutionAdapter,
        FabricInventoryEquipExecutionAdapter,
        FabricWorldTimeQueryExecutionAdapter,
        FabricWorldBlockBreakExecutionAdapter,
        FabricWorldBlockInteractExecutionAdapter,
        FabricScreenQueryExecutionAdapter,
        FabricScreenCloseExecutionAdapter,
    )

internal object FabricPlayerQueryExecutionAdapter : FabricExecutionAdapter {
    override val operationId: String = FabricBootstrapOperationIds.PLAYER_QUERY

    override fun invoke(
        clientId: String,
        invocation: DriverActionInvocation,
        context: FabricActionContext,
    ): DriverActionResult {
        val data =
            context.queryOnClient {
                val player = requireNotNull(player) { "client is not connected to a server" }
                buildJsonObject {
                    put("position", player.pos.toCraftlessJson())
                    put(
                        "rotation",
                        buildJsonObject {
                            put("yaw", player.yaw)
                            put("pitch", player.pitch)
                        },
                    )
                    put("selected-slot", player.inventory.selectedSlot)
                    put("on-ground", player.isOnGround)
                    put("sneaking", player.isSneaking)
                    put("sprinting", player.isSprinting)
                }
            }
        return DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            message = "fabric ${context.modeId} action ${invocation.action} queried",
            data = data,
        )
    }
}

internal object FabricPlayerLookExecutionAdapter : FabricExecutionAdapter {
    override val operationId: String = FabricBootstrapOperationIds.PLAYER_LOOK

    override fun invoke(
        clientId: String,
        invocation: DriverActionInvocation,
        context: FabricActionContext,
    ): DriverActionResult {
        val yaw =
            invocation.arguments.numberArgument("yaw")
                ?: return DriverActionResult(
                    action = invocation.action,
                    status = DriverActionStatus.FAILED,
                    message = "missing-yaw",
                    data = actionFailure("missing-yaw", "applied"),
                )
        val pitch =
            invocation.arguments.numberArgument("pitch")
                ?: return DriverActionResult(
                    action = invocation.action,
                    status = DriverActionStatus.FAILED,
                    message = "missing-pitch",
                    data = actionFailure("missing-pitch", "applied"),
                )
        if (pitch !in MIN_PITCH..MAX_PITCH) {
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.FAILED,
                message = "invalid-pitch",
                data = actionFailure("invalid-pitch", "applied"),
            )
        }
        context.executeOnClient {
            val player = requireNotNull(player) { "client is not connected to a server" }
            val yawFloat = yaw.toFloat()
            player.setYaw(yawFloat)
            player.setPitch(pitch.toFloat())
            player.headYaw = yawFloat
            player.bodyYaw = yawFloat
        }
        return DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            message = "fabric ${context.modeId} action ${invocation.action} accepted",
        )
    }

    private const val MIN_PITCH = -90.0
    private const val MAX_PITCH = 90.0
}

internal object FabricInventoryQueryExecutionAdapter : FabricExecutionAdapter {
    override val operationId: String = FabricBootstrapOperationIds.INVENTORY_QUERY

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

internal object FabricInventoryEquipExecutionAdapter : FabricExecutionAdapter {
    override val operationId: String = FabricBootstrapOperationIds.INVENTORY_EQUIP

    override fun invoke(
        clientId: String,
        invocation: DriverActionInvocation,
        context: FabricActionContext,
    ): DriverActionResult {
        val slot =
            invocation.arguments.intArgument("slot")
                ?: return DriverActionResult(
                    action = invocation.action,
                    status = DriverActionStatus.FAILED,
                    message = "missing-slot",
                    data = actionFailure("missing-slot", "equipped"),
                )
        if (slot !in HOTBAR_SLOT_RANGE) {
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.FAILED,
                message = "invalid-slot",
                data = actionFailure("invalid-slot", "equipped"),
            )
        }
        context.executeOnClient {
            val player = requireNotNull(player) { "client is not connected to a server" }
            player.inventory.selectedSlot = slot
            player.networkHandler.sendPacket(UpdateSelectedSlotC2SPacket(slot))
        }
        return DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            message = "fabric ${context.modeId} action ${invocation.action} accepted for slot $slot",
        )
    }

    private val HOTBAR_SLOT_RANGE = 0..8
}

internal object FabricWorldBlockBreakExecutionAdapter : FabricExecutionAdapter {
    override val operationId: String = FabricBootstrapOperationIds.WORLD_BLOCK_BREAK

    override fun invoke(
        clientId: String,
        invocation: DriverActionInvocation,
        context: FabricActionContext,
    ): DriverActionResult {
        val maxDistance = invocation.arguments["max-distance"]?.jsonPrimitive?.doubleOrNull ?: DEFAULT_MAX_DISTANCE
        if (maxDistance <= 0.0) {
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.FAILED,
                message = "invalid-max-distance",
                data = blockBreakFailure("invalid-max-distance"),
            )
        }
        val ticks = invocation.arguments.intArgument("ticks") ?: DEFAULT_BREAK_TICKS
        if (ticks !in 1..MAX_BREAK_TICKS) {
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.FAILED,
                message = "invalid-ticks",
                data = blockBreakFailure("invalid-ticks"),
            )
        }
        val includeFluids = invocation.arguments.booleanArgument("include-fluids")
        val targetParse = invocation.arguments.blockBreakTarget()
        if (targetParse.reason != null) {
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.FAILED,
                message = targetParse.reason,
                data = blockBreakFailure(targetParse.reason),
            )
        }
        val requestedTarget = targetParse.target
        val data =
            context.queryOnClient {
                val player = requireNotNull(player) { "client is not connected to a server" }
                val world = requireNotNull(world) { "client is not connected to a server" }
                val interactionManager =
                    requireNotNull(interactionManager) { "client interaction manager is unavailable" }
                if (requestedTarget != null) {
                    val distance = player.eyePos.distanceTo(Vec3d.ofCenter(requestedTarget.position))
                    require(distance <= maxDistance) { "block target exceeds max-distance" }
                    val side = Direction.UP
                    val progress =
                        interactionManager.breakBlockWithProgress(
                            player = player,
                            world = world,
                            position = requestedTarget.position,
                            side = side,
                            ticks = ticks,
                        )
                    return@queryOnClient requestedTarget.toCraftlessBlockBreakData(progress, side)
                }
                val camera = requireNotNull(cameraEntity ?: player) { "client is not connected to a server" }
                val target =
                    camera.raycast(maxDistance, 1.0f, includeFluids) as? BlockHitResult
                        ?: error("no block target")
                val progress =
                    interactionManager.breakBlockWithProgress(
                        player = player,
                        world = world,
                        position = target.blockPos,
                        side = target.side,
                        ticks = ticks,
                    )
                target.toCraftlessBlockBreakData(progress)
            }
        return DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            message = "fabric ${context.modeId} action ${invocation.action} accepted",
            data = data,
        )
    }
}

internal object FabricWorldBlockInteractExecutionAdapter : FabricExecutionAdapter {
    override val operationId: String = FabricBootstrapOperationIds.WORLD_BLOCK_INTERACT

    override fun invoke(
        clientId: String,
        invocation: DriverActionInvocation,
        context: FabricActionContext,
    ): DriverActionResult {
        val maxDistance = invocation.arguments["max-distance"]?.jsonPrimitive?.doubleOrNull ?: DEFAULT_MAX_DISTANCE
        if (maxDistance <= 0.0) {
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.FAILED,
                message = "invalid-max-distance",
                data = blockInteractFailure("invalid-max-distance"),
            )
        }
        val includeFluids = invocation.arguments.booleanArgument("include-fluids")
        val targetParse = invocation.arguments.blockBreakTarget()
        if (targetParse.reason != null) {
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.FAILED,
                message = targetParse.reason,
                data = blockInteractFailure(targetParse.reason),
            )
        }
        val requestedTarget = targetParse.target
        val sideParse = invocation.arguments.blockSide(Direction.UP)
        if (sideParse.reason != null) {
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.FAILED,
                message = sideParse.reason,
                data = blockInteractFailure(sideParse.reason),
            )
        }
        val requestedSide = sideParse.side
        val data =
            context.queryOnClient {
                val player = requireNotNull(player) { "client is not connected to a server" }
                val world = requireNotNull(world) { "client is not connected to a server" }
                val interactionManager =
                    requireNotNull(interactionManager) { "client interaction manager is unavailable" }
                if (requestedTarget != null) {
                    val distance = player.eyePos.distanceTo(Vec3d.ofCenter(requestedTarget.position))
                    require(distance <= maxDistance) { "block target exceeds max-distance" }
                    val target =
                        BlockHitResult(
                            craftlessBlockFaceHitPosition(requestedTarget.position, requestedSide),
                            requestedSide,
                            requestedTarget.position,
                            false,
                        )
                    val adjacentPosition = requestedTarget.position.offset(requestedSide)
                    val targetState = world.getBlockState(requestedTarget.position)
                    val before = world.getBlockState(adjacentPosition)
                    val blockResult = interactionManager.interactBlock(player, Hand.MAIN_HAND, target)
                    val itemResult =
                        if (blockResult.isAccepted) {
                            ActionResult.PASS
                        } else {
                            interactionManager.interactItem(player, Hand.MAIN_HAND)
                        }
                    val accepted = craftlessBlockInteractAccepted(blockResult, itemResult)
                    if (accepted) {
                        player.swingHand(Hand.MAIN_HAND)
                    }
                    val after = world.getBlockState(adjacentPosition)
                    return@queryOnClient target.toCraftlessBlockInteractData(
                        accepted = accepted,
                        changed = before != after,
                        adjacentPosition = adjacentPosition,
                        blockAccepted = blockResult.isAccepted,
                        itemAccepted = itemResult.isAccepted,
                        heldItem = player.mainHandStack,
                        targetState = targetState,
                        adjacentState = after,
                    )
                }
                val camera = requireNotNull(cameraEntity ?: player) { "client is not connected to a server" }
                val target =
                    camera.raycast(maxDistance, 1.0f, includeFluids) as? BlockHitResult
                        ?: error("no block target")
                val targetState = world.getBlockState(target.blockPos)
                val blockResult = interactionManager.interactBlock(player, Hand.MAIN_HAND, target)
                val itemResult =
                    if (blockResult.isAccepted) {
                        ActionResult.PASS
                    } else {
                        interactionManager.interactItem(player, Hand.MAIN_HAND)
                    }
                val accepted = craftlessBlockInteractAccepted(blockResult, itemResult)
                if (accepted) {
                    player.swingHand(Hand.MAIN_HAND)
                }
                target.toCraftlessBlockInteractData(
                    accepted = accepted,
                    blockAccepted = blockResult.isAccepted,
                    itemAccepted = itemResult.isAccepted,
                    heldItem = player.mainHandStack,
                    targetState = targetState,
                )
            }
        return DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            message = "fabric ${context.modeId} action ${invocation.action} accepted",
            data = data,
        )
    }
}

internal fun craftlessBlockFaceHitPosition(
    position: BlockPos,
    side: Direction,
): Vec3d =
    Vec3d(
        position.x + 0.5 + side.offsetX * 0.5,
        position.y + 0.5 + side.offsetY * 0.5,
        position.z + 0.5 + side.offsetZ * 0.5,
    )

internal fun craftlessBlockInteractAccepted(
    blockResult: ActionResult,
    itemResult: ActionResult,
): Boolean = blockResult.isAccepted || itemResult.isAccepted

internal object FabricWorldTimeQueryExecutionAdapter : FabricExecutionAdapter {
    override val operationId: String = FabricBootstrapOperationIds.WORLD_TIME_QUERY

    override fun invoke(
        clientId: String,
        invocation: DriverActionInvocation,
        context: FabricActionContext,
    ): DriverActionResult {
        val data =
            context.queryOnClient {
                val world = requireNotNull(world) { "client is not connected to a server" }
                buildJsonObject {
                    put("time", world.time)
                    put("time-of-day", world.timeOfDay)
                }
            }
        return DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            message = "fabric ${context.modeId} action ${invocation.action} queried",
            data = data,
        )
    }
}

internal object FabricPlayerRaycastExecutionAdapter : FabricExecutionAdapter {
    override val operationId: String = FabricBootstrapOperationIds.PLAYER_RAYCAST

    override fun invoke(
        clientId: String,
        invocation: DriverActionInvocation,
        context: FabricActionContext,
    ): DriverActionResult {
        val maxDistance = invocation.arguments["max-distance"]?.jsonPrimitive?.doubleOrNull ?: DEFAULT_MAX_DISTANCE
        if (maxDistance <= 0.0) {
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.FAILED,
                message = "invalid-max-distance",
                data = actionFailure("invalid-max-distance", "hit"),
            )
        }
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
}

private const val DEFAULT_MAX_DISTANCE = 5.0
private const val DEFAULT_BREAK_TICKS = 80
private const val MAX_BREAK_TICKS = 400
private const val HAND_SWING_PROGRESS_INTERVAL = 4

private fun Map<String, kotlinx.serialization.json.JsonElement>.numberArgument(name: String): Double? =
    this[name]?.jsonPrimitive?.let { primitive ->
        primitive.doubleOrNull ?: primitive.contentOrNull?.toDoubleOrNull()
    }

private fun Map<String, JsonElement>.blockBreakTarget(): CraftlessBlockTargetParse {
    val target = this["target"] as? JsonObject ?: return CraftlessBlockTargetParse(target = null)
    target["handle"]?.jsonPrimitive?.contentOrNull?.let { handle ->
        val position = parseCraftlessBlockHandle(handle) ?: return CraftlessBlockTargetParse("invalid-target-handle")
        return CraftlessBlockTargetParse(target = CraftlessBlockBreakTarget(position, handle))
    }
    val position = target["position"]
    if (position !is JsonObject) {
        return CraftlessBlockTargetParse("invalid-target")
    }
    val blockPosition = position.toBlockPos()
    return CraftlessBlockTargetParse(
        target =
            blockPosition
                ?.let { CraftlessBlockBreakTarget(it, null) },
        reason = if (blockPosition == null) "invalid-target-position" else null,
    )
}

private fun Map<String, JsonElement>.blockSide(default: Direction): CraftlessBlockSideParse {
    val side =
        this["side"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return CraftlessBlockSideParse(side = default)
    val direction = Direction.entries.firstOrNull { direction -> direction.name.equals(side, ignoreCase = true) }
    return CraftlessBlockSideParse(
        side = direction ?: default,
        reason = if (direction == null) "invalid-side" else null,
    )
}

private fun parseCraftlessBlockHandle(handle: String): BlockPos? {
    val parts = handle.split(":")
    if (parts.size != 4 || parts[0] != "world.block") {
        return null
    }
    val x = parts[1].toIntOrNull() ?: return null
    val y = parts[2].toIntOrNull() ?: return null
    val z = parts[3].toIntOrNull() ?: return null
    return BlockPos(x, y, z)
}

private fun JsonObject.toBlockPos(): BlockPos? {
    val x = blockCoordinate("x")
    val y = blockCoordinate("y")
    val z = blockCoordinate("z")
    if (x == null || y == null || z == null) {
        return null
    }
    return BlockPos(x, y, z)
}

private fun JsonObject.blockCoordinate(name: String): Int? =
    this[name]
        ?.jsonPrimitive
        ?.contentOrNull
        ?.toIntOrNull()

private data class CraftlessBlockBreakTarget(
    val position: BlockPos,
    val handle: String?,
)

private data class CraftlessBlockTargetParse(
    val reason: String? = null,
    val target: CraftlessBlockBreakTarget? = null,
)

private data class CraftlessBlockSideParse(
    val side: Direction,
    val reason: String? = null,
)

private fun blockBreakFailure(reason: String): JsonObject =
    buildJsonObject {
        put("started", false)
        put("changed", false)
        put("reason", reason)
    }

private fun blockInteractFailure(reason: String): JsonObject =
    buildJsonObject {
        put("accepted", false)
        put("changed", false)
        put("reason", reason)
    }

internal object FabricScreenQueryExecutionAdapter : FabricExecutionAdapter {
    override val operationId: String = FabricBootstrapOperationIds.SCREEN_QUERY

    override fun invoke(
        clientId: String,
        invocation: DriverActionInvocation,
        context: FabricActionContext,
    ): DriverActionResult {
        val data =
            context.queryOnClient {
                currentScreen.toCraftlessScreenData()
            }
        return DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            message = "fabric ${context.modeId} action ${invocation.action} queried",
            data = data,
        )
    }
}

internal object FabricScreenCloseExecutionAdapter : FabricExecutionAdapter {
    override val operationId: String = FabricBootstrapOperationIds.SCREEN_CLOSE

    override fun invoke(
        clientId: String,
        invocation: DriverActionInvocation,
        context: FabricActionContext,
    ): DriverActionResult {
        context.executeOnClient {
            setScreen(null)
        }
        return DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            message = "fabric ${context.modeId} action ${invocation.action} accepted",
        )
    }
}

private object FabricPlayerChatExecutionAdapter : FabricExecutionAdapter {
    override val operationId: String = FabricBootstrapOperationIds.PLAYER_CHAT

    override fun invoke(
        clientId: String,
        invocation: DriverActionInvocation,
        context: FabricActionContext,
    ): DriverActionResult {
        val message =
            invocation.arguments.stringArgument("message")
                ?: return DriverActionResult(
                    action = invocation.action,
                    status = DriverActionStatus.FAILED,
                    message = "missing-message",
                    data = actionFailure("missing-message", "sent"),
                )
        if (message.isBlank()) {
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.FAILED,
                message = "blank-message",
                data = actionFailure("blank-message", "sent"),
            )
        }
        if (message.startsWith("/")) {
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.FAILED,
                message = "minecraft-command-rejected",
                data = actionFailure("minecraft-command-rejected", "sent"),
            )
        }
        context.record("chat $clientId $message")
        context.executeOnClient {
            val networkHandler = requireNotNull(networkHandler) { "client is not connected to a server" }
            networkHandler.sendChatMessage(message)
        }
        return DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            message = message,
        )
    }
}

private object FabricPlayerMoveExecutionAdapter : FabricExecutionAdapter {
    override val operationId: String = FabricBootstrapOperationIds.PLAYER_MOVE

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
        if (intent.ticks <= 0) {
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.FAILED,
                message = "invalid-ticks",
                data = actionFailure("invalid-ticks", "moved"),
            )
        }
        val data =
            context.queryOnClient {
                val player = requireNotNull(player) { "client is not connected to a server" }
                val telemetry = intent.toCraftlessMovementData(player.pos, player.yaw, player.pitch, player.isOnGround)
                val originalInput = player.input
                player.input =
                    CraftlessMovementInput(
                        originalInput,
                        intent.forward,
                        intent.backward,
                        intent.left,
                        intent.right,
                        intent.jump,
                        intent.sneak,
                        intent.sprint,
                        intent.ticks,
                        {
                            if (player.input is CraftlessMovementInput) {
                                player.input = originalInput
                            }
                        },
                    )
                telemetry
            }
        return DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            message = "fabric ${context.modeId} action ${invocation.action} accepted for ${intent.ticks} tick(s)",
            data = data,
        )
    }
}

private fun actionFailure(
    reason: String,
    flag: String,
): JsonObject =
    buildJsonObject {
        put(flag, false)
        put("reason", reason)
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
    fun toCraftlessMovementData(
        positionBefore: Vec3d,
        yaw: Float,
        pitch: Float,
        onGround: Boolean,
    ): JsonObject =
        buildJsonObject {
            put("ticks", ticks)
            put(
                "input",
                buildJsonObject {
                    put("forward", forward)
                    put("backward", backward)
                    put("left", left)
                    put("right", right)
                    put("jump", jump)
                    put("sneak", sneak)
                    put("sprint", sprint)
                },
            )
            put("position-before", positionBefore.toCraftlessJson())
            put(
                "rotation-before",
                buildJsonObject {
                    put("yaw", yaw)
                    put("pitch", pitch)
                },
            )
            put("on-ground-before", onGround)
        }
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

private data class CraftlessBlockBreakProgress(
    val started: Boolean,
    val changed: Boolean,
    val ticks: Int,
)

private fun ClientPlayerInteractionManager.breakBlockWithProgress(
    player: ClientPlayerEntity,
    world: ClientWorld,
    position: BlockPos,
    side: Direction,
    ticks: Int,
): CraftlessBlockBreakProgress {
    val initialState = world.getBlockState(position)
    val started = attackBlock(position, side)
    if (started) {
        player.swingHand(Hand.MAIN_HAND)
    }
    var usedTicks = 0
    var changed = world.hasBlockChanged(position, initialState)
    while (started && !changed && usedTicks < ticks) {
        usedTicks += 1
        val progressing = updateBlockBreakingProgress(position, side)
        if (progressing && usedTicks % HAND_SWING_PROGRESS_INTERVAL == 0) {
            player.swingHand(Hand.MAIN_HAND)
        }
        changed = world.hasBlockChanged(position, initialState)
        if (!progressing && !isBreakingBlock) {
            break
        }
    }
    if (!changed && isBreakingBlock) {
        cancelBlockBreaking()
    }
    return CraftlessBlockBreakProgress(started = started, changed = changed, ticks = usedTicks)
}

private fun ClientWorld.hasBlockChanged(
    position: BlockPos,
    initialState: BlockState,
): Boolean {
    val currentState = getBlockState(position)
    return currentState.isAir || currentState.block != initialState.block || currentState != initialState
}

private fun BlockHitResult.toCraftlessBlockBreakData(progress: CraftlessBlockBreakProgress): JsonObject =
    buildJsonObject {
        put("hit", true)
        put("target-kind", "block")
        put("started", progress.started)
        put("changed", progress.changed)
        put("ticks", progress.ticks)
        put("block", blockPos.toShortString())
        put("handle", blockPos.toCraftlessBlockHandle())
        put("side", side.name.lowercase())
        put("position", pos.toCraftlessJson())
    }

private fun CraftlessBlockBreakTarget.toCraftlessBlockBreakData(
    progress: CraftlessBlockBreakProgress,
    side: Direction,
): JsonObject =
    buildJsonObject {
        put("hit", true)
        put("target-kind", "block")
        put("started", progress.started)
        put("changed", progress.changed)
        put("ticks", progress.ticks)
        put("block", position.toShortString())
        put("handle", handle ?: position.toCraftlessBlockHandle())
        put("side", side.name.lowercase())
        put("position", position.toCraftlessJson())
    }

private fun BlockHitResult.toCraftlessBlockInteractData(
    accepted: Boolean,
    changed: Boolean? = null,
    adjacentPosition: BlockPos? = null,
    blockAccepted: Boolean? = null,
    itemAccepted: Boolean? = null,
    heldItem: ItemStack? = null,
    targetState: BlockState? = null,
    adjacentState: BlockState? = null,
): JsonObject =
    buildJsonObject {
        put("hit", true)
        put("target-kind", "block")
        put("accepted", accepted)
        put("block", blockPos.toShortString())
        put("handle", blockPos.toCraftlessBlockHandle())
        put("side", side.name.lowercase())
        put("position", pos.toCraftlessJson())
        changed?.let { put("changed", it) }
        blockAccepted?.let { put("block-accepted", it) }
        itemAccepted?.let { put("item-accepted", it) }
        heldItem?.let { stack ->
            put("held-item-empty", stack.isEmpty)
            if (!stack.isEmpty) {
                put("held-item-name", stack.name.string)
                put("held-item-count", stack.count)
            }
        }
        targetState?.let { state ->
            put("target-category", state.toCraftlessBlockCategory())
            put("target-replaceable", state.isReplaceable)
        }
        adjacentPosition?.let { adjacent ->
            put("adjacent-block", adjacent.toShortString())
            put("adjacent-handle", adjacent.toCraftlessBlockHandle())
            put("adjacent-position", adjacent.toCraftlessJson())
        }
        adjacentState?.let { state ->
            put("adjacent-category", state.toCraftlessBlockCategory())
            put("adjacent-replaceable", state.isReplaceable)
        }
    }

private fun BlockState.toCraftlessBlockCategory(): String =
    when {
        isAir -> "air"
        isIn(BlockTags.LOGS) -> "log"
        !fluidState.isEmpty -> "fluid"
        else -> "block"
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

private fun Screen?.toCraftlessScreenData(): JsonObject =
    buildJsonObject {
        put("open", this@toCraftlessScreenData != null)
        this@toCraftlessScreenData?.let { screen ->
            put("title", screen.title.string)
        }
    }

internal fun Vec3d.toCraftlessJson(): JsonObject =
    buildJsonObject {
        put("x", x)
        put("y", y)
        put("z", z)
    }

private fun BlockPos.toCraftlessJson(): JsonObject =
    buildJsonObject {
        put("x", x)
        put("y", y)
        put("z", z)
    }

private fun BlockPos.toCraftlessBlockHandle(): String = "world.block:$x:$y:$z"
