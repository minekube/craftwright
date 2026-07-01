package com.minekube.craftless.protocol

import kotlinx.serialization.Serializable

@Serializable
data class CachePrepareRequest(
    val minecraftVersion: String = DEFAULT_MINECRAFT_VERSION,
    val loader: Loader,
    val loaderVersion: String? = null,
    val java: JavaRuntimeRequirement? = null,
) {
    init {
        requireFileSafeSegment(minecraftVersion, "minecraft version")
        loaderVersion?.let { requireFileSafeSegment(it, "loader version") }
    }
}

@Serializable
data class CacheExportRequest(
    val manifest: String,
    val archive: String? = null,
) {
    init {
        requireRelativeCacheHandle(manifest, "manifest")
        archive?.let { requireRelativeCacheHandle(it, "archive") }
    }
}

@Serializable
data class CacheExportResult(
    val manifest: String,
    val archive: String,
    val included: List<String>,
    val status: CacheExportStatus,
)

@Serializable
data class CacheCleanupRequest(
    val manifest: String,
) {
    init {
        requireRelativeCacheHandle(manifest, "manifest")
    }
}

@Serializable
data class CacheCleanupResult(
    val manifest: String,
    val deleted: List<String>,
    val missing: List<String>,
    val status: CacheCleanupStatus,
)

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
    val launch: CacheLaunchPlan,
    val javaSelection: JavaRuntimeSelection? = null,
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
                    CachePreparedArtifact(
                        kind = CachePreparedArtifactKind.MINECRAFT_CLIENT_JAR,
                        handle = "cache/minecraft/versions/${request.minecraftVersion}/client.jar",
                        status = CachePreparedArtifactStatus.CACHED,
                    ),
                    CachePreparedArtifact(
                        kind = CachePreparedArtifactKind.MINECRAFT_ASSET_INDEX,
                        handle = "cache/assets/indexes/${request.minecraftVersion}.json",
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
                launch = CacheLaunchPlan.fromArtifacts(artifacts),
            )
        }
    }
}

@Serializable
data class JavaRuntimeRequirement(
    val majorVersion: Int,
    val component: String? = null,
    val platform: String? = null,
    val architecture: String? = null,
    val sourcePolicy: JavaRuntimeSourcePolicy = JavaRuntimeSourcePolicy.AUTO,
    val reason: String,
) {
    init {
        require(majorVersion > 0) { "Java major version must be positive" }
        component?.let { requireFileSafeSegment(it, "Java runtime component") }
        platform?.let { requireFileSafeSegment(it, "Java runtime platform") }
        architecture?.let { requireFileSafeSegment(it, "Java runtime architecture") }
        require(reason.isNotBlank()) { "Java runtime requirement reason is required" }
    }
}

@Serializable
enum class JavaRuntimeSourcePolicy {
    AUTO,
    CONFIGURED_ONLY,
    MANAGED_ALLOWED,
}

@Serializable
data class MinecraftVersionListResult(
    val latest: MinecraftLatestVersions,
    val versions: List<MinecraftVersionDescriptor>,
)

@Serializable
data class MinecraftLatestVersions(
    val release: String,
    val snapshot: String,
)

@Serializable
data class MinecraftVersionDescriptor(
    val id: String,
    val type: String,
    val url: String? = null,
)

@Serializable
data class FabricGameVersionListResult(
    val versions: List<FabricGameVersionDescriptor>,
)

@Serializable
data class FabricGameVersionDescriptor(
    val version: String,
    val stable: Boolean,
)

@Serializable
data class FabricLoaderVersionListResult(
    val versions: List<FabricLoaderVersionDescriptor>,
)

@Serializable
data class FabricLoaderVersionDescriptor(
    val version: String,
    val stable: Boolean,
)

@Serializable
data class DriverModVersionListResult(
    val entries: List<DriverModVersionDescriptor>,
    val source: String? = null,
)

@Serializable
data class DriverModVersionDescriptor(
    val loader: Loader,
    val minecraftVersion: String,
    val loaderVersion: String? = null,
    val fabricApiVersion: String? = null,
    val javaMajorVersion: Int? = null,
    val mappingsFingerprint: String? = null,
    val path: String,
    val runtimeMods: List<String> = emptyList(),
)

@Serializable
data class FabricSupportTargetListResult(
    val targets: List<FabricSupportTargetDescriptor>,
    val source: String? = null,
)

@Serializable
data class FabricSupportTargetDescriptor(
    val minecraftVersion: String,
    val stable: Boolean,
    val loader: Loader = Loader.FABRIC,
    val supported: Boolean,
    val reason: FabricSupportReason? = null,
    val driverMods: List<DriverModVersionDescriptor> = emptyList(),
    val runtimeTargets: List<FabricSupportRuntimeTargetDescriptor> = emptyList(),
)

@Serializable
data class FabricSupportRuntimeTargetDescriptor(
    val loader: Loader = Loader.FABRIC,
    val loaderVersion: String? = null,
    val loaderStable: Boolean? = null,
    val javaMajorVersion: Int? = null,
    val mappingsFingerprint: String? = null,
    val supported: Boolean,
    val reason: FabricSupportReason? = null,
    val driverMod: DriverModVersionDescriptor? = null,
)

@Serializable
enum class FabricSupportReason {
    NO_DRIVER_MOD,
    NO_COMPATIBLE_DRIVER_MOD,
}

@Serializable
enum class JavaRuntimeProviderKind {
    CONFIGURED,
    MANAGED,
    MISE,
    SYSTEM,
}

@Serializable
data class JavaRuntimeDescriptor(
    val id: String,
    val provider: JavaRuntimeProviderKind,
    val javaHome: String? = null,
    val executable: String,
    val majorVersion: Int,
    val version: String,
    val vendor: String? = null,
    val architecture: String? = null,
    val managed: Boolean = false,
    val evidence: Map<String, String> = emptyMap(),
) {
    init {
        require(id.isNotBlank()) { "Java runtime id is required" }
        javaHome?.let { requireJavaRuntimeLocation(it, "Java runtime home") }
        requireJavaRuntimeLocation(executable, "Java runtime executable")
        require(majorVersion > 0) { "Java major version must be positive" }
        require(version.isNotBlank()) { "Java runtime version is required" }
    }
}

@Serializable
data class RejectedJavaRuntimeCandidate(
    val executable: String,
    val provider: JavaRuntimeProviderKind,
    val reason: String,
    val detectedMajorVersion: Int? = null,
) {
    init {
        requireJavaRuntimeLocation(executable, "rejected Java runtime executable")
        require(reason.isNotBlank()) { "rejected Java runtime reason is required" }
        detectedMajorVersion?.let { require(it > 0) { "detected Java major version must be positive" } }
    }
}

@Serializable
data class JavaRuntimeSelection(
    val requirement: JavaRuntimeRequirement,
    val status: JavaRuntimeSelectionStatus,
    val selected: JavaRuntimeDescriptor? = null,
    val rejected: List<RejectedJavaRuntimeCandidate> = emptyList(),
    val reason: String,
) {
    init {
        require(reason.isNotBlank()) { "Java runtime selection reason is required" }
        if (status == JavaRuntimeSelectionStatus.SELECTED) {
            requireNotNull(selected) { "selected Java runtime is required for selected status" }
        }
        if (status == JavaRuntimeSelectionStatus.UNSATISFIED) {
            require(selected == null) { "unsatisfied Java runtime selection must not include a selected runtime" }
        }
    }
}

@Serializable
enum class JavaRuntimeSelectionStatus {
    SELECTED,
    UNSATISFIED,
}

@Serializable
data class JavaRuntimeResolveRequest(
    val minecraftVersion: String? = null,
    val requirement: JavaRuntimeRequirement? = null,
) {
    init {
        minecraftVersion?.let { requireFileSafeSegment(it, "minecraft version") }
        require(minecraftVersion != null || requirement != null) {
            "Java runtime resolve request requires minecraftVersion or requirement"
        }
    }
}

@Serializable
data class JavaRuntimeListResult(
    val runtimes: List<JavaRuntimeDescriptor>,
    val rejected: List<RejectedJavaRuntimeCandidate> = emptyList(),
)

const val MINECRAFT_VERSION_INDEX_URL: String = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
const val MINECRAFT_JAVA_RUNTIME_INDEX_URL: String =
    "https://piston-meta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json"
const val FABRIC_META_BASE_URL: String = "https://meta.fabricmc.net/v2"

@Serializable
data class CachePreparedArtifact(
    val kind: CachePreparedArtifactKind,
    val handle: String,
    val source: String? = null,
    val sha1: String? = null,
    val status: CachePreparedArtifactStatus,
) {
    init {
        sha1?.let { digest ->
            require(Regex("[a-fA-F0-9]{40}").matches(digest)) { "cache artifact sha1 must be a SHA-1 hex string" }
        }
    }
}

@Serializable
data class CacheLaunchPlan(
    val classpath: List<String>,
    val nativePath: List<String> = emptyList(),
    val mods: List<String> = emptyList(),
    val javaExecutable: String? = null,
    val arguments: String? = null,
) {
    companion object {
        fun fromArtifacts(artifacts: List<CachePreparedArtifact>): CacheLaunchPlan =
            CacheLaunchPlan(
                classpath =
                    artifacts
                        .filter { artifact ->
                            artifact.kind == CachePreparedArtifactKind.FABRIC_LIBRARY ||
                                artifact.kind == CachePreparedArtifactKind.MINECRAFT_LIBRARY ||
                                artifact.kind == CachePreparedArtifactKind.MINECRAFT_CLIENT_JAR
                        }.sortedBy { artifact ->
                            when (artifact.kind) {
                                CachePreparedArtifactKind.MINECRAFT_LIBRARY -> 0
                                CachePreparedArtifactKind.FABRIC_LIBRARY -> 1
                                CachePreparedArtifactKind.MINECRAFT_CLIENT_JAR -> 2
                                else -> 3
                            }
                        }.map { it.handle },
                nativePath =
                    artifacts
                        .filter { artifact ->
                            artifact.kind == CachePreparedArtifactKind.MINECRAFT_NATIVE_DIRECTORY
                        }.map { it.handle },
                mods =
                    artifacts
                        .filter { artifact ->
                            artifact.kind == CachePreparedArtifactKind.FABRIC_MOD
                        }.map { it.handle },
                javaExecutable =
                    artifacts
                        .singleOrNull { artifact ->
                            artifact.kind == CachePreparedArtifactKind.JAVA_RUNTIME_EXECUTABLE
                        }?.handle,
                arguments =
                    artifacts
                        .singleOrNull { artifact ->
                            artifact.kind == CachePreparedArtifactKind.LAUNCH_ARGUMENTS
                        }?.handle,
            )
    }
}

@Serializable
enum class CachePreparedArtifactKind {
    MINECRAFT_VERSION_INDEX,
    MINECRAFT_VERSION_MANIFEST,
    MINECRAFT_CLIENT_JAR,
    MINECRAFT_ASSET_INDEX,
    MINECRAFT_ASSET_OBJECT,
    MINECRAFT_LOG_CONFIG,
    JAVA_RUNTIME_INDEX,
    JAVA_RUNTIME_MANIFEST,
    JAVA_RUNTIME_FILE,
    JAVA_RUNTIME_EXECUTABLE,
    MINECRAFT_LIBRARY,
    MINECRAFT_NATIVE_LIBRARY,
    MINECRAFT_NATIVE_DIRECTORY,
    LAUNCH_ARGUMENTS,
    FABRIC_LOADER_VERSIONS,
    FABRIC_LOADER_PROFILE,
    FABRIC_LIBRARY,
    FABRIC_MOD,
}

@Serializable
enum class CachePreparedArtifactStatus {
    RESOLVED,
    INDEXED,
    CACHED,
    EXTRACTED,
}

@Serializable
enum class CachePrepareStatus {
    PREPARED,
}

@Serializable
enum class CacheExportStatus {
    EXPORTED,
}

@Serializable
enum class CacheCleanupStatus {
    CLEANED,
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

private fun requireRelativeCacheHandle(
    value: String,
    label: String,
) {
    require(value.isNotBlank()) { "$label is required" }
    require(!value.startsWith('/')) { "$label must be relative" }
    require(!value.contains('\\')) { "$label must use forward slashes" }
    require(!value.split('/').any { it == ".." }) { "$label must stay under the workspace root" }
}

private fun requireJavaRuntimeLocation(
    value: String,
    label: String,
) {
    require(value.isNotBlank()) { "$label is required" }
    require(!value.contains('\u0000')) { "$label must not contain NUL" }
}
