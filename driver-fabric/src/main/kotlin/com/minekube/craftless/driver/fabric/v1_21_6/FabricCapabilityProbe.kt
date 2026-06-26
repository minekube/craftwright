package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.protocol.RuntimeAvailability
import com.minekube.craftless.protocol.RuntimeCapabilityGraph
import com.minekube.craftless.protocol.RuntimeEventNode
import com.minekube.craftless.protocol.RuntimeHandleNode
import com.minekube.craftless.protocol.RuntimeOperationNode
import com.minekube.craftless.protocol.RuntimeResourceNode
import com.minekube.craftless.protocol.RuntimeSchema
import com.minekube.craftless.protocol.RuntimeSourceEvidence

internal fun interface FabricCapabilityDiscovery {
    fun discover(context: FabricCapabilityProbeContext): RuntimeCapabilityGraph
}

internal fun interface FabricCapabilityProbe {
    fun discover(context: FabricCapabilityProbeContext): FabricCapabilityGraphFragment
}

internal data class FabricCapabilityProbeContext(
    val clientId: String,
    val modeId: String,
    val gateway: FabricClientGateway?,
    val runtimeMetadata: DriverRuntimeMetadata = DriverRuntimeMetadata.runtimeAdapter(),
    val bindings: Map<String, FabricActionBinding> = emptyMap(),
)

internal data class FabricCapabilityGraphFragment(
    val resources: List<RuntimeResourceNode> = emptyList(),
    val operations: List<RuntimeOperationNode> = emptyList(),
    val handles: List<RuntimeHandleNode> = emptyList(),
    val events: List<RuntimeEventNode> = emptyList(),
)

internal fun defaultFabricCapabilityDiscovery(
    probes: List<FabricCapabilityProbe> = defaultFabricCapabilityProbes(),
): FabricCapabilityDiscovery =
    FabricCapabilityDiscovery { context ->
        val fragments = probes.map { probe -> probe.discover(context) }
        RuntimeCapabilityGraph(
            clientId = context.clientId,
            resources = fragments.flatMap { it.resources },
            operations = fragments.flatMap { it.operations },
            handles = fragments.flatMap { it.handles },
            events = fragments.flatMap { it.events },
        )
    }

private fun defaultFabricCapabilityProbes(): List<FabricCapabilityProbe> =
    listOf(
        FabricRuntimeMetadataCapabilityProbe,
        FabricClientStateCapabilityProbe,
        FabricNavigationDiscovery(),
    )

internal object FabricRuntimeMetadataCapabilityProbe : FabricCapabilityProbe {
    override fun discover(context: FabricCapabilityProbeContext): FabricCapabilityGraphFragment =
        FabricCapabilityGraphFragment(
            resources =
                listOf(
                    RuntimeResourceNode(
                        id = "runtime",
                        availability = RuntimeAvailability.available(),
                        sourceEvidence =
                            listOf(
                                RuntimeSourceEvidence("installed-mods", context.runtimeMetadata.installedModsFingerprint),
                                RuntimeSourceEvidence("registry", context.runtimeMetadata.registryFingerprint),
                                RuntimeSourceEvidence("server-features", context.runtimeMetadata.serverFeatureFingerprint),
                                RuntimeSourceEvidence("permissions", context.runtimeMetadata.permissionsFingerprint),
                            ),
                    ),
                ),
        )
}

internal object FabricClientStateCapabilityProbe : FabricCapabilityProbe {
    override fun discover(context: FabricCapabilityProbeContext): FabricCapabilityGraphFragment {
        val gateway = context.gateway
        val capabilities =
            if (gateway == null || !gateway.isConnected()) {
                FabricClientCapabilitySnapshot.disconnected()
            } else {
                gateway.queryOnClient {
                    val currentPlayer = player
                    FabricClientCapabilitySnapshot(
                        connected = networkHandler != null && currentPlayer != null,
                        player = currentPlayer != null,
                        inventory = currentPlayer?.inventory != null,
                        camera = cameraEntity != null || currentPlayer != null,
                        interactionManager = interactionManager != null,
                        world = world != null,
                    )
                }
            }
        val screenOpen =
            if (gateway == null || !gateway.isConnected()) {
                false
            } else {
                gateway.queryOnClient { currentScreen != null }
            }
        val playerAvailability = capabilities.playerAvailability()
        val inventoryAvailability = capabilities.inventoryAvailability()
        val cameraAvailability = capabilities.cameraAvailability()
        val worldAvailability = capabilities.worldAvailability()
        val blockBreakAvailability = capabilities.blockBreakAvailability()
        val blockInteractAvailability = capabilities.blockInteractAvailability()
        val screenCloseAvailability =
            if (screenOpen) RuntimeAvailability.available() else RuntimeAvailability.unavailable("screen-not-open")
        val operations =
            listOf(
                context.operation("player.query", "player", "fabric.player-query", playerAvailability),
                context.operation("player.chat", "player", "fabric.player-chat", playerAvailability),
                context.operation("player.look", "player", "fabric.player-look", playerAvailability),
                context.operation("player.move", "player", "fabric.player-move", playerAvailability),
                context.operation("player.raycast", "player", "fabric.player-raycast", cameraAvailability),
                context.operation("inventory.query", "inventory", "fabric.inventory-query", inventoryAvailability),
                context.operation("inventory.equip", "inventory", "fabric.inventory-equip", inventoryAvailability),
                RuntimeOperationNode(
                    id = "entity.query",
                    resource = "entity",
                    adapter = "fabric.entity-query",
                    arguments =
                        mapOf(
                            "radius" to RuntimeSchema("number"),
                            "limit" to RuntimeSchema("integer"),
                        ),
                    result = RuntimeSchema.objectSchema(),
                    availability = capabilities.entityAvailability(),
                ),
                context.operation("world.time.query", "world", "fabric.world-time-query", worldAvailability),
                context.operation("world.block.break", "world", "fabric.world-block-break", blockBreakAvailability),
                context.operation("world.block.interact", "world", "fabric.world-block-interact", blockInteractAvailability),
                context.operation("screen.query", "screen", "fabric.screen-query", RuntimeAvailability.available()),
                context.operation("screen.close", "screen", "fabric.screen-close", screenCloseAvailability),
            )

        return FabricCapabilityGraphFragment(
            resources =
                listOf(
                    RuntimeResourceNode("client", capabilities.connectedAvailability()),
                    RuntimeResourceNode("player", playerAvailability),
                    RuntimeResourceNode("inventory", inventoryAvailability),
                    RuntimeResourceNode("world", worldAvailability),
                    RuntimeResourceNode("entity", capabilities.entityAvailability()),
                    RuntimeResourceNode("screen", RuntimeAvailability.available()),
                ),
            operations = operations,
            handles =
                listOf(
                    RuntimeHandleNode(
                        id = "inventory.slot",
                        resource = "inventory",
                        schema = RuntimeSchema.objectSchema(),
                        availability = inventoryAvailability,
                    ),
                    RuntimeHandleNode(
                        id = "entity.handle",
                        resource = "entity",
                        schema = RuntimeSchema.objectSchema(),
                        availability = capabilities.entityAvailability(),
                    ),
                ),
            events = operations.map { operation -> operation.toEventNode() },
        )
    }
}

private fun FabricCapabilityProbeContext.operation(
    id: String,
    resource: String,
    adapter: String,
    availability: RuntimeAvailability,
): RuntimeOperationNode =
    RuntimeOperationNode(
        id = id,
        resource = resource,
        adapter = adapter,
        arguments =
            actionDescriptorArguments(id)
                ?.mapValues { (_, argument) -> RuntimeSchema(argument.type, required = argument.required) }
                .orEmpty(),
        availability = availability,
    )

private fun FabricCapabilityProbeContext.actionDescriptorArguments(id: String) =
    bindings[id]?.descriptor?.arguments
        ?: fabricBootstrapDescriptor(id)?.arguments

private fun RuntimeOperationNode.toEventNode(): RuntimeEventNode =
    RuntimeEventNode(
        id = id,
        resource = resource,
        payload = RuntimeSchema.objectSchema(),
        availability = availability,
        sourceEvidence = sourceEvidence,
    )

private fun fabricBootstrapDescriptor(id: String) =
    when (id) {
        "player.query" -> fabricPlayerQueryDescriptor()
        "player.look" -> fabricPlayerLookDescriptor()
        "player.raycast" -> fabricRaycastDescriptor()
        "inventory.query" -> fabricInventoryQueryDescriptor()
        "inventory.equip" -> fabricInventoryEquipDescriptor()
        "world.time.query" -> fabricWorldTimeQueryDescriptor()
        "world.block.break" -> fabricWorldBlockBreakDescriptor()
        "world.block.interact" -> fabricWorldBlockInteractDescriptor()
        "screen.query" -> fabricScreenQueryDescriptor()
        "screen.close" -> fabricScreenCloseDescriptor()
        else -> null
    }

private fun FabricClientCapabilitySnapshot.connectedAvailability(): RuntimeAvailability =
    if (connected) RuntimeAvailability.available() else RuntimeAvailability.unavailable("client-not-connected")

private fun FabricClientCapabilitySnapshot.playerAvailability(): RuntimeAvailability = availability(playerReason())

private fun FabricClientCapabilitySnapshot.inventoryAvailability(): RuntimeAvailability = availability(inventoryReason())

private fun FabricClientCapabilitySnapshot.cameraAvailability(): RuntimeAvailability = availability(cameraReason())

private fun FabricClientCapabilitySnapshot.worldAvailability(): RuntimeAvailability = availability(worldReason())

private fun FabricClientCapabilitySnapshot.entityAvailability(): RuntimeAvailability = availability(entityReason())

private fun FabricClientCapabilitySnapshot.blockBreakAvailability(): RuntimeAvailability = availability(blockBreakReason())

private fun FabricClientCapabilitySnapshot.blockInteractAvailability(): RuntimeAvailability = availability(blockInteractReason())

private fun FabricClientCapabilitySnapshot.playerReason(): String? =
    when {
        !connected -> "client-not-connected"
        !player -> "player-unavailable"
        else -> null
    }

private fun FabricClientCapabilitySnapshot.inventoryReason(): String? =
    when {
        !connected -> "client-not-connected"
        !inventory -> "inventory-unavailable"
        else -> null
    }

private fun FabricClientCapabilitySnapshot.cameraReason(): String? =
    when {
        !connected -> "client-not-connected"
        !camera -> "camera-unavailable"
        else -> null
    }

private fun FabricClientCapabilitySnapshot.worldReason(): String? =
    when {
        !connected -> "client-not-connected"
        !world -> "world-unavailable"
        else -> null
    }

private fun FabricClientCapabilitySnapshot.entityReason(): String? =
    playerReason()
        ?: worldReason()

private fun FabricClientCapabilitySnapshot.blockBreakReason(): String? =
    worldReason()
        ?: cameraReason()
        ?: when {
            !interactionManager -> "interaction-unavailable"
            else -> null
        }

private fun FabricClientCapabilitySnapshot.blockInteractReason(): String? =
    playerReason()
        ?: worldReason()
        ?: cameraReason()
        ?: when {
            !interactionManager -> "interaction-unavailable"
            else -> null
        }

private fun availability(reason: String?): RuntimeAvailability =
    if (reason == null) RuntimeAvailability.available() else RuntimeAvailability.unavailable(reason)
