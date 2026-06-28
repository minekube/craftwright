package com.minekube.craftless.driver.fabric.discovery

import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.protocol.RuntimeCapabilityGraph
import com.minekube.craftless.protocol.RuntimeEventNode
import com.minekube.craftless.protocol.RuntimeHandleNode
import com.minekube.craftless.protocol.RuntimeOperationNode
import com.minekube.craftless.protocol.RuntimeResourceNode
import com.minekube.craftless.protocol.RuntimeSourceEvidence

data class FabricRuntimeGraphFragment(
    val resources: List<RuntimeResourceNode> = emptyList(),
    val operations: List<RuntimeOperationNode> = emptyList(),
    val handles: List<RuntimeHandleNode> = emptyList(),
    val events: List<RuntimeEventNode> = emptyList(),
)

fun fabricRuntimeGraph(
    clientId: String,
    fragments: List<FabricRuntimeGraphFragment>,
): RuntimeCapabilityGraph =
    RuntimeCapabilityGraph(
        clientId = clientId,
        resources = fragments.flatMap { it.resources },
        operations = fragments.flatMap { it.operations },
        handles = fragments.flatMap { it.handles },
        events = fragments.flatMap { it.events },
    )

fun fabricRuntimeMetadataGraph(
    clientId: String,
    metadata: DriverRuntimeMetadata,
    sourceEvidence: List<RuntimeSourceEvidence> = emptyList(),
): RuntimeCapabilityGraph =
    fabricRuntimeGraph(
        clientId = clientId,
        fragments =
            listOf(
                FabricRuntimeGraphFragment(
                    resources =
                        listOf(
                            fabricRuntimeResourceNode(
                                metadata = metadata,
                                sourceEvidence = sourceEvidence,
                            ),
                        ),
                ),
            ),
    )
