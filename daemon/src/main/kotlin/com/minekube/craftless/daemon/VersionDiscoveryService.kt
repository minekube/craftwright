package com.minekube.craftless.daemon

import com.minekube.craftless.protocol.DriverModVersionDescriptor
import com.minekube.craftless.protocol.DriverModVersionListResult
import com.minekube.craftless.protocol.FABRIC_META_BASE_URL
import com.minekube.craftless.protocol.FabricGameVersionDescriptor
import com.minekube.craftless.protocol.FabricGameVersionListResult
import com.minekube.craftless.protocol.FabricLoaderVersionDescriptor
import com.minekube.craftless.protocol.FabricLoaderVersionListResult
import com.minekube.craftless.protocol.FabricSupportReason
import com.minekube.craftless.protocol.FabricSupportRuntimeTargetDescriptor
import com.minekube.craftless.protocol.FabricSupportTargetDescriptor
import com.minekube.craftless.protocol.FabricSupportTargetListResult
import com.minekube.craftless.protocol.Loader
import com.minekube.craftless.protocol.MINECRAFT_VERSION_INDEX_URL
import com.minekube.craftless.protocol.MinecraftVersionListResult
import com.minekube.craftless.protocol.minecraftVersionList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class VersionDiscoveryService(
    private val metadataFetcher: CacheMetadataFetcher = KtorCacheMetadataFetcher(),
    private val driverModProvider: ClientRuntimeDriverModProvider = ConfiguredClientRuntimeDriverModProvider(),
) {
    suspend fun listMinecraftVersions(): MinecraftVersionListResult =
        metadataFetcher
            .fetchText(MINECRAFT_VERSION_INDEX_URL)
            .minecraftVersionList()

    suspend fun listFabricGameVersions(): FabricGameVersionListResult =
        FabricGameVersionListResult(
            versions =
                metadataFetcher
                    .fetchText("$FABRIC_META_BASE_URL/versions/game")
                    .parseFabricGameVersions(),
        )

    suspend fun listFabricLoaderVersions(): FabricLoaderVersionListResult =
        FabricLoaderVersionListResult(
            versions =
                metadataFetcher
                    .fetchText("$FABRIC_META_BASE_URL/versions/loader")
                    .parseFabricLoaderVersions(),
        )

    fun listDriverModVersions(): DriverModVersionListResult = driverModProvider.driverModVersions()

    suspend fun listFabricSupportTargets(): FabricSupportTargetListResult {
        val gameVersions = listFabricGameVersions().versions
        val loaderVersions = listFabricLoaderVersions().versions
        val driverMods = listDriverModVersions()
        val fabricDriverModsByMinecraftVersion =
            driverMods
                .entries
                .filter { entry -> entry.loader == Loader.FABRIC }
                .groupBy { entry -> entry.minecraftVersion }
        return FabricSupportTargetListResult(
            source = driverMods.source,
            targets =
                gameVersions.map { version ->
                    val matches = fabricDriverModsByMinecraftVersion[version.version].orEmpty()
                    val runtimeTargets = matches.runtimeTargets(loaderVersions)
                    FabricSupportTargetDescriptor(
                        minecraftVersion = version.version,
                        stable = version.stable,
                        supported = runtimeTargets.any { it.supported },
                        reason = if (runtimeTargets.none { it.supported }) matches.unsupportedReason() else null,
                        driverMods = matches,
                        runtimeTargets = runtimeTargets,
                    )
                },
        )
    }
}

private fun List<DriverModVersionDescriptor>.runtimeTargets(
    loaderVersions: List<FabricLoaderVersionDescriptor>,
): List<FabricSupportRuntimeTargetDescriptor> {
    val driverModsByLoaderVersion = filter { driverMod -> driverMod.loader == Loader.FABRIC }.groupBy { it.loaderVersion }
    val runtimeTargets =
        loaderVersions.map { loaderVersion ->
            val driverMod = driverModsByLoaderVersion[loaderVersion.version].orEmpty().firstOrNull()
            if (driverMod == null) {
                return@map FabricSupportRuntimeTargetDescriptor(
                    loaderVersion = loaderVersion.version,
                    loaderStable = loaderVersion.stable,
                    supported = false,
                    reason = unsupportedReason(),
                )
            }
            FabricSupportRuntimeTargetDescriptor(
                loader = driverMod.loader,
                loaderVersion = driverMod.loaderVersion,
                loaderStable = loaderVersion.stable,
                javaMajorVersion = driverMod.javaMajorVersion,
                mappingsFingerprint = driverMod.mappingsFingerprint,
                supported = true,
                driverMod = driverMod,
            )
        }
    val loaderVersionSet = loaderVersions.map { it.version }.toSet()
    val manifestOnlyTargets =
        filter { driverMod -> driverMod.loaderVersion !in loaderVersionSet }
            .map { driverMod ->
                FabricSupportRuntimeTargetDescriptor(
                    loader = driverMod.loader,
                    loaderVersion = driverMod.loaderVersion,
                    javaMajorVersion = driverMod.javaMajorVersion,
                    mappingsFingerprint = driverMod.mappingsFingerprint,
                    supported = true,
                    driverMod = driverMod,
                )
            }
    return runtimeTargets + manifestOnlyTargets
}

private fun List<DriverModVersionDescriptor>.unsupportedReason(): FabricSupportReason =
    if (isEmpty()) {
        FabricSupportReason.NO_DRIVER_MOD
    } else {
        FabricSupportReason.NO_COMPATIBLE_DRIVER_MOD
    }

private fun String.parseFabricGameVersions(): List<FabricGameVersionDescriptor> =
    Json
        .parseToJsonElement(this)
        .jsonArray
        .map { element ->
            val item = element.jsonObject
            FabricGameVersionDescriptor(
                version = item["version"]?.jsonPrimitive?.content ?: error("Fabric game version entry is missing version"),
                stable = item["stable"]?.jsonPrimitive?.boolean ?: false,
            )
        }

private fun String.parseFabricLoaderVersions(): List<FabricLoaderVersionDescriptor> =
    Json
        .parseToJsonElement(this)
        .jsonArray
        .map { element ->
            val loader = element.jsonObject["loader"]?.jsonObject ?: element.jsonObject
            FabricLoaderVersionDescriptor(
                version = loader["version"]?.jsonPrimitive?.content ?: error("Fabric loader entry is missing version"),
                stable = loader["stable"]?.jsonPrimitive?.boolean ?: false,
            )
        }
