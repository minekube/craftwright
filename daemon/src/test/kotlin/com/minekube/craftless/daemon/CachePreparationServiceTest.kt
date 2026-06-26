package com.minekube.craftless.daemon

import com.minekube.craftless.protocol.CachePrepareRequest
import com.minekube.craftless.protocol.CachePreparedArtifactKind
import com.minekube.craftless.protocol.FABRIC_META_BASE_URL
import com.minekube.craftless.protocol.Loader
import com.minekube.craftless.protocol.MINECRAFT_VERSION_INDEX_URL
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CachePreparationServiceTest {
    @Test
    fun `cache preparation resolves and stores minecraft version metadata`() =
        runBlocking {
            val workspace = Files.createTempDirectory("craftless-cache-resolution")
            val versionUrl = "https://metadata.test/1.21.6.json"
            val loaderVersionsUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6"
            val loaderProfileUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6/0.17.2/profile/json"
            val service =
                CachePreparationService(
                    workspaceRoot = workspace,
                    metadataFetcher =
                        StaticCacheMetadataFetcher(
                            mapOf(
                                MINECRAFT_VERSION_INDEX_URL to
                                    """
                                    {
                                      "versions": [
                                        { "id": "1.21.6", "url": "$versionUrl" }
                                      ]
                                    }
                                    """.trimIndent(),
                                versionUrl to """{"id":"1.21.6","downloads":{"client":{"url":"https://metadata.test/client.jar"}}}""",
                                loaderVersionsUrl to
                                    """
                                    [
                                      { "loader": { "version": "0.17.1", "stable": false } },
                                      { "loader": { "version": "0.17.2", "stable": true } }
                                    ]
                                    """.trimIndent(),
                                loaderProfileUrl to """{"id":"fabric-loader-0.17.2-1.21.6"}""",
                            ),
                        ),
                )

            val result = service.prepare(CachePrepareRequest("1.21.6", Loader.FABRIC))

            assertEquals(
                listOf(
                    CachePreparedArtifactKind.MINECRAFT_VERSION_INDEX,
                    CachePreparedArtifactKind.MINECRAFT_VERSION_MANIFEST,
                    CachePreparedArtifactKind.FABRIC_LOADER_VERSIONS,
                    CachePreparedArtifactKind.FABRIC_LOADER_PROFILE,
                ),
                result.artifacts.map { it.kind },
            )
            assertEquals("0.17.2", result.loaderVersion)
            assertEquals(versionUrl, result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_VERSION_MANIFEST }.source)
            assertEquals(loaderProfileUrl, result.artifacts.single { it.kind == CachePreparedArtifactKind.FABRIC_LOADER_PROFILE }.source)
            assertTrue(Files.readString(workspace.resolve("cache/minecraft/version_manifest_v2.json")).contains("1.21.6"))
            assertTrue(Files.readString(workspace.resolve("cache/minecraft/versions/1.21.6/version.json")).contains("client.jar"))
            assertTrue(Files.readString(workspace.resolve("cache/loaders/fabric/1.21.6/versions.json")).contains("0.17.2"))
            assertTrue(Files.readString(workspace.resolve("cache/loaders/fabric/1.21.6/0.17.2/profile.json")).contains("fabric-loader"))
            assertTrue(Files.readString(workspace.resolve(result.manifest)).contains("MINECRAFT_VERSION_MANIFEST"))
        }

    @Test
    fun `cache preparation uses pinned compatible fabric loader version`() =
        runBlocking {
            val workspace = Files.createTempDirectory("craftless-cache-loader-pin")
            val versionUrl = "https://metadata.test/1.21.6.json"
            val loaderVersionsUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6"
            val pinnedProfileUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6/0.16.14/profile/json"
            val service =
                CachePreparationService(
                    workspaceRoot = workspace,
                    metadataFetcher =
                        StaticCacheMetadataFetcher(
                            mapOf(
                                MINECRAFT_VERSION_INDEX_URL to
                                    """
                                    { "versions": [{ "id": "1.21.6", "url": "$versionUrl" }] }
                                    """.trimIndent(),
                                versionUrl to """{"id":"1.21.6"}""",
                                loaderVersionsUrl to
                                    """
                                    [
                                      { "loader": { "version": "0.17.2", "stable": true } },
                                      { "loader": { "version": "0.16.14", "stable": true } }
                                    ]
                                    """.trimIndent(),
                                pinnedProfileUrl to """{"id":"fabric-loader-0.16.14-1.21.6"}""",
                            ),
                        ),
                )

            val result =
                service.prepare(
                    CachePrepareRequest(
                        minecraftVersion = "1.21.6",
                        loader = Loader.FABRIC,
                        loaderVersion = "0.16.14",
                    ),
                )

            assertEquals("0.16.14", result.loaderVersion)
            assertEquals("cache/loaders/fabric/1.21.6/0.16.14", result.loaderRoot)
            assertTrue(Files.readString(workspace.resolve(result.manifest)).contains("0.16.14"))
            assertTrue(Files.readString(workspace.resolve("cache/loaders/fabric/1.21.6/0.16.14/profile.json")).contains("0.16.14"))
        }
}

private class StaticCacheMetadataFetcher(
    private val responses: Map<String, String>,
) : CacheMetadataFetcher {
    override suspend fun fetchText(url: String): String = requireNotNull(responses[url]) { "missing test response for $url" }
}
