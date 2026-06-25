package com.minekube.craftless.testkit

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
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
                "CRAFTLESS_SMOKE_JAVA_EXECUTABLE" to "/tmp/craftless-java",
                "CRAFTLESS_SMOKE_ACTION_COMMAND_JSON" to """["/tmp/craftless-action","--target","server"]""",
                "CRAFTLESS_SMOKE_ACTION_TIMEOUT_MS" to "45000",
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
        assertEquals(Path.of("/tmp/craftless-java"), config.javaExecutable)
        assertEquals(listOf("/tmp/craftless-action", "--target", "server"), config.actionCommand)
        assertEquals(45_000, config.actionTimeoutMillis)
        assertEquals(90_000, config.readinessTimeoutMillis)
        assertEquals(15_000, config.shutdownTimeoutMillis)
        assertEquals("256M", config.minHeap)
        assertEquals("512M", config.maxHeap)
    }

    @Test
    fun `local server smoke is enabled by fabric client smoke gate`() {
        val config = LocalMinecraftServerSmokeConfig.fromEnvironment(
            mapOf("CRAFTLESS_FABRIC_CLIENT_SMOKE" to "1")
        )

        assertTrue(config.enabled)
    }

    @Test
    fun `local server smoke skips without opt in`() {
        val result = LocalMinecraftServerSmoke.run(
            config = LocalMinecraftServerSmokeConfig.fromEnvironment(emptyMap()),
        )

        assertEquals(LocalMinecraftServerSmokeStatus.SKIPPED, result.status)
        assertEquals("set CRAFTLESS_LOCAL_SERVER_SMOKE=1 to run the local server smoke", result.message)
    }

    @Test
    fun `local server smoke keeps server running while caller action executes`() {
        val root = createTempDirectory("craftless-local-server-smoke-action")
        val fakeJava = root.resolve("fake-java")
        val fakeServerJar = root.resolve("server.jar")
        Files.writeString(fakeServerJar, "fake")
        Files.writeString(
            fakeJava,
            """
            #!/bin/sh
            echo '[12:00:00] [Server thread/INFO]: Done (1.000s)! For help, type "help"'
            touch server-is-ready
            read command
            printf '%s\n' "${'$'}command" > minecraft-server-stdin.txt
            echo '[12:00:01] [Server thread/INFO]: Alice joined the game'
            echo '[12:00:02] [Server thread/INFO]: <Alice> hello while smoke action ran'
            echo '[12:00:03] [Server thread/INFO]: Alice left the game'
            """.trimIndent() + "\n"
        )
        assertTrue(fakeJava.toFile().setExecutable(true))
        val config = LocalMinecraftServerSmokeConfig(
            enabled = true,
            root = root,
            javaExecutable = fakeJava,
            readinessTimeoutMillis = 5_000,
            shutdownTimeoutMillis = 5_000,
        )
        var actionObservedRunningServer = false

        val result = LocalMinecraftServerSmoke.runWithServer(
            config = config,
            provisionServerJar = { _, _ -> fakeServerJar },
        ) { layout, handle ->
            actionObservedRunningServer = handle.isRunning()
            assertTrue(waitUntilExists(root.resolve("server-is-ready")))
            assertFalse(Files.exists(root.resolve("minecraft-server-stdin.txt")))
            assertTrue(Files.exists(layout.serverProperties))
        }

        assertTrue(actionObservedRunningServer)
        assertEquals(LocalMinecraftServerSmokeStatus.RAN, result.status)
        assertEquals(3, result.evidenceCount)
        assertEquals("stop\n", Files.readString(root.resolve("minecraft-server-stdin.txt")))
    }

    @Test
    fun `local server smoke runs configured command before stopping server`() {
        val root = createTempDirectory("craftless-local-server-smoke-command")
        val fakeJava = root.resolve("fake-java")
        val fakeServerJar = root.resolve("server.jar")
        val actionCommand = root.resolve("smoke-action")
        Files.writeString(fakeServerJar, "fake")
        Files.writeString(
            fakeJava,
            """
            #!/bin/sh
            echo '[12:00:00] [Server thread/INFO]: Done (1.000s)! For help, type "help"'
            touch server-is-ready
            read command
            printf '%s\n' "${'$'}command" > minecraft-server-stdin.txt
            echo '[12:00:01] [Server thread/INFO]: Alice joined the game'
            echo '[12:00:02] [Server thread/INFO]: <Alice> hello from configured smoke command'
            echo '[12:00:03] [Server thread/INFO]: Alice left the game'
            """.trimIndent() + "\n"
        )
        Files.writeString(
            actionCommand,
            """
            #!/bin/sh
            test -f server-is-ready
            test ! -f minecraft-server-stdin.txt
            printf '%s\n' "${'$'}PWD" > action-working-directory.txt
            printf '%s\n' "$@" > action-arguments.txt
            echo 'configured smoke action ran'
            """.trimIndent() + "\n"
        )
        assertTrue(fakeJava.toFile().setExecutable(true))
        assertTrue(actionCommand.toFile().setExecutable(true))
        val config = LocalMinecraftServerSmokeConfig(
            enabled = true,
            root = root,
            javaExecutable = fakeJava,
            actionCommand = listOf(actionCommand.toString(), "--client", "fabric"),
            actionTimeoutMillis = 5_000,
            readinessTimeoutMillis = 5_000,
            shutdownTimeoutMillis = 5_000,
        )

        val result = LocalMinecraftServerSmoke.runWithServer(
            config = config,
            provisionServerJar = { _, _ -> fakeServerJar },
        )

        assertEquals(LocalMinecraftServerSmokeStatus.RAN, result.status)
        assertEquals(0, result.actionExitCode)
        assertEquals(
            root.toRealPath(),
            Path.of(Files.readString(root.resolve("action-working-directory.txt")).trim()).toRealPath(),
        )
        assertEquals(listOf("--client", "fabric"), Files.readAllLines(root.resolve("action-arguments.txt")))
        assertEquals("stop\n", Files.readString(root.resolve("minecraft-server-stdin.txt")))
        assertTrue(Files.readString(requireNotNull(result.actionLog)).contains("configured smoke action ran"))
    }
}

private fun waitUntilExists(path: Path, timeoutMillis: Long = 1_000): Boolean {
    val deadline = System.nanoTime() + timeoutMillis * 1_000_000
    while (System.nanoTime() < deadline) {
        if (Files.exists(path)) {
            return true
        }
        Thread.sleep(10)
    }
    return Files.exists(path)
}
