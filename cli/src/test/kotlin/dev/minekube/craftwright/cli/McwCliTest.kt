package dev.minekube.craftwright.cli

import kotlin.test.Test
import kotlin.test.assertTrue

class McwCliTest {
    @Test
    fun `cli registers first jvm command tree`() {
        val commands = McwCli.registeredCommandPaths()

        assertTrue(commands.contains("versions"))
        assertTrue(commands.contains("profiles"))
        assertTrue(commands.contains("clients create"))
        assertTrue(commands.contains("clients list"))
        assertTrue(commands.contains("clients connect"))
        assertTrue(commands.contains("clients api"))
        assertTrue(commands.contains("server start"))
        assertTrue(commands.contains("test run"))
    }
}
