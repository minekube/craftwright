package com.minekube.craftless.daemon

import com.minekube.craftless.protocol.CachePrepareRequest
import com.minekube.craftless.protocol.CachePrepareResult
import com.minekube.craftless.protocol.CachePreparedArtifactKind
import com.minekube.craftless.protocol.FABRIC_META_BASE_URL
import com.minekube.craftless.protocol.Loader
import com.minekube.craftless.protocol.MINECRAFT_VERSION_INDEX_URL
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

class CachePreparationService(
    workspaceRoot: Path,
    private val metadataFetcher: CacheMetadataFetcher = KtorCacheMetadataFetcher(),
) {
    private val root: Path = workspaceRoot.toAbsolutePath().normalize()
    private val json = Json { encodeDefaults = true }

    suspend fun prepare(request: CachePrepareRequest): CachePrepareResult {
        val versionIndex = metadataFetcher.fetchText(MINECRAFT_VERSION_INDEX_URL)
        val versionManifestUrl = versionIndex.versionManifestUrl(request.minecraftVersion)
        val versionManifest = metadataFetcher.fetchText(versionManifestUrl)
        val fabricMetadata = resolveFabricMetadata(request)
        val baseResult = CachePrepareResult.forRequest(request, fabricMetadata?.loaderVersion)
        val result =
            baseResult.copy(
                artifacts =
                    baseResult.artifacts.map { artifact ->
                        when (artifact.kind) {
                            CachePreparedArtifactKind.MINECRAFT_VERSION_MANIFEST -> artifact.copy(source = versionManifestUrl)
                            CachePreparedArtifactKind.FABRIC_LOADER_PROFILE -> artifact.copy(source = fabricMetadata?.profileUrl)
                            else -> artifact
                        }
                    },
            )
        Files.createDirectories(root)
        listOf(
            result.cacheRoot,
            result.minecraftVersionRoot,
            result.loaderRoot,
            result.runtimeRoot,
        ).forEach { handle ->
            Files.createDirectories(resolveHandle(handle))
        }
        Files.writeString(
            resolveHandle(result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_VERSION_INDEX }.handle),
            versionIndex,
        )
        Files.writeString(
            resolveHandle(result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_VERSION_MANIFEST }.handle),
            versionManifest,
        )
        fabricMetadata?.let { metadata ->
            Files.writeString(
                resolveHandle(result.artifacts.single { it.kind == CachePreparedArtifactKind.FABRIC_LOADER_VERSIONS }.handle),
                metadata.loaderVersions,
            )
            Files.writeString(
                resolveHandle(result.artifacts.single { it.kind == CachePreparedArtifactKind.FABRIC_LOADER_PROFILE }.handle),
                metadata.profile,
            )
        }
        val manifest = resolveHandle(result.manifest)
        Files.createDirectories(manifest.parent)
        Files.writeString(manifest, json.encodeToString(result) + "\n")
        return result
    }

    private suspend fun resolveFabricMetadata(request: CachePrepareRequest): FabricCacheMetadata? {
        if (request.loader != Loader.FABRIC) return null
        val loaderVersionsUrl = fabricLoaderVersionsUrl(request.minecraftVersion)
        val loaderVersions = metadataFetcher.fetchText(loaderVersionsUrl)
        val loaderVersion = loaderVersions.compatibleFabricLoaderVersion(request.loaderVersion)
        val profileUrl = fabricLoaderProfileUrl(request.minecraftVersion, loaderVersion)
        return FabricCacheMetadata(
            loaderVersion = loaderVersion,
            loaderVersions = loaderVersions,
            profileUrl = profileUrl,
            profile = metadataFetcher.fetchText(profileUrl),
        )
    }

    private fun resolveHandle(handle: String): Path {
        require(!Path.of(handle).isAbsolute) { "cache handle must be relative" }
        val resolved = root.resolve(handle).normalize()
        require(resolved.startsWith(root)) { "cache handle must stay under the workspace root" }
        return resolved
    }
}

private data class FabricCacheMetadata(
    val loaderVersion: String,
    val loaderVersions: String,
    val profileUrl: String,
    val profile: String,
)

interface CacheMetadataFetcher {
    suspend fun fetchText(url: String): String
}

class KtorCacheMetadataFetcher : CacheMetadataFetcher {
    override suspend fun fetchText(url: String): String =
        HttpClient(CIO).use { http ->
            val response = http.get(url)
            require(response.status.isSuccess()) { "metadata fetch failed for $url: ${response.status.value}" }
            response.bodyAsText()
        }
}

private fun String.versionManifestUrl(minecraftVersion: String): String {
    val versions =
        Json
            .parseToJsonElement(this)
            .jsonObject["versions"]
            ?.jsonArray
            .orEmpty()
    val version =
        versions.firstOrNull { version ->
            version.jsonObject["id"]?.jsonPrimitive?.content == minecraftVersion
        } ?: error("minecraft version $minecraftVersion was not found in version index")
    return version.jsonObject["url"]?.jsonPrimitive?.content ?: error("minecraft version $minecraftVersion is missing metadata url")
}

private fun String.compatibleFabricLoaderVersion(requestedLoaderVersion: String?): String {
    val versions =
        Json
            .parseToJsonElement(this)
            .jsonArray
            .mapNotNull { entry ->
                val loader = entry.jsonObject["loader"]?.jsonObject ?: return@mapNotNull null
                val version = loader["version"]?.jsonPrimitive?.content ?: return@mapNotNull null
                FabricLoaderVersion(
                    version = version,
                    stable = loader["stable"]?.jsonPrimitive?.booleanOrNull == true,
                )
            }
    requestedLoaderVersion?.let { requested ->
        return versions.firstOrNull { it.version == requested }?.version
            ?: error("fabric loader version $requested is not compatible with this minecraft version")
    }
    return versions.firstOrNull { it.stable }?.version
        ?: versions.firstOrNull()?.version
        ?: error("no compatible fabric loader version was found")
}

private data class FabricLoaderVersion(
    val version: String,
    val stable: Boolean,
)

private fun fabricLoaderVersionsUrl(minecraftVersion: String): String = "$FABRIC_META_BASE_URL/versions/loader/$minecraftVersion"

private fun fabricLoaderProfileUrl(
    minecraftVersion: String,
    loaderVersion: String,
): String = "$FABRIC_META_BASE_URL/versions/loader/$minecraftVersion/$loaderVersion/profile/json"
