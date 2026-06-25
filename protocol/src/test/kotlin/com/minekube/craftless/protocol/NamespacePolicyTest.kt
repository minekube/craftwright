package com.minekube.craftless.protocol

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertTrue

class NamespacePolicyTest {
    @Test
    fun `repository policy scanner sees sibling modules`() {
        val violations = repositoryContentViolations(
            include = { path -> path.name == "HmcBridgeDriverBackend.kt" },
        ) { contents ->
            contents.contains("class HmcBridgeDriverBackend")
        }

        assertTrue(
            violations.contains("driver-runtime/src/main/kotlin/com/minekube/craftless/driver/runtime/HmcBridgeDriverBackend.kt"),
            "Repository policy scanner must inspect sibling modules from Gradle module test tasks",
        )
    }

    @Test
    fun `repository uses minekube com namespace`() {
        val previousPackage = "dev" + ".minekube"
        val previousDomain = "minekube" + ".dev"
        val violations = repositoryContentViolations { contents ->
            contents.contains(previousPackage) || contents.contains(previousDomain)
        }

        assertTrue(
            violations.isEmpty(),
            "Previous minekube .dev namespace remains:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun `repository uses craftless product naming`() {
        val previousBrand = "Craft" + "wright"
        val previousBrandLower = "craft" + "wright"
        val violations = repositoryContentViolations { contents ->
            contents.contains(previousBrand) || contents.contains(previousBrandLower)
        }

        assertTrue(
            violations.isEmpty(),
            "Previous " + previousBrand + " naming remains:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun `kotlin sources avoid forbidden http implementations`() {
        val forbiddenImports = listOf(
            "java.net" + ".http",
            "com.sun.net" + ".httpserver",
            "ok" + "http3",
            "Ok" + "Http",
        )
        val forbiddenHttpEnums = listOf(
            "enum class Http" + "Method",
            "enum class HTTP" + "Method",
            "sealed class Http" + "Method",
            "object Http" + "Method",
        )
        val violations = repositoryContentViolations(
            include = { path -> path.name.endsWith(".kt") || path.name.endsWith(".kts") },
        ) { contents ->
            forbiddenImports.any(contents::contains) || forbiddenHttpEnums.any(contents::contains)
        }

        assertTrue(
            violations.isEmpty(),
            "Forbidden HTTP implementation or custom method enum remains:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun `javascript helper sources stay scoped to playwright`() {
        val root = repositoryRoot()
        val violations = Files.walk(root).use { paths ->
            paths
                .filter { path -> path.isJavaScriptHelperFile() }
                .filter { path -> !root.relativize(path).pathString.startsWith("playwright/") }
                .map { path -> root.relativize(path).pathString }
                .sorted()
                .toList()
        }

        assertTrue(
            violations.isEmpty(),
            "JavaScript helper files must stay in playwright/ and not recreate a TypeScript SDK:\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun `repository does not include non bun package manager artifacts`() {
        val forbiddenNames = setOf(
            "package-lock.json",
            "npm-shrinkwrap.json",
            "yarn.lock",
            "pnpm-lock.yaml",
        )
        val root = repositoryRoot()
        val violations = Files.walk(root).use { paths ->
            paths
                .filter { path -> path.isScannable() && path.name in forbiddenNames }
                .map { path -> root.relativize(path).pathString }
                .sorted()
                .toList()
        }

        assertTrue(
            violations.isEmpty(),
            "Non-Bun package manager artifacts are not allowed:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun `driver runtime public errors do not expose bridge internals`() {
        val forbiddenMessage = "bridge" + " returned"
        val root = repositoryRoot()
        val violations = repositoryContentViolations(
            include = { path ->
                val relative = root.relativize(path).pathString
                relative.startsWith("driver-runtime/src/main/") && path.name.endsWith(".kt")
            },
        ) { contents ->
            contents.contains(forbiddenMessage)
        }

        assertTrue(
            violations.isEmpty(),
            "Driver runtime public errors must not expose bridge internals:\n${violations.joinToString("\n")}",
        )
    }

    private fun repositoryContentViolations(
        include: (Path) -> Boolean = { true },
        predicate: (String) -> Boolean,
    ): List<String> {
        val root = repositoryRoot()
        return Files.walk(root).use { paths ->
            paths
                .filter { path -> path.isScannable() && include(path) }
                .filter { path -> predicate(Files.readString(path)) }
                .map { path -> root.relativize(path).pathString }
                .sorted()
                .toList()
        }
    }

    private fun repositoryRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        while (!Files.exists(current.resolve("settings.gradle.kts"))) {
            current = requireNotNull(current.parent) { "repository root not found" }
        }
        return current
    }

    private fun Path.isScannable(): Boolean {
        val path = pathString
        if (isDirectory()) return false
        if ("/build/" in path || "/.gradle/" in path || "/.git/" in path) return false
        if (name.endsWith(".class") || name.endsWith(".jar") || name.endsWith(".png")) return false
        return true
    }

    private fun Path.isJavaScriptHelperFile(): Boolean {
        if (!isScannable()) return false
        return name == "package.json" ||
            name == "tsconfig.json" ||
            name.endsWith(".ts") ||
            name.endsWith(".tsx") ||
            name.endsWith(".js") ||
            name.endsWith(".mjs") ||
            name.endsWith(".cjs")
    }
}
