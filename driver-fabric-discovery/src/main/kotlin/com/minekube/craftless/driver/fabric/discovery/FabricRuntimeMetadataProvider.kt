package com.minekube.craftless.driver.fabric.discovery

import com.minekube.craftless.driver.api.DriverRuntimeMetadata
import com.minekube.craftless.protocol.RuntimeAvailability
import com.minekube.craftless.protocol.RuntimeResourceNode
import com.minekube.craftless.protocol.RuntimeSourceEvidence
import net.fabricmc.loader.api.FabricLoader
import java.security.MessageDigest

fun interface FabricRuntimeMetadataProvider {
    fun runtimeMetadata(clientId: String): DriverRuntimeMetadata
}

data class FabricRuntimeMetadataSnapshot(
    val loaderVersion: String,
    val driver: String,
    val driverVersion: String,
    val mappings: String,
    val installedModsFingerprint: String,
    val registryFingerprint: String,
    val serverFeatureFingerprint: String,
    val permissionsFingerprint: String = "permissions:local-client",
) {
    init {
        require(loaderVersion.isNotBlank()) { "loader version is required" }
        require(driver.isNotBlank()) { "driver id is required" }
        require(driverVersion.isNotBlank()) { "driver version is required" }
        require(mappings.isNotBlank()) { "mappings fingerprint is required" }
        require(installedModsFingerprint.isNotBlank()) { "installed mods fingerprint is required" }
        require(registryFingerprint.isNotBlank()) { "registry fingerprint is required" }
        require(serverFeatureFingerprint.isNotBlank()) { "server feature fingerprint is required" }
        require(permissionsFingerprint.isNotBlank()) { "permissions fingerprint is required" }
    }
}

class SnapshotFabricRuntimeMetadataProvider(
    private val snapshot: FabricRuntimeMetadataSnapshot,
) : FabricRuntimeMetadataProvider {
    override fun runtimeMetadata(clientId: String): DriverRuntimeMetadata =
        DriverRuntimeMetadata(
            loaderVersion = snapshot.loaderVersion,
            driver = snapshot.driver,
            driverVersion = snapshot.driverVersion,
            mappings = snapshot.mappings,
            installedModsFingerprint = snapshot.installedModsFingerprint,
            registryFingerprint = snapshot.registryFingerprint,
            serverFeatureFingerprint = snapshot.serverFeatureFingerprint,
            permissionsFingerprint = snapshot.permissionsFingerprint,
        )
}

class FabricLoaderRuntimeMetadataReader(
    private val loader: FabricLoader = FabricLoader.getInstance(),
) {
    fun loaderVersion(loaderModId: String = FABRIC_LOADER_ID): String = versionFor(loaderModId) ?: "unknown"

    fun driverVersion(
        driverId: String,
        fallback: String,
    ): String = versionFor(driverId) ?: fallback

    fun installedModsFingerprint(): String = fabricRuntimeFingerprint("mods", installedModCoordinates())

    fun installedModCoordinates(): List<String> =
        loader.allMods.map { container ->
            "${container.metadata.id}@${container.metadata.version.friendlyString}"
        }

    fun isDevelopmentEnvironment(): Boolean =
        try {
            loader.isDevelopmentEnvironment
        } catch (_: NullPointerException) {
            false
        }

    private fun versionFor(modId: String): String? =
        loader
            .getModContainer(modId)
            .map { container -> container.metadata.version.friendlyString }
            .orElse(null)
}

fun fabricRuntimeResourceNode(
    metadata: DriverRuntimeMetadata,
    sourceEvidence: List<RuntimeSourceEvidence> = emptyList(),
): RuntimeResourceNode =
    RuntimeResourceNode(
        id = "runtime",
        availability = RuntimeAvailability.available(),
        sourceEvidence =
            listOf(
                RuntimeSourceEvidence("installed-mods", metadata.installedModsFingerprint),
                RuntimeSourceEvidence("registry", metadata.registryFingerprint),
                RuntimeSourceEvidence("server-features", metadata.serverFeatureFingerprint),
                RuntimeSourceEvidence("permissions", metadata.permissionsFingerprint),
            ) + sourceEvidence,
    )

fun fabricRuntimeFingerprint(
    label: String,
    values: List<String>,
): String {
    require(label.isNotBlank()) { "fingerprint label is required" }
    require(values.isNotEmpty()) { "fingerprint values are required" }
    val digest = MessageDigest.getInstance("SHA-256")
    values.sorted().forEach { value ->
        require(value.isNotBlank()) { "fingerprint value is required" }
        digest.update(value.encodeToByteArray())
        digest.update(0)
    }
    return "$label:" + digest.digest().joinToString("") { byte -> "%02x".format(byte) }.take(FINGERPRINT_LENGTH)
}

private const val FABRIC_LOADER_ID = "fabricloader"
private const val FINGERPRINT_LENGTH = 16
