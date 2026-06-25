package com.minekube.craftless.testkit

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalServerFixtureTest {
    @Test
    fun `fixture writes offline server properties and artifact directories`() {
        val root = Files.createTempDirectory("craftless-server-fixture")
        val fixture = LocalServerFixture(root = root, port = 25567)

        val layout = fixture.prepare()

        assertEquals(root.resolve("server.properties"), layout.serverProperties)
        assertEquals(root.resolve("logs"), layout.logsDir)
        assertEquals(root.resolve("artifacts"), layout.artifactsDir)
        assertTrue(Files.readString(layout.serverProperties).contains("online-mode=false"))
        assertTrue(Files.readString(layout.serverProperties).contains("server-port=25567"))
        assertTrue(Files.isDirectory(layout.logsDir))
        assertTrue(Files.isDirectory(layout.artifactsDir))
    }

    @Test
    fun `fixture records local server evidence as json lines`() {
        val root = Files.createTempDirectory("craftless-server-evidence")
        val layout = LocalServerFixture(root = root, port = 25567).prepare()

        assertEquals(root.resolve("artifacts").resolve("server-evidence.jsonl"), layout.evidenceLog)
        assertFalse(Files.exists(layout.evidenceLog))

        layout.recordEvidence(LocalServerEvidence.playerJoined("Alice"))
        layout.recordEvidence(LocalServerEvidence.chat("Alice", "hello from Fabric smoke"))
        layout.recordEvidence(
            LocalServerEvidence.movement(
                player = "Alice",
                from = LocalServerPosition(x = 0.0, y = 64.0, z = 0.0),
                to = LocalServerPosition(x = 0.0, y = 64.0, z = 1.25),
            )
        )
        layout.recordEvidence(LocalServerEvidence.playerDisconnected("Alice"))

        val evidence = layout.readEvidence()
        assertEquals(
            listOf(
                LocalServerEvidence(type = LocalServerEvidenceType.PLAYER_JOINED, player = "Alice"),
                LocalServerEvidence(
                    type = LocalServerEvidenceType.CHAT,
                    player = "Alice",
                    message = "hello from Fabric smoke",
                ),
                LocalServerEvidence(
                    type = LocalServerEvidenceType.MOVEMENT,
                    player = "Alice",
                    from = LocalServerPosition(x = 0.0, y = 64.0, z = 0.0),
                    to = LocalServerPosition(x = 0.0, y = 64.0, z = 1.25),
                ),
                LocalServerEvidence(type = LocalServerEvidenceType.PLAYER_DISCONNECTED, player = "Alice"),
            ),
            evidence,
        )
        assertEquals(4, Files.readAllLines(layout.evidenceLog).size)
    }

    @Test
    fun `fixture imports recognized server log evidence`() {
        val root = Files.createTempDirectory("craftless-server-log-evidence")
        val layout = LocalServerFixture(root = root, port = 25567).prepare()

        assertTrue(layout.recordEvidenceFromLogLine("[12:00:00] [Server thread/INFO]: Alice joined the game"))
        assertTrue(layout.recordEvidenceFromLogLine("[12:00:01] [Server thread/INFO]: <Alice> hello from server log"))
        assertTrue(
            layout.recordEvidenceFromLogLine(
                "[12:00:02] [Server thread/INFO]: [Craftless] Alice moved from 0.0 64.0 0.0 to 0.0 64.0 1.25"
            )
        )
        assertTrue(layout.recordEvidenceFromLogLine("[12:00:03] [Server thread/INFO]: Alice left the game"))
        assertFalse(layout.recordEvidenceFromLogLine("[12:00:04] [Server thread/INFO]: Preparing spawn area: 100%"))

        assertEquals(
            listOf(
                LocalServerEvidence(type = LocalServerEvidenceType.PLAYER_JOINED, player = "Alice"),
                LocalServerEvidence(
                    type = LocalServerEvidenceType.CHAT,
                    player = "Alice",
                    message = "hello from server log",
                ),
                LocalServerEvidence(
                    type = LocalServerEvidenceType.MOVEMENT,
                    player = "Alice",
                    from = LocalServerPosition(x = 0.0, y = 64.0, z = 0.0),
                    to = LocalServerPosition(x = 0.0, y = 64.0, z = 1.25),
                ),
                LocalServerEvidence(type = LocalServerEvidenceType.PLAYER_DISCONNECTED, player = "Alice"),
            ),
            layout.readEvidence(),
        )
    }

    @Test
    fun `fixture collects evidence from a server process output`() {
        val root = Files.createTempDirectory("craftless-server-process-evidence")
        val layout = LocalServerFixture(root = root, port = 25567).prepare()
        val java = Path.of(System.getProperty("java.home")).resolve("bin").resolve("java").toString()

        val result = layout.collectEvidenceFromProcess(
            listOf(
                java,
                "-cp",
                System.getProperty("java.class.path"),
                LocalServerLogEmitter::class.java.name,
            )
        )

        assertEquals(0, result.exitCode)
        assertEquals(3, result.evidenceCount)
        assertTrue(Files.readString(layout.serverLog).contains("Alice joined the game"))
        assertEquals(
            listOf(
                LocalServerEvidence(type = LocalServerEvidenceType.PLAYER_JOINED, player = "Alice"),
                LocalServerEvidence(type = LocalServerEvidenceType.CHAT, player = "Alice", message = "hello from process"),
                LocalServerEvidence(type = LocalServerEvidenceType.PLAYER_DISCONNECTED, player = "Alice"),
            ),
            layout.readEvidence(),
        )
    }

    @Test
    fun `fixture launches minecraft server jar with accepted eula and evidence collection`() {
        val root = Files.createTempDirectory("craftless-minecraft-server-evidence")
        val layout = LocalServerFixture(root = root, port = 25567).prepare()
        val fakeJava = root.resolve("fake-java")
        val fakeServerJar = root.resolve("server.jar")
        Files.writeString(fakeServerJar, "fake")
        Files.writeString(
            fakeJava,
            """
            #!/bin/sh
            printf '%s\n' "$@" > minecraft-server-args.txt
            echo '[12:00:00] [Server thread/INFO]: Alice joined the game'
            echo '[12:00:01] [Server thread/INFO]: <Alice> hello from minecraft server process'
            echo '[12:00:02] [Server thread/INFO]: Alice left the game'
            """.trimIndent() + "\n"
        )
        assertTrue(fakeJava.toFile().setExecutable(true))

        val result = layout.collectMinecraftServerEvidence(
            serverJar = fakeServerJar,
            javaExecutable = fakeJava,
            minHeap = "256M",
            maxHeap = "512M",
        )

        assertEquals(0, result.exitCode)
        assertEquals(3, result.evidenceCount)
        assertEquals("eula=true\n", Files.readString(layout.eulaFile))
        assertEquals(
            listOf("-Xms256M", "-Xmx512M", "-jar", fakeServerJar.toString(), "nogui"),
            Files.readAllLines(root.resolve("minecraft-server-args.txt")),
        )
        assertEquals(
            listOf(
                LocalServerEvidence(type = LocalServerEvidenceType.PLAYER_JOINED, player = "Alice"),
                LocalServerEvidence(
                    type = LocalServerEvidenceType.CHAT,
                    player = "Alice",
                    message = "hello from minecraft server process",
                ),
                LocalServerEvidence(type = LocalServerEvidenceType.PLAYER_DISCONNECTED, player = "Alice"),
            ),
            layout.readEvidence(),
        )
    }
}

private object LocalServerLogEmitter {
    @JvmStatic
    fun main(args: Array<String>) {
        println("[12:00:00] [Server thread/INFO]: Alice joined the game")
        println("[12:00:01] [Server thread/INFO]: <Alice> hello from process")
        println("[12:00:02] [Server thread/INFO]: Alice left the game")
    }
}
