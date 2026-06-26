package com.minekube.craftless.protocol

import kotlinx.serialization.Serializable

@Serializable
data class CachePrepareRequest(
    val minecraftVersion: String,
    val loader: Loader,
    val loaderVersion: String? = null,
) {
    init {
        requireFileSafeSegment(minecraftVersion, "minecraft version")
        loaderVersion?.let { requireFileSafeSegment(it, "loader version") }
    }
}

@Serializable
data class CachePrepareResult(
    val minecraftVersion: String,
    val loader: Loader,
    val loaderVersion: String? = null,
    val cacheRoot: String,
    val minecraftVersionRoot: String,
    val loaderRoot: String,
    val runtimeRoot: String,
    val manifest: String,
    val status: CachePrepareStatus,
    val artifacts: List<CachePreparedArtifact>,
) {
    companion object {
        fun forRequest(
            request: CachePrepareRequest,
            resolvedLoaderVersion: String? = request.loaderVersion,
        ): CachePrepareResult {
            val loaderPath = request.loader.name.lowercase()
            val loaderRoot =
                listOfNotNull(
                    "cache/loaders/$loaderPath/${request.minecraftVersion}",
                    resolvedLoaderVersion,
                ).joinToString("/")
            val manifestVersionPart = resolvedLoaderVersion?.let { "-$it" }.orEmpty()
            val versionManifest = "cache/minecraft/versions/${request.minecraftVersion}/version.json"
            val artifacts =
                listOf(
                    CachePreparedArtifact(
                        kind = CachePreparedArtifactKind.MINECRAFT_VERSION_INDEX,
                        handle = "cache/minecraft/version_manifest_v2.json",
                        source = MINECRAFT_VERSION_INDEX_URL,
                        status = CachePreparedArtifactStatus.RESOLVED,
                    ),
                    CachePreparedArtifact(
                        kind = CachePreparedArtifactKind.MINECRAFT_VERSION_MANIFEST,
                        handle = versionManifest,
                        status = CachePreparedArtifactStatus.RESOLVED,
                    ),
                ) +
                    if (request.loader == Loader.FABRIC) {
                        listOf(
                            CachePreparedArtifact(
                                kind = CachePreparedArtifactKind.FABRIC_LOADER_VERSIONS,
                                handle = "cache/loaders/$loaderPath/${request.minecraftVersion}/versions.json",
                                source = "$FABRIC_META_BASE_URL/versions/loader/${request.minecraftVersion}",
                                status = CachePreparedArtifactStatus.RESOLVED,
                            ),
                            CachePreparedArtifact(
                                kind = CachePreparedArtifactKind.FABRIC_LOADER_PROFILE,
                                handle = "$loaderRoot/profile.json",
                                status = CachePreparedArtifactStatus.RESOLVED,
                            ),
                        )
                    } else {
                        emptyList()
                    }
            return CachePrepareResult(
                minecraftVersion = request.minecraftVersion,
                loader = request.loader,
                loaderVersion = resolvedLoaderVersion,
                cacheRoot = "cache",
                minecraftVersionRoot = "cache/minecraft/versions/${request.minecraftVersion}",
                loaderRoot = loaderRoot,
                runtimeRoot = "cache/runtimes",
                manifest = "cache/prepared/${request.minecraftVersion}-$loaderPath$manifestVersionPart.json",
                status = CachePrepareStatus.PREPARED,
                artifacts = artifacts,
            )
        }
    }
}

const val MINECRAFT_VERSION_INDEX_URL: String = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
const val FABRIC_META_BASE_URL: String = "https://meta.fabricmc.net/v2"

@Serializable
data class CachePreparedArtifact(
    val kind: CachePreparedArtifactKind,
    val handle: String,
    val source: String? = null,
    val status: CachePreparedArtifactStatus,
)

@Serializable
enum class CachePreparedArtifactKind {
    MINECRAFT_VERSION_INDEX,
    MINECRAFT_VERSION_MANIFEST,
    FABRIC_LOADER_VERSIONS,
    FABRIC_LOADER_PROFILE,
}

@Serializable
enum class CachePreparedArtifactStatus {
    RESOLVED,
}

@Serializable
enum class CachePrepareStatus {
    PREPARED,
}

private fun requireFileSafeSegment(
    value: String,
    label: String,
) {
    require(value.isNotBlank()) { "$label is required" }
    require(!value.contains('/')) { "$label must be a file-safe segment" }
    require(!value.contains('\\')) { "$label must use forward slashes" }
    require(!value.contains("..")) { "$label must be a file-safe segment" }
}
