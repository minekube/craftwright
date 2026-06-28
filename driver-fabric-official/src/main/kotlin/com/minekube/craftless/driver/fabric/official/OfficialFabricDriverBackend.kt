package com.minekube.craftless.driver.fabric.official

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.fabric.discovery.FabricLoaderRuntimeMetadataReader
import com.minekube.craftless.driver.fabric.discovery.FabricRuntimeMetadataProvider
import com.minekube.craftless.driver.fabric.discovery.FabricRuntimeMetadataSnapshot
import com.minekube.craftless.driver.fabric.discovery.FabricRuntimeGraphFragment
import com.minekube.craftless.driver.fabric.discovery.SnapshotFabricRuntimeMetadataProvider
import com.minekube.craftless.driver.fabric.discovery.fabricClientStateGraphFragment
import com.minekube.craftless.driver.fabric.discovery.fabricClientStateWorldTimeQueryOperation
import com.minekube.craftless.driver.fabric.discovery.fabricEventGraphFragment
import com.minekube.craftless.driver.fabric.discovery.fabricRegistryGraphFragment
import com.minekube.craftless.driver.fabric.discovery.fabricRuntimeFingerprint
import com.minekube.craftless.driver.fabric.discovery.fabricRuntimeGraph
import com.minekube.craftless.driver.fabric.discovery.fabricRuntimeMetadataGraphFragment
import com.minekube.craftless.driver.fabric.discovery.toFabricRuntimeEventNode
import com.minekube.craftless.driver.runtime.DriverBackend
import com.minekube.craftless.driver.runtime.DriverBackendAction
import com.minekube.craftless.driver.runtime.DriverBackendResult
import com.minekube.craftless.protocol.RuntimeAvailabilityState
import com.minekube.craftless.protocol.RuntimeSourceEvidence
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class OfficialFabricDriverBackend(
    private val runtimeMetadataProvider: FabricRuntimeMetadataProvider = officialFabricRuntimeMetadataProvider(),
    private val clientStateProvider: OfficialFabricClientStateProvider = MinecraftOfficialFabricClientStateProvider(),
    private val eventSourceProvider: OfficialFabricEventSourceProvider = MinecraftOfficialFabricEventSources,
    private val clientConnector: OfficialFabricClientConnector = MinecraftOfficialFabricClientConnector(),
    private val worldTimeProvider: OfficialFabricWorldTimeProvider = MinecraftOfficialFabricWorldTimeProvider(),
) : DriverBackend {
    override fun connect(
        clientId: String,
        target: ConnectionTarget,
    ): DriverBackendResult {
        val observed = clientConnector.connect(target)
        val outcome =
            if (observed) {
                "scheduled official lane probe connection"
            } else {
                "official lane probe backend could not connect"
            }
        return DriverBackendResult(
            action = DriverBackendAction.CONNECT,
            message = "$outcome $clientId to ${target.host}:${target.port}",
            observed = observed,
        )
    }

    override fun runtimeMetadata(clientId: String): DriverRuntimeMetadata = runtimeMetadataProvider.runtimeMetadata(clientId)

    override fun runtimeGraph(clientId: String) =
        runtimeMetadata(clientId).let { metadata ->
            val clientState = clientStateProvider.snapshot()
            val clientStateOperation =
                fabricClientStateWorldTimeQueryOperation(
                    snapshot = clientState,
                    adapter = WORLD_TIME_QUERY_ADAPTER_KEY,
                )
            val eventSourceEvidence = eventSourceProvider.sourceEvidence()
            fabricRuntimeGraph(
                clientId = clientId,
                fragments =
                    listOf(
                        fabricRuntimeMetadataGraphFragment(
                            metadata = metadata,
                            sourceEvidence =
                                listOf(
                                    RuntimeSourceEvidence("runtime-lane", "latest-current-official"),
                                    RuntimeSourceEvidence("runtime-status", "client-state-probe"),
                                    RuntimeSourceEvidence("runtime-java", "java:25"),
                                ),
                        ),
                        fabricRegistryGraphFragment(
                            metadata = metadata,
                            available = metadata.registryFingerprint != REGISTRIES_NOT_DISCOVERED,
                        ),
                        fabricEventGraphFragment(
                            sourceEvidence = eventSourceEvidence,
                            available = eventSourceEvidence.isNotEmpty(),
                        ),
                        fabricClientStateGraphFragment(clientState),
                        FabricRuntimeGraphFragment(
                            operations = listOf(clientStateOperation),
                            events = listOf(clientStateOperation.toFabricRuntimeEventNode()),
                        ),
                    ),
            )
        }

    override fun invoke(
        clientId: String,
        invocation: DriverActionInvocation,
    ): DriverActionResult {
        require(invocation.action.isNotBlank()) { "action is required" }
        val operation = runtimeGraph(clientId).operations.firstOrNull { operation -> operation.id == invocation.action }
        if (operation == null) {
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.UNSUPPORTED,
                message = "official lane probe backend has no generated runtime operation for ${invocation.action}",
            )
        }
        if (operation.availability.state == RuntimeAvailabilityState.UNAVAILABLE) {
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.UNSUPPORTED,
                message = operation.availability.reason ?: "unavailable official lane operation ${invocation.action}",
            )
        }
        return when (operation.adapter) {
            WORLD_TIME_QUERY_ADAPTER_KEY -> invokeWorldTimeQuery(invocation)
            else ->
                DriverActionResult(
                    action = invocation.action,
                    status = DriverActionStatus.UNSUPPORTED,
                    message = "official lane probe backend has no adapter ${operation.adapter}",
                )
        }
    }

    private fun invokeWorldTimeQuery(invocation: DriverActionInvocation): DriverActionResult {
        val worldTime = worldTimeProvider.query()
        return DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.ACCEPTED,
            message = "official lane action ${invocation.action} queried",
            data =
                buildJsonObject {
                    put("time", worldTime.time)
                    put("time-of-day", worldTime.timeOfDay)
                },
        )
    }

    override fun stop(clientId: String): DriverBackendResult =
        DriverBackendResult(
            action = DriverBackendAction.STOP,
            message = "stopped official lane probe backend for $clientId",
        )
}

internal fun officialFabricRuntimeMetadataProvider(
    registryProvider: OfficialFabricRegistryProvider = MinecraftOfficialFabricRegistryProvider(),
    serverFeatureProvider: OfficialFabricServerFeatureProvider = MinecraftOfficialFabricServerFeatureProvider(),
): FabricRuntimeMetadataProvider {
    val reader = FabricLoaderRuntimeMetadataReader()
    return FabricRuntimeMetadataProvider { clientId ->
        SnapshotFabricRuntimeMetadataProvider(
            FabricRuntimeMetadataSnapshot(
                loaderVersion = reader.loaderVersion(),
                driver = OFFICIAL_FABRIC_DRIVER_ID,
                driverVersion = reader.driverVersion(OFFICIAL_FABRIC_DRIVER_ID, OFFICIAL_FABRIC_DRIVER_VERSION),
                mappings = OFFICIAL_FABRIC_MAPPINGS_FINGERPRINT,
                installedModsFingerprint = reader.installedModsFingerprint(),
                registryFingerprint = fabricRuntimeFingerprint("registries", registryProvider.registryEntries()),
                serverFeatureFingerprint =
                    fabricRuntimeFingerprint(
                        "server-features",
                        listOf("environment:${if (reader.isDevelopmentEnvironment()) "dev" else "runtime"}") +
                            serverFeatureProvider.serverFeatures(),
                    ),
                permissionsFingerprint = "permissions:local-client",
            ),
        ).runtimeMetadata(clientId)
    }
}

private const val OFFICIAL_FABRIC_DRIVER_ID = "craftless-driver-fabric-official"
private const val OFFICIAL_FABRIC_DRIVER_VERSION = "0.1.0-SNAPSHOT"
private const val OFFICIAL_FABRIC_MAPPINGS_FINGERPRINT = "craftless-official-bindings-26-2"
private const val REGISTRIES_NOT_DISCOVERED = "registries:not-discovered"
private const val WORLD_TIME_QUERY_ADAPTER_KEY = "fabric.world-time-query"
