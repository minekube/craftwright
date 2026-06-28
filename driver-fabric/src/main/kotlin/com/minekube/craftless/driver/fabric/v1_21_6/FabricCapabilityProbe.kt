package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.fabric.discovery.fabricRuntimeResourceNode
import com.minekube.craftless.driver.fabric.runtime.FabricCompatibilityLane
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
    val compatibilityLane: FabricCompatibilityLane? = null,
)

internal data class FabricCapabilityGraphFragment(
    val resources: List<RuntimeResourceNode> = emptyList(),
    val operations: List<RuntimeOperationNode> = emptyList(),
    val handles: List<RuntimeHandleNode> = emptyList(),
    val events: List<RuntimeEventNode> = emptyList(),
)

internal data class FabricClientCapabilitySnapshot(
    val connected: Boolean,
    val player: Boolean,
    val inventory: Boolean,
    val camera: Boolean,
    val interactionManager: Boolean,
    val world: Boolean,
    val recipes: Boolean = false,
    val recipeCrafting: Boolean = false,
) {
    companion object {
        fun disconnected(): FabricClientCapabilitySnapshot =
            FabricClientCapabilitySnapshot(
                connected = false,
                player = false,
                inventory = false,
                camera = false,
                interactionManager = false,
                world = false,
            )
    }
}

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
        FabricRegistrySummaryCapabilityProbe,
        FabricEventSourceCapabilityProbe,
        FabricClientStateCapabilityProbe,
        FabricNavigationDiscovery(),
    )

internal object FabricRuntimeMetadataCapabilityProbe : FabricCapabilityProbe {
    override fun discover(context: FabricCapabilityProbeContext): FabricCapabilityGraphFragment =
        FabricCapabilityGraphFragment(
            resources =
                listOf(
                    fabricRuntimeResourceNode(
                        metadata = context.runtimeMetadata,
                        sourceEvidence = context.compatibilityLane.sourceEvidence(),
                    ),
                ),
        )
}

private fun FabricCompatibilityLane?.sourceEvidence(): List<RuntimeSourceEvidence> = this?.sourceEvidence().orEmpty()

internal object FabricRegistrySummaryCapabilityProbe : FabricCapabilityProbe {
    override fun discover(context: FabricCapabilityProbeContext): FabricCapabilityGraphFragment {
        val registryEvidence = RuntimeSourceEvidence("registry", context.runtimeMetadata.registryFingerprint)
        return FabricCapabilityGraphFragment(
            resources =
                listOf(
                    RuntimeResourceNode(
                        id = "registry",
                        availability = RuntimeAvailability.available(),
                        sourceEvidence = listOf(registryEvidence),
                    ),
                ),
            handles =
                registrySummaryHandles.map { id ->
                    RuntimeHandleNode(
                        id = "registry.$id",
                        resource = "registry",
                        schema = RuntimeSchema.objectSchema(),
                        availability = RuntimeAvailability.available(),
                        sourceEvidence = listOf(registryEvidence),
                    )
                },
        )
    }
}

internal object FabricEventSourceCapabilityProbe : FabricCapabilityProbe {
    override fun discover(context: FabricCapabilityProbeContext): FabricCapabilityGraphFragment {
        val eventSourceEvidence =
            listOf(RuntimeSourceEvidence("event-source", "driver:${context.runtimeMetadata.driverVersion}")) +
                FabricEventHooks.sourceEvidence() +
                FabricEventCallbacks.sourceEvidence()
        return FabricCapabilityGraphFragment(
            resources =
                listOf(
                    RuntimeResourceNode(
                        id = "event",
                        availability = RuntimeAvailability.available(),
                        sourceEvidence = eventSourceEvidence,
                    ),
                ),
            events =
                eventSourceIds.map { id ->
                    RuntimeEventNode(
                        id = "event.$id",
                        resource = "event",
                        payload = RuntimeSchema.objectSchema(),
                        availability = RuntimeAvailability.available(),
                        sourceEvidence = eventSourceEvidence,
                    )
                },
        )
    }
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
                        recipes = currentPlayer?.recipeBook != null && networkHandler?.recipeManager != null,
                        recipeCrafting = interactionManager != null && currentPlayer?.currentScreenHandler != null,
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
        val recipeQueryAvailability = capabilities.recipeQueryAvailability()
        val recipeCraftAvailability = capabilities.recipeCraftAvailability()
        val blockQueryAvailability = capabilities.blockQueryAvailability()
        val operations =
            fabricBootstrapOperationDefinitions().map { definition ->
                definition.toRuntimeOperation(
                    capabilities.bootstrapAvailability(
                        kind = definition.availability,
                        screenOpen = screenOpen,
                    ),
                )
            }

        return FabricCapabilityGraphFragment(
            resources =
                listOf(
                    RuntimeResourceNode("client", capabilities.connectedAvailability()),
                    RuntimeResourceNode("player", playerAvailability),
                    RuntimeResourceNode("inventory", inventoryAvailability),
                    RuntimeResourceNode("recipe", recipeQueryAvailability),
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
                        id = "recipe.handle",
                        resource = "recipe",
                        schema = RuntimeSchema.objectSchema(),
                        availability = recipeQueryAvailability,
                    ),
                    RuntimeHandleNode(
                        id = "world.block.handle",
                        resource = "world",
                        schema = RuntimeSchema.objectSchema(),
                        availability = blockQueryAvailability,
                    ),
                    RuntimeHandleNode(
                        id = "entity.handle",
                        resource = "entity",
                        schema = RuntimeSchema.objectSchema(),
                        availability = capabilities.entityAvailability(),
                    ),
                ),
            events = operations.map { operation -> operation.toFabricEventNode() },
        )
    }
}

private fun FabricClientCapabilitySnapshot.connectedAvailability(): RuntimeAvailability =
    if (connected) RuntimeAvailability.available() else RuntimeAvailability.unavailable("client-not-connected")

private fun FabricClientCapabilitySnapshot.playerAvailability(): RuntimeAvailability = availability(playerReason())

private fun FabricClientCapabilitySnapshot.inventoryAvailability(): RuntimeAvailability = availability(inventoryReason())

private fun FabricClientCapabilitySnapshot.recipeQueryAvailability(): RuntimeAvailability = availability(recipeQueryReason())

private fun FabricClientCapabilitySnapshot.recipeCraftAvailability(): RuntimeAvailability = availability(recipeCraftReason())

private fun FabricClientCapabilitySnapshot.cameraAvailability(): RuntimeAvailability = availability(cameraReason())

private fun FabricClientCapabilitySnapshot.worldAvailability(): RuntimeAvailability = availability(worldReason())

private fun FabricClientCapabilitySnapshot.entityAvailability(): RuntimeAvailability = availability(entityReason())

private fun FabricClientCapabilitySnapshot.entityAttackAvailability(): RuntimeAvailability = availability(entityAttackReason())

private fun FabricClientCapabilitySnapshot.blockQueryAvailability(): RuntimeAvailability = availability(blockQueryReason())

private fun FabricClientCapabilitySnapshot.blockBreakAvailability(): RuntimeAvailability = availability(blockBreakReason())

private fun FabricClientCapabilitySnapshot.blockInteractAvailability(): RuntimeAvailability = availability(blockInteractReason())

private fun FabricClientCapabilitySnapshot.bootstrapAvailability(
    kind: FabricBootstrapOperationAvailabilityKind,
    screenOpen: Boolean,
): RuntimeAvailability =
    when (kind) {
        FabricBootstrapOperationAvailabilityKind.PLAYER -> playerAvailability()
        FabricBootstrapOperationAvailabilityKind.CAMERA -> cameraAvailability()
        FabricBootstrapOperationAvailabilityKind.INVENTORY -> inventoryAvailability()
        FabricBootstrapOperationAvailabilityKind.RECIPE_QUERY -> recipeQueryAvailability()
        FabricBootstrapOperationAvailabilityKind.RECIPE_CRAFT -> recipeCraftAvailability()
        FabricBootstrapOperationAvailabilityKind.ENTITY -> entityAvailability()
        FabricBootstrapOperationAvailabilityKind.ENTITY_ATTACK -> entityAttackAvailability()
        FabricBootstrapOperationAvailabilityKind.BLOCK_QUERY -> blockQueryAvailability()
        FabricBootstrapOperationAvailabilityKind.WORLD -> worldAvailability()
        FabricBootstrapOperationAvailabilityKind.BLOCK_BREAK -> blockBreakAvailability()
        FabricBootstrapOperationAvailabilityKind.BLOCK_INTERACT -> blockInteractAvailability()
        FabricBootstrapOperationAvailabilityKind.SCREEN -> RuntimeAvailability.available()
        FabricBootstrapOperationAvailabilityKind.SCREEN_CLOSE ->
            if (screenOpen) RuntimeAvailability.available() else RuntimeAvailability.unavailable("screen-not-open")
    }

private val registrySummaryHandles =
    listOf(
        "block",
        "item",
        "entity",
        "screen",
        "effect",
        "event",
    )

private val eventSourceIds =
    listOf(
        "lifecycle",
        "action",
        "capability",
    )

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

private fun FabricClientCapabilitySnapshot.recipeQueryReason(): String? =
    playerReason()
        ?: inventoryReason()
        ?: if (!recipes) "recipe-discovery-unavailable" else null

private fun FabricClientCapabilitySnapshot.recipeCraftReason(): String? =
    recipeQueryReason()
        ?: when {
            !interactionManager -> "interaction-manager-unavailable"
            !recipeCrafting -> "recipe-context-unavailable"
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

private fun FabricClientCapabilitySnapshot.entityAttackReason(): String? =
    playerReason()
        ?: worldReason()
        ?: when {
            !interactionManager -> "interaction-unavailable"
            else -> null
        }

private fun FabricClientCapabilitySnapshot.blockQueryReason(): String? =
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
