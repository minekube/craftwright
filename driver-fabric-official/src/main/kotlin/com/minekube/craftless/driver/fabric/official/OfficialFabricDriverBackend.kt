package com.minekube.craftless.driver.fabric.official

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.fabric.discovery.FabricLoaderRuntimeMetadataReader
import com.minekube.craftless.driver.fabric.discovery.FabricRuntimeMetadataProvider
import com.minekube.craftless.driver.fabric.discovery.FabricRuntimeMetadataSnapshot
import com.minekube.craftless.driver.fabric.discovery.SnapshotFabricRuntimeMetadataProvider
import com.minekube.craftless.driver.fabric.discovery.fabricRuntimeMetadataGraph
import com.minekube.craftless.driver.runtime.DriverBackend
import com.minekube.craftless.driver.runtime.DriverBackendAction
import com.minekube.craftless.driver.runtime.DriverBackendResult
import com.minekube.craftless.protocol.RuntimeSourceEvidence

internal class OfficialFabricDriverBackend(
    private val runtimeMetadataProvider: FabricRuntimeMetadataProvider = officialFabricRuntimeMetadataProvider(),
) : DriverBackend {
    override fun connect(
        clientId: String,
        target: ConnectionTarget,
    ): DriverBackendResult =
        DriverBackendResult(
            action = DriverBackendAction.CONNECT,
            message = "official lane metadata-only backend cannot connect $clientId to ${target.host}:${target.port}",
            observed = false,
        )

    override fun runtimeMetadata(clientId: String): DriverRuntimeMetadata = runtimeMetadataProvider.runtimeMetadata(clientId)

    override fun runtimeGraph(clientId: String) =
        fabricRuntimeMetadataGraph(
            clientId = clientId,
            metadata = runtimeMetadata(clientId),
            sourceEvidence =
                listOf(
                    RuntimeSourceEvidence("runtime-lane", "latest-current-official"),
                    RuntimeSourceEvidence("runtime-status", "metadata-only"),
                    RuntimeSourceEvidence("runtime-java", "java:25"),
                ),
        )

    override fun invoke(
        clientId: String,
        invocation: DriverActionInvocation,
    ): DriverActionResult =
        DriverActionResult(
            action = invocation.action,
            status = DriverActionStatus.UNSUPPORTED,
            message = "official lane metadata-only backend has no generated runtime operation for ${invocation.action}",
        )

    override fun stop(clientId: String): DriverBackendResult =
        DriverBackendResult(
            action = DriverBackendAction.STOP,
            message = "stopped official lane metadata-only backend for $clientId",
        )
}

private fun officialFabricRuntimeMetadataProvider(): FabricRuntimeMetadataProvider {
    val reader = FabricLoaderRuntimeMetadataReader()
    return FabricRuntimeMetadataProvider { clientId ->
        SnapshotFabricRuntimeMetadataProvider(
            FabricRuntimeMetadataSnapshot(
                loaderVersion = reader.loaderVersion(),
                driver = OFFICIAL_FABRIC_DRIVER_ID,
                driverVersion = reader.driverVersion(OFFICIAL_FABRIC_DRIVER_ID, OFFICIAL_FABRIC_DRIVER_VERSION),
                mappings = OFFICIAL_FABRIC_MAPPINGS_FINGERPRINT,
                installedModsFingerprint = reader.installedModsFingerprint(),
                registryFingerprint = "registries:not-discovered",
                serverFeatureFingerprint = "server-features:not-connected",
                permissionsFingerprint = "permissions:local-client",
            ),
        ).runtimeMetadata(clientId)
    }
}

private const val OFFICIAL_FABRIC_DRIVER_ID = "craftless-driver-fabric-official"
private const val OFFICIAL_FABRIC_DRIVER_VERSION = "0.1.0-SNAPSHOT"
private const val OFFICIAL_FABRIC_MAPPINGS_FINGERPRINT = "craftless-official-bindings-26-2"
