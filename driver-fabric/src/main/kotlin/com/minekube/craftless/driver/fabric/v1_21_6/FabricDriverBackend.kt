package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionDescriptor
import com.minekube.craftless.driver.api.DriverActionInvocation
import com.minekube.craftless.driver.api.DriverActionResult
import com.minekube.craftless.driver.api.DriverActionStatus
import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.driver.runtime.DriverBackend
import com.minekube.craftless.driver.runtime.DriverBackendAction
import com.minekube.craftless.driver.runtime.DriverBackendResult
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import java.security.MessageDigest

class FabricDriverBackend private constructor(
    private val mode: Mode,
    private val gateway: FabricClientGateway?,
    actionBindings: List<FabricActionBinding> = defaultFabricActionBindings(),
    private val actionDiscovery: FabricActionDiscovery = defaultFabricActionDiscovery(),
    private val runtimeMetadataProvider: FabricRuntimeMetadataProvider = staticFabricRuntimeMetadataProvider(),
) : DriverBackend {
    private val events = mutableListOf<String>()
    private val actionBindingsById = actionBindings.associateBy { it.descriptor.id }

    override fun connect(
        clientId: String,
        target: ConnectionTarget,
    ): DriverBackendResult {
        require(target.host.isNotBlank()) { "connection host is required" }
        require(target.port in 1..65535) { "connection port must be between 1 and 65535" }
        record("connect $clientId ${target.host}:${target.port}")
        gateway?.execute {
            gateway.connect(target)
        }
        return DriverBackendResult(DriverBackendAction.CONNECT, "fabric ${mode.id} connect requested")
    }

    override fun actions(clientId: String): List<DriverActionDescriptor> = discoveredActions(clientId).map { it.descriptor }

    override fun runtimeMetadata(clientId: String): DriverRuntimeMetadata = runtimeMetadataProvider.runtimeMetadata(clientId)

    override fun invoke(
        clientId: String,
        invocation: DriverActionInvocation,
    ): DriverActionResult {
        require(invocation.action.isNotBlank()) { "action is required" }
        val discoveredAction = discoveredActions(clientId).firstOrNull { it.descriptor.id == invocation.action }
        if (discoveredAction == null) {
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.UNSUPPORTED,
                message = "unsupported Fabric action ${invocation.action}",
            )
        }
        val binding = discoveredAction.binding
        if (binding == null) {
            return DriverActionResult(
                action = invocation.action,
                status = DriverActionStatus.UNSUPPORTED,
                message = discoveredAction.descriptor.availabilityReason ?: "unavailable Fabric action ${invocation.action}",
            )
        }
        return binding.invoke(
            clientId = clientId,
            invocation = invocation,
            context =
                FabricActionContext(
                    modeId = mode.id,
                    gateway = gateway,
                    record = ::record,
                ),
        )
    }

    override fun stop(clientId: String): DriverBackendResult {
        record("stop $clientId")
        gateway?.execute {
            gateway.stop()
        }
        return DriverBackendResult(DriverBackendAction.STOP, "fabric ${mode.id} stop requested")
    }

    fun events(): List<String> = events.toList()

    private fun discoveredActions(clientId: String): List<FabricDiscoveredAction> =
        actionDiscovery.discover(
            FabricActionDiscoveryContext(
                clientId = clientId,
                modeId = mode.id,
                gateway = gateway,
                bindings = actionBindingsById,
            ),
        )

    private fun record(event: String) {
        events += event
    }

    private enum class Mode(
        val id: String,
    ) {
        METADATA_ONLY("metadata-only"),
        REAL_CLIENT("real-client"),
    }

    companion object {
        @Volatile
        private var installed: FabricDriverBackend? = null

        fun metadataOnly(): FabricDriverBackend = metadataOnly(defaultFabricActionDiscovery())

        internal fun metadataOnly(actionDiscovery: FabricActionDiscovery): FabricDriverBackend =
            FabricDriverBackend(
                mode = Mode.METADATA_ONLY,
                gateway = null,
                actionDiscovery = actionDiscovery,
            )

        fun real(gateway: FabricClientGateway = MinecraftFabricClientGateway()): FabricDriverBackend =
            real(gateway, defaultFabricActionDiscovery())

        internal fun real(
            gateway: FabricClientGateway,
            actionDiscovery: FabricActionDiscovery,
        ): FabricDriverBackend =
            real(
                gateway = gateway,
                actionDiscovery = actionDiscovery,
                runtimeMetadataProvider = FabricLoaderRuntimeMetadataProvider,
            )

        internal fun real(
            gateway: FabricClientGateway,
            actionDiscovery: FabricActionDiscovery = defaultFabricActionDiscovery(),
            runtimeMetadataProvider: FabricRuntimeMetadataProvider,
        ): FabricDriverBackend =
            FabricDriverBackend(
                mode = Mode.REAL_CLIENT,
                gateway = gateway,
                actionDiscovery = actionDiscovery,
                runtimeMetadataProvider = runtimeMetadataProvider,
            )

        fun install(backend: FabricDriverBackend) {
            installed = backend
        }

        fun current(): FabricDriverBackend = installed ?: metadataOnly().also(::install)
    }
}

internal fun interface FabricRuntimeMetadataProvider {
    fun runtimeMetadata(clientId: String): DriverRuntimeMetadata
}

private fun staticFabricRuntimeMetadataProvider(): FabricRuntimeMetadataProvider =
    FabricRuntimeMetadataProvider {
        DriverRuntimeMetadata(
            loaderVersion = "unknown",
            driver = FABRIC_DRIVER_ID,
            driverVersion = FABRIC_DRIVER_VERSION,
            mappings = FABRIC_MAPPINGS_FINGERPRINT,
            installedModsFingerprint = "mods:metadata-only",
            registryFingerprint = "registries:metadata-only",
            serverFeatureFingerprint = "server-features:metadata-only",
            permissionsFingerprint = "permissions:local-client",
        )
    }

internal data class FabricRuntimeMetadataSnapshot(
    val loaderVersion: String,
    val driverVersion: String,
    val installedMods: List<String>,
    val registries: List<String>,
    val serverFeatures: List<String>,
    val permissionsFingerprint: String = "permissions:local-client",
)

internal class SnapshotFabricRuntimeMetadataProvider(
    private val snapshot: FabricRuntimeMetadataSnapshot,
) : FabricRuntimeMetadataProvider {
    override fun runtimeMetadata(clientId: String): DriverRuntimeMetadata =
        DriverRuntimeMetadata(
            loaderVersion = snapshot.loaderVersion,
            driver = FABRIC_DRIVER_ID,
            driverVersion = snapshot.driverVersion,
            mappings = FABRIC_MAPPINGS_FINGERPRINT,
            installedModsFingerprint = fingerprint("mods", snapshot.installedMods),
            registryFingerprint = fingerprint("registries", snapshot.registries),
            serverFeatureFingerprint = fingerprint("server-features", snapshot.serverFeatures),
            permissionsFingerprint = snapshot.permissionsFingerprint,
        )
}

private object FabricLoaderRuntimeMetadataProvider : FabricRuntimeMetadataProvider {
    override fun runtimeMetadata(clientId: String): DriverRuntimeMetadata =
        SnapshotFabricRuntimeMetadataProvider(runtimeMetadataSnapshot()).runtimeMetadata(clientId)

    private fun runtimeMetadataSnapshot(): FabricRuntimeMetadataSnapshot {
        val loader = FabricLoader.getInstance()
        return FabricRuntimeMetadataSnapshot(
            loaderVersion = loader.versionFor(FABRIC_LOADER_ID) ?: "unknown",
            driverVersion = loader.versionFor(FABRIC_DRIVER_ID) ?: FABRIC_DRIVER_VERSION,
            installedMods = loader.installedMods(),
            registries = runtimeRegistryEntries(),
            serverFeatures = listOf("environment:${if (loader.isDevelopmentEnvironment) "dev" else "runtime"}"),
        )
    }
}

private fun FabricLoader.versionFor(modId: String): String? =
    getModContainer(modId)
        .map { it.metadata.version.friendlyString }
        .orElse(null)

private fun FabricLoader.installedMods(): List<String> =
    allMods
        .map { "${it.metadata.id}@${it.metadata.version.friendlyString}" }
        .sorted()

private fun runtimeRegistryEntries(): List<String> =
    listOf(
        registryEntries("block", Registries.BLOCK),
        registryEntries("item", Registries.ITEM),
        registryEntries("entity-type", Registries.ENTITY_TYPE),
        registryEntries("screen-handler", Registries.SCREEN_HANDLER),
        registryEntries("status-effect", Registries.STATUS_EFFECT),
        registryEntries("game-event", Registries.GAME_EVENT),
    ).flatten()

private fun registryEntries(
    label: String,
    registry: Registry<*>,
): List<String> = registry.ids.map { id -> "$label:$id" }

private fun fingerprint(
    label: String,
    values: List<String>,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    values.forEach { value ->
        digest.update(value.encodeToByteArray())
        digest.update(0)
    }
    return "$label:" + digest.digest().joinToString("") { byte -> "%02x".format(byte) }.take(FINGERPRINT_LENGTH)
}

private const val FABRIC_DRIVER_ID = "craftless-driver-fabric"
private const val FABRIC_DRIVER_VERSION = "0.1.0-SNAPSHOT"
private const val FABRIC_LOADER_ID = "fabricloader"
private const val FABRIC_MAPPINGS_FINGERPRINT = "craftless-fabric-bindings"
private const val FINGERPRINT_LENGTH = 16
