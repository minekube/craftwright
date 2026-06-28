package com.minekube.craftless.driver.fabric.discovery

import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.protocol.RuntimeAvailability
import com.minekube.craftless.protocol.RuntimeHandleNode
import com.minekube.craftless.protocol.RuntimeResourceNode
import com.minekube.craftless.protocol.RuntimeSchema
import com.minekube.craftless.protocol.RuntimeSourceEvidence

fun fabricRegistryGraphFragment(
    metadata: DriverRuntimeMetadata,
    available: Boolean,
): FabricRuntimeGraphFragment {
    val availability =
        if (available && metadata.registryFingerprint != REGISTRIES_NOT_DISCOVERED) {
            RuntimeAvailability.available()
        } else {
            RuntimeAvailability.unavailable("registry-not-discovered")
        }
    val evidence = listOf(RuntimeSourceEvidence("registry", metadata.registryFingerprint))
    return FabricRuntimeGraphFragment(
        resources =
            listOf(
                RuntimeResourceNode(
                    id = "registry",
                    availability = availability,
                    sourceEvidence = evidence,
                ),
            ),
        handles =
            fabricRegistryHandleIds.map { handleId ->
                RuntimeHandleNode(
                    id = handleId,
                    resource = "registry",
                    schema = RuntimeSchema.objectSchema(),
                    availability = availability,
                    sourceEvidence = evidence,
                )
            },
    )
}

private val fabricRegistryHandleIds =
    listOf(
        "registry.block",
        "registry.item",
        "registry.entity",
        "registry.screen",
        "registry.effect",
        "registry.event",
    )

private const val REGISTRIES_NOT_DISCOVERED = "registries:not-discovered"
