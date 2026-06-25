package com.minekube.craftwright.protocol

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertTrue

class NamespacePolicyTest {
    @Test
    fun `repository uses minekube com namespace`() {
        val root = Path.of("").toAbsolutePath().normalize()
        val legacyPackage = "dev" + ".minekube"
        val legacyDomain = "minekube" + ".dev"
        val violations = Files.walk(root).use { paths ->
            paths
                .filter { path -> path.isScannable() }
                .filter { path ->
                    val contents = Files.readString(path)
                    contents.contains(legacyPackage) || contents.contains(legacyDomain)
                }
                .map { path -> root.relativize(path).pathString }
                .sorted()
                .toList()
        }

        assertTrue(
            violations.isEmpty(),
            "Legacy minekube .dev namespace remains:\n${violations.joinToString("\n")}",
        )
    }

    private fun Path.isScannable(): Boolean {
        val path = pathString
        if (isDirectory()) return false
        if ("/build/" in path || "/.gradle/" in path || "/.git/" in path) return false
        if (name.endsWith(".class") || name.endsWith(".jar") || name.endsWith(".png")) return false
        return true
    }
}
