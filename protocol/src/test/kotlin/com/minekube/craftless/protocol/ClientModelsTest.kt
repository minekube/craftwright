package com.minekube.craftless.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClientModelsTest {
    @Test
    fun `instance file layout uses craftless owned paths learned from prism structure`() {
        val instance =
            Instance(
                id = "alice-1.21.6-fabric",
                version = MinecraftVersion("1.21.6"),
                loader = Loader.FABRIC,
            )

        assertEquals("instances/alice-1.21.6-fabric", instance.files.root)
        assertEquals("instances/alice-1.21.6-fabric/minecraft", instance.files.gameRoot)
        assertEquals("instances/alice-1.21.6-fabric/minecraft/mods", instance.files.mods)
        assertEquals("instances/alice-1.21.6-fabric/minecraft/config", instance.files.config)
        assertEquals("instances/alice-1.21.6-fabric/minecraft/saves", instance.files.saves)
        assertEquals("instances/alice-1.21.6-fabric/minecraft/resourcepacks", instance.files.resourcePacks)
        assertEquals("instances/alice-1.21.6-fabric/minecraft/shaderpacks", instance.files.shaderPacks)
    }

    @Test
    fun `create client request rejects ids that cannot be used as route segments`() {
        listOf(
            "",
            "alice/bob",
            "alice:run",
            "alice bob",
            ".alice",
            "alice.",
        ).forEach { clientId ->
            assertFailsWith<IllegalArgumentException> {
                CreateClientRequest(
                    id = clientId,
                    version = "1.21.4",
                    loader = Loader.FABRIC,
                    profile = Profile.offline("Alice"),
                )
            }
        }
    }
}
