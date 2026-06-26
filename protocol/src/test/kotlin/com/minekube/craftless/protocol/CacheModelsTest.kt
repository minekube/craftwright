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
            )

        assertEquals("1.21.6", request.minecraftVersion)
        assertEquals(Loader.FABRIC, request.loader)
        assertFailsWith<IllegalArgumentException> {
            request.copy(minecraftVersion = "")
        }
    }

    @Test
    fun `cache prepare result uses craftless owned relative handles`() {
        val result = CachePrepareResult.forRequest(CachePrepareRequest("1.21.6", Loader.FABRIC))

        assertEquals("cache", result.cacheRoot)
        assertEquals("cache/minecraft/versions/1.21.6", result.minecraftVersionRoot)
        assertEquals("cache/loaders/fabric/1.21.6", result.loaderRoot)
        assertEquals("cache/runtimes", result.runtimeRoot)
        assertEquals("cache/prepared/1.21.6-fabric.json", result.manifest)
        assertEquals(CachePrepareStatus.PREPARED, result.status)
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
            ),
            result.artifacts,
        )
    }
}
