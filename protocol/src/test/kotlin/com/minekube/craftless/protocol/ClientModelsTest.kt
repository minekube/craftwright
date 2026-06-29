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
        assertEquals("instances/alice-1.21.6-fabric/runtime", instance.files.runtimeRoot)
        assertEquals("instances/alice-1.21.6-fabric/cache", instance.files.cache)
        assertEquals("instances/alice-1.21.6-fabric/minecraft/mods", instance.files.mods)
        assertEquals("instances/alice-1.21.6-fabric/minecraft/config", instance.files.config)
        assertEquals("instances/alice-1.21.6-fabric/minecraft/saves", instance.files.saves)
        assertEquals("instances/alice-1.21.6-fabric/minecraft/resourcepacks", instance.files.resourcePacks)
        assertEquals("instances/alice-1.21.6-fabric/minecraft/shaderpacks", instance.files.shaderPacks)
        assertEquals("instances/alice-1.21.6-fabric/minecraft/screenshots", instance.files.screenshots)
        assertEquals("instances/alice-1.21.6-fabric/runtime/logs", instance.files.logs)
        assertEquals("instances/alice-1.21.6-fabric/runtime/artifacts", instance.files.artifacts)
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

    @Test
    fun `create client request accepts and validates loader version lane`() {
        val request =
            CreateClientRequest(
                id = "alice",
                version = "1.21.6",
                loader = Loader.FABRIC,
                loaderVersion = "0.19.3",
                profile = Profile.offline("Alice"),
            )

        assertEquals("0.19.3", request.loaderVersion)

        assertFailsWith<IllegalArgumentException> {
            CreateClientRequest(
                id = "alice",
                version = "1.21.6",
                loader = Loader.FABRIC,
                loaderVersion = "0.19/3",
                profile = Profile.offline("Alice"),
            )
        }
    }

    @Test
    fun `create client request defaults to automation muted non visible presentation`() {
        val request =
            CreateClientRequest(
                id = "api-bot-01",
                version = "latest-release",
                loader = Loader.FABRIC,
            )

        assertEquals(null, request.profile)
        assertEquals(ClientPresentation(), request.presentation)
        assertEquals(Profile.offline("Apibot01"), request.resolvedProfile())
    }

    @Test
    fun `create client request accepts visible default audio presentation`() {
        val request =
            CreateClientRequest(
                id = "robin",
                version = "latest-release",
                loader = Loader.FABRIC,
                profile = Profile.offline("Robin"),
                presentation =
                    ClientPresentation(
                        window = ClientWindowMode.VISIBLE,
                        audio = ClientAudioMode.DEFAULT,
                    ),
            )

        assertEquals(Profile.offline("Robin"), request.resolvedProfile())
        assertEquals(ClientWindowMode.VISIBLE, request.presentation.window)
        assertEquals(ClientAudioMode.DEFAULT, request.presentation.audio)
    }
}
