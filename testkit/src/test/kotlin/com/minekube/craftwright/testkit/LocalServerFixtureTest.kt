package com.minekube.craftwright.testkit

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalServerFixtureTest {
    @Test
    fun `fixture writes offline server properties and artifact directories`() {
        val root = Files.createTempDirectory("craftwright-server-fixture")
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
}
