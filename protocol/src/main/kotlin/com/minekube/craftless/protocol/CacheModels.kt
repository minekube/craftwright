package com.minekube.craftless.protocol

import kotlinx.serialization.Serializable

@Serializable
data class CachePrepareRequest(
    val minecraftVersion: String,
    val loader: Loader,
) {
    init {
        require(minecraftVersion.isNotBlank()) { "minecraft version is required" }
        require(!minecraftVersion.contains('/')) { "minecraft version must be a file-safe segment" }
        require(!minecraftVersion.contains('\\')) { "minecraft version must use forward slashes" }
        require(!minecraftVersion.contains("..")) { "minecraft version must be a file-safe segment" }
    }
}

@Serializable
data class CachePrepareResult(
    val minecraftVersion: String,
    val loader: Loader,
    val cacheRoot: String,
    val minecraftVersionRoot: String,
    val loaderRoot: String,
    val runtimeRoot: String,
    val manifest: String,
    val status: CachePrepareStatus,
    val artifacts: List<CachePreparedArtifact>,
) {
    companion object {
        fun forRequest(request: CachePrepareRequest): CachePrepareResult {
            val loaderPath = request.loader.name.lowercase()
            val versionManifest = "cache/minecraft/versions/${request.minecraftVersion}/version.json"
            return CachePrepareResult(
                minecraftVersion = request.minecraftVersion,
                loader = request.loader,
                cacheRoot = "cache",
                minecraftVersionRoot = "cache/minecraft/versions/${request.minecraftVersion}",
                loaderRoot = "cache/loaders/$loaderPath/${request.minecraftVersion}",
                runtimeRoot = "cache/runtimes",
                manifest = "cache/prepared/${request.minecraftVersion}-$loaderPath.json",
                status = CachePrepareStatus.PREPARED,
                artifacts =
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
                    ),
            )
        }
    }
}

const val MINECRAFT_VERSION_INDEX_URL: String = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

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
}

@Serializable
enum class CachePreparedArtifactStatus {
    RESOLVED,
}

@Serializable
enum class CachePrepareStatus {
    PREPARED,
}
