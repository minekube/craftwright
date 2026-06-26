package com.minekube.craftless.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CacheModelsTest {
    @Test
    fun `cache prepare request validates setup inputs`() {
        val request =
            CachePrepareRequest(
                minecraftVersion = "1.21.6",
                loader = Loader.FABRIC,
                loaderVersion = "0.17.2",
            )

        assertEquals("1.21.6", request.minecraftVersion)
        assertEquals(Loader.FABRIC, request.loader)
        assertEquals("0.17.2", request.loaderVersion)
        assertFailsWith<IllegalArgumentException> {
            request.copy(minecraftVersion = "")
        }
        assertFailsWith<IllegalArgumentException> {
            request.copy(loaderVersion = "../0.17.2")
        }
    }

    @Test
    fun `cache export and cleanup requests validate handles`() {
        val export = CacheExportRequest(manifest = "cache/prepared/1.21.6-fabric.json")
        val cleanup = CacheCleanupRequest(manifest = "cache/prepared/1.21.6-fabric.json")

        assertEquals("cache/prepared/1.21.6-fabric.json", export.manifest)
        assertEquals(null, export.archive)
        assertEquals("cache/prepared/1.21.6-fabric.json", cleanup.manifest)
        assertFailsWith<IllegalArgumentException> {
            CacheExportRequest(manifest = "/tmp/prepared.json")
        }
        assertFailsWith<IllegalArgumentException> {
            CacheExportRequest(manifest = "../prepared.json")
        }
        assertFailsWith<IllegalArgumentException> {
            CacheExportRequest(
                manifest = "cache/prepared/1.21.6-fabric.json",
                archive = "exports/../prepared.zip",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CacheCleanupRequest(manifest = "")
        }
    }

    @Test
    fun `cache prepare result uses craftless owned relative handles`() {
        val result = CachePrepareResult.forRequest(CachePrepareRequest("1.21.6", Loader.FABRIC))

        assertEquals("cache", result.cacheRoot)
        assertEquals("cache/minecraft/versions/1.21.6", result.minecraftVersionRoot)
        assertEquals("cache/loaders/fabric/1.21.6", result.loaderRoot)
        assertEquals(null, result.loaderVersion)
        assertEquals("cache/runtimes", result.runtimeRoot)
        assertEquals("cache/prepared/1.21.6-fabric.json", result.manifest)
        assertEquals(CachePrepareStatus.PREPARED, result.status)
        assertEquals(
            listOf("cache/minecraft/versions/1.21.6/client.jar"),
            result.launch.classpath,
        )
        assertEquals(null, result.launch.javaExecutable)
        assertEquals(null, result.launch.arguments)
        assertEquals(
            listOf(
                CachePreparedArtifact(
                    kind = CachePreparedArtifactKind.MINECRAFT_VERSION_INDEX,
                    handle = "cache/minecraft/version_manifest_v2.json",
                    source = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json",
                    status = CachePreparedArtifactStatus.RESOLVED,
                ),
                CachePreparedArtifact(
                    kind = CachePreparedArtifactKind.MINECRAFT_VERSION_MANIFEST,
                    handle = "cache/minecraft/versions/1.21.6/version.json",
                    status = CachePreparedArtifactStatus.RESOLVED,
                ),
                CachePreparedArtifact(
                    kind = CachePreparedArtifactKind.MINECRAFT_CLIENT_JAR,
                    handle = "cache/minecraft/versions/1.21.6/client.jar",
                    status = CachePreparedArtifactStatus.CACHED,
                ),
                CachePreparedArtifact(
                    kind = CachePreparedArtifactKind.MINECRAFT_ASSET_INDEX,
                    handle = "cache/assets/indexes/1.21.6.json",
                    status = CachePreparedArtifactStatus.RESOLVED,
                ),
                CachePreparedArtifact(
                    kind = CachePreparedArtifactKind.FABRIC_LOADER_VERSIONS,
                    handle = "cache/loaders/fabric/1.21.6/versions.json",
                    source = "https://meta.fabricmc.net/v2/versions/loader/1.21.6",
                    status = CachePreparedArtifactStatus.RESOLVED,
                ),
                CachePreparedArtifact(
                    kind = CachePreparedArtifactKind.FABRIC_LOADER_PROFILE,
                    handle = "cache/loaders/fabric/1.21.6/profile.json",
                    status = CachePreparedArtifactStatus.RESOLVED,
                ),
            ),
            result.artifacts,
        )
    }

    @Test
    fun `cache prepare result records pinned fabric loader metadata handles`() {
        val result =
            CachePrepareResult.forRequest(
                CachePrepareRequest(
                    minecraftVersion = "1.21.6",
                    loader = Loader.FABRIC,
                    loaderVersion = "0.17.2",
                ),
            )

        assertEquals("0.17.2", result.loaderVersion)
        assertEquals("cache/loaders/fabric/1.21.6/0.17.2", result.loaderRoot)
        assertEquals("cache/prepared/1.21.6-fabric-0.17.2.json", result.manifest)
        assertEquals(
            listOf(
                CachePreparedArtifactKind.MINECRAFT_VERSION_INDEX,
                CachePreparedArtifactKind.MINECRAFT_VERSION_MANIFEST,
                CachePreparedArtifactKind.MINECRAFT_CLIENT_JAR,
                CachePreparedArtifactKind.MINECRAFT_ASSET_INDEX,
                CachePreparedArtifactKind.FABRIC_LOADER_VERSIONS,
                CachePreparedArtifactKind.FABRIC_LOADER_PROFILE,
            ),
            result.artifacts.map { it.kind },
        )
        assertEquals(
            "cache/loaders/fabric/1.21.6/0.17.2/profile.json",
            result.artifacts.single { it.kind == CachePreparedArtifactKind.FABRIC_LOADER_PROFILE }.handle,
        )
    }
}
