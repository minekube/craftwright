package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.driver.api.DriverActionAvailability
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionSource

internal fun interface FabricActionDiscovery {
    fun discover(context: FabricActionDiscoveryContext): List<FabricDiscoveredAction>
}

internal data class FabricActionDiscoveryContext(
    val clientId: String,
    val modeId: String,
    val gateway: FabricClientGateway?,
    val bindings: Map<String, FabricActionBinding>,
)

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

internal fun defaultFabricActionDiscovery(): FabricActionDiscovery =
    FabricActionDiscovery { context ->
        val bindingBackedActions =
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
        bindingBackedActions + context.probeUnavailableActions()
    }

private fun FabricActionDiscoveryContext.probeUnavailableActions(): List<FabricDiscoveredAction> {
    val gateway = gateway ?: return emptyList()
    return if (gateway.isConnected()) {
        listOf(
            FabricDiscoveredAction(
                descriptor = FabricPlayerRaycastActionBinding.descriptor,
                binding = FabricPlayerRaycastActionBinding,
            ),
            FabricDiscoveredAction(
                descriptor = FabricInventoryQueryActionBinding.descriptor,
                binding = FabricInventoryQueryActionBinding,
            ),
        )
    } else {
        listOf(
            FabricDiscoveredAction(
                descriptor = unavailableRaycastDescriptor(),
            ),
            FabricDiscoveredAction(
                descriptor = unavailableInventoryQueryDescriptor(),
            ),
        )
    }
}

private fun unavailableRaycastDescriptor(): DriverActionDescriptor =
    fabricRaycastDescriptor().copy(
        source = DriverActionSource.RUNTIME_PROBE,
        availability = DriverActionAvailability.UNAVAILABLE,
        availabilityReason = "client-not-connected",
    )

private fun unavailableInventoryQueryDescriptor(): DriverActionDescriptor =
    fabricInventoryQueryDescriptor().copy(
        source = DriverActionSource.RUNTIME_PROBE,
        availability = DriverActionAvailability.UNAVAILABLE,
        availabilityReason = "client-not-connected",
    )
