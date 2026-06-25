package com.minekube.craftless.testkit

import java.nio.file.Files
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
}
