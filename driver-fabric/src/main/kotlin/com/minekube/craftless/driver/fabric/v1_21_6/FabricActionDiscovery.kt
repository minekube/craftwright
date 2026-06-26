package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.driver.api.DriverActionAvailability
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionSource

internal fun interface FabricActionDiscovery {
    fun discover(context: FabricActionDiscoveryContext): List<FabricDiscoveredAction>
}

internal fun interface FabricActionProbe {
    fun discover(context: FabricActionDiscoveryContext): List<FabricDiscoveredAction>
}

internal data class FabricActionDiscoveryContext(
    val clientId: String,
    val modeId: String,
    val gateway: FabricClientGateway?,
    val bindings: Map<String, FabricActionBinding>,
)

internal data class FabricClientCapabilitySnapshot(
    val connected: Boolean,
    val player: Boolean,
    val inventory: Boolean,
    val camera: Boolean,
    val interactionManager: Boolean,
    val world: Boolean,
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

internal data class FabricDiscoveredAction(
    val descriptor: DriverActionDescriptor,
    val binding: FabricActionBinding? = null,
) {
    init {
        if (binding == null) {
            require(
                descriptor.source == DriverActionSource.RUNTIME_PROBE &&
                    descriptor.availability == DriverActionAvailability.UNAVAILABLE,
            ) {
                "discovered action ${descriptor.id} must have a binding or unavailable runtime-probe metadata"
            }
        }
        if (binding != null) {
            require(descriptor.availability == DriverActionAvailability.AVAILABLE) {
                "binding-backed action ${descriptor.id} must be available"
            }
        }
        if (descriptor.source == DriverActionSource.RUNTIME_PROBE) {
            require(descriptor.availability == DriverActionAvailability.UNAVAILABLE || binding != null) {
                "runtime-probe action ${descriptor.id} must have a binding or unavailable reason"
            }
        }
    }
}

internal fun defaultFabricActionDiscovery(probes: List<FabricActionProbe> = defaultFabricActionProbes()): FabricActionDiscovery =
    FabricActionDiscovery { context ->
        probes
            .flatMap { probe -> probe.discover(context) }
            .also { actions -> actions.requireUniqueActionIds() }
    }

private fun defaultFabricActionProbes(): List<FabricActionProbe> =
    listOf(
        BindingBackedFabricActionProbe,
        ScreenFabricActionProbe,
        ConnectedClientFabricActionProbe,
    )

private object BindingBackedFabricActionProbe : FabricActionProbe {
    override fun discover(context: FabricActionDiscoveryContext): List<FabricDiscoveredAction> =
        context.bindings.values.map { binding ->
            FabricDiscoveredAction(
                descriptor =
                    binding.descriptor.copy(
                        source = DriverActionSource.BINDING,
                        availability = DriverActionAvailability.AVAILABLE,
                        availabilityReason = null,
                    ),
                binding = binding,
            )
        }
}

private object ScreenFabricActionProbe : FabricActionProbe {
    override fun discover(context: FabricActionDiscoveryContext): List<FabricDiscoveredAction> =
        if (context.gateway == null) {
            emptyList()
        } else {
            listOf(
                FabricDiscoveredAction(
                    descriptor = FabricScreenQueryActionBinding.descriptor,
                    binding = FabricScreenQueryActionBinding,
                ),
                context.discoverScreenCloseAction(),
            )
        }
}

private object ConnectedClientFabricActionProbe : FabricActionProbe {
    override fun discover(context: FabricActionDiscoveryContext): List<FabricDiscoveredAction> = context.probeConnectedClientActions()
}

private fun FabricActionDiscoveryContext.probeConnectedClientActions(): List<FabricDiscoveredAction> {
    val gateway = gateway ?: return emptyList()
    val capabilities = discoverClientCapabilities(gateway)
    return listOf(
        capabilities.discovered(
            binding = FabricPlayerQueryActionBinding,
            unavailable = ::unavailablePlayerQueryDescriptor,
            reason = capabilities.playerReason(),
        ),
        capabilities.discovered(
            binding = FabricPlayerLookActionBinding,
            unavailable = ::unavailablePlayerLookDescriptor,
            reason = capabilities.playerReason(),
        ),
        capabilities.discovered(
            binding = FabricPlayerRaycastActionBinding,
            unavailable = ::unavailableRaycastDescriptor,
            reason = capabilities.cameraReason(),
        ),
        capabilities.discovered(
            binding = FabricInventoryQueryActionBinding,
            unavailable = ::unavailableInventoryQueryDescriptor,
            reason = capabilities.inventoryReason(),
        ),
        capabilities.discovered(
            binding = FabricInventoryEquipActionBinding,
            unavailable = ::unavailableInventoryEquipDescriptor,
            reason = capabilities.inventoryReason(),
        ),
        capabilities.discovered(
            binding = FabricWorldBlockBreakActionBinding,
            unavailable = ::unavailableWorldBlockBreakDescriptor,
            reason = capabilities.blockBreakReason(),
        ),
        capabilities.discovered(
            binding = FabricWorldBlockInteractActionBinding,
            unavailable = ::unavailableWorldBlockInteractDescriptor,
            reason = capabilities.blockInteractReason(),
        ),
        capabilities.discovered(
            binding = FabricWorldTimeQueryActionBinding,
            unavailable = ::unavailableWorldTimeQueryDescriptor,
            reason = capabilities.worldReason(),
        ),
    )
}

private fun FabricActionDiscoveryContext.discoverClientCapabilities(gateway: FabricClientGateway): FabricClientCapabilitySnapshot =
    if (!gateway.isConnected()) {
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

private fun FabricClientCapabilitySnapshot.discovered(
    binding: FabricActionBinding,
    unavailable: (String) -> DriverActionDescriptor,
    reason: String?,
): FabricDiscoveredAction =
    if (reason == null) {
        FabricDiscoveredAction(
            descriptor = binding.descriptor,
            binding = binding,
        )
    } else {
        FabricDiscoveredAction(descriptor = unavailable(reason))
    }

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

private fun List<FabricDiscoveredAction>.requireUniqueActionIds() {
    val duplicateAction =
        groupBy { it.descriptor.id }
            .entries
            .firstOrNull { (_, matches) -> matches.size > 1 }
    if (duplicateAction != null) {
        throw IllegalArgumentException("duplicate discovered Fabric action id ${duplicateAction.key}")
    }
}

private fun unavailablePlayerQueryDescriptor(reason: String = "client-not-connected"): DriverActionDescriptor =
    fabricPlayerQueryDescriptor().copy(
        source = DriverActionSource.RUNTIME_PROBE,
        availability = DriverActionAvailability.UNAVAILABLE,
        availabilityReason = reason,
    )

private fun unavailablePlayerLookDescriptor(reason: String = "client-not-connected"): DriverActionDescriptor =
    fabricPlayerLookDescriptor().copy(
        source = DriverActionSource.RUNTIME_PROBE,
        availability = DriverActionAvailability.UNAVAILABLE,
        availabilityReason = reason,
    )

private fun unavailableRaycastDescriptor(reason: String = "client-not-connected"): DriverActionDescriptor =
    fabricRaycastDescriptor().copy(
        source = DriverActionSource.RUNTIME_PROBE,
        availability = DriverActionAvailability.UNAVAILABLE,
        availabilityReason = reason,
    )

private fun unavailableInventoryQueryDescriptor(reason: String = "client-not-connected"): DriverActionDescriptor =
    fabricInventoryQueryDescriptor().copy(
        source = DriverActionSource.RUNTIME_PROBE,
        availability = DriverActionAvailability.UNAVAILABLE,
        availabilityReason = reason,
    )

private fun unavailableInventoryEquipDescriptor(reason: String = "client-not-connected"): DriverActionDescriptor =
    fabricInventoryEquipDescriptor().copy(
        source = DriverActionSource.RUNTIME_PROBE,
        availability = DriverActionAvailability.UNAVAILABLE,
        availabilityReason = reason,
    )

private fun unavailableWorldBlockBreakDescriptor(reason: String = "client-not-connected"): DriverActionDescriptor =
    fabricWorldBlockBreakDescriptor().copy(
        source = DriverActionSource.RUNTIME_PROBE,
        availability = DriverActionAvailability.UNAVAILABLE,
        availabilityReason = reason,
    )

private fun unavailableWorldBlockInteractDescriptor(reason: String = "client-not-connected"): DriverActionDescriptor =
    fabricWorldBlockInteractDescriptor().copy(
        source = DriverActionSource.RUNTIME_PROBE,
        availability = DriverActionAvailability.UNAVAILABLE,
        availabilityReason = reason,
    )

private fun unavailableWorldTimeQueryDescriptor(reason: String = "client-not-connected"): DriverActionDescriptor =
    fabricWorldTimeQueryDescriptor().copy(
        source = DriverActionSource.RUNTIME_PROBE,
        availability = DriverActionAvailability.UNAVAILABLE,
        availabilityReason = reason,
    )

private fun FabricActionDiscoveryContext.discoverScreenCloseAction(): FabricDiscoveredAction {
    val gateway = requireNotNull(gateway)
    return if (gateway.queryOnClient { currentScreen != null }) {
        FabricDiscoveredAction(
            descriptor = FabricScreenCloseActionBinding.descriptor,
            binding = FabricScreenCloseActionBinding,
        )
    } else {
        FabricDiscoveredAction(
            descriptor =
                fabricScreenCloseDescriptor().copy(
                    source = DriverActionSource.RUNTIME_PROBE,
                    availability = DriverActionAvailability.UNAVAILABLE,
                    availabilityReason = "screen-not-open",
                ),
        )
    }
}
