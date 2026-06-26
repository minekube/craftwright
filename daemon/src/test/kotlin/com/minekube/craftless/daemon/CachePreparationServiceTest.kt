package com.minekube.craftless.daemon

import com.minekube.craftless.protocol.CachePrepareRequest
import com.minekube.craftless.protocol.CachePreparedArtifactKind
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
                            ),
                        ),
                )

            val result = service.prepare(CachePrepareRequest("1.21.6", Loader.FABRIC))

            assertEquals(
                listOf(
                    CachePreparedArtifactKind.MINECRAFT_VERSION_INDEX,
                    CachePreparedArtifactKind.MINECRAFT_VERSION_MANIFEST,
                ),
                result.artifacts.map { it.kind },
            )
            assertEquals(versionUrl, result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_VERSION_MANIFEST }.source)
            assertTrue(Files.readString(workspace.resolve("cache/minecraft/version_manifest_v2.json")).contains("1.21.6"))
            assertTrue(Files.readString(workspace.resolve("cache/minecraft/versions/1.21.6/version.json")).contains("client.jar"))
            assertTrue(Files.readString(workspace.resolve(result.manifest)).contains("MINECRAFT_VERSION_MANIFEST"))
        }
}

private class StaticCacheMetadataFetcher(
    private val responses: Map<String, String>,
) : CacheMetadataFetcher {
    override suspend fun fetchText(url: String): String = requireNotNull(responses[url]) { "missing test response for $url" }
}
