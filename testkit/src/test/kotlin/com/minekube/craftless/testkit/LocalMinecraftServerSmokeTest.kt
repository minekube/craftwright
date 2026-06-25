package com.minekube.craftless.testkit

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalMinecraftServerSmokeTest {
    @Test
    fun `local server smoke is disabled by default`() {
        val config = LocalMinecraftServerSmokeConfig.fromEnvironment(emptyMap())

        assertFalse(config.enabled)
        assertEquals("1.21.6", config.minecraftVersion)
        assertEquals(25565, config.port)
    }

    @Test
    fun `local server smoke parses opt in environment`() {
        val config = LocalMinecraftServerSmokeConfig.fromEnvironment(
            mapOf(
                "CRAFTLESS_LOCAL_SERVER_SMOKE" to "1",
                "CRAFTLESS_LOCAL_SERVER_SMOKE_ROOT" to "/tmp/craftless-smoke",
                "CRAFTLESS_SMOKE_MINECRAFT_VERSION" to "1.21.6",
                "CRAFTLESS_SMOKE_SERVER_PORT" to "25567",
                "CRAFTLESS_SMOKE_READINESS_TIMEOUT_MS" to "90000",
                "CRAFTLESS_SMOKE_SHUTDOWN_TIMEOUT_MS" to "15000",
                "CRAFTLESS_SMOKE_MIN_HEAP" to "256M",
                "CRAFTLESS_SMOKE_MAX_HEAP" to "512M",
            )
        )

        assertTrue(config.enabled)
        assertEquals(Path.of("/tmp/craftless-smoke"), config.root)
        assertEquals("1.21.6", config.minecraftVersion)
        assertEquals(25567, config.port)
        assertEquals(90_000, config.readinessTimeoutMillis)
        assertEquals(15_000, config.shutdownTimeoutMillis)
        assertEquals("256M", config.minHeap)
        assertEquals("512M", config.maxHeap)
    }

    @Test
    fun `local server smoke skips without opt in`() {
        val result = LocalMinecraftServerSmoke.run(
            config = LocalMinecraftServerSmokeConfig.fromEnvironment(emptyMap()),
        )

        assertEquals(LocalMinecraftServerSmokeStatus.SKIPPED, result.status)
        assertEquals("set CRAFTLESS_LOCAL_SERVER_SMOKE=1 to run the local server smoke", result.message)
    }
}
