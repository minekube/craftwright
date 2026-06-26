package com.minekube.craftless.daemon

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JavaRuntimeValidatorTest {
    @Test
    fun `validates Java version vendor and architecture from executable output`() {
        val java =
            fakeExecutable(
                """
                #!/usr/bin/env sh
                echo 'openjdk version "25.0.3" 2026-04-21 LTS' >&2
                echo 'Eclipse Temurin Runtime Environment' >&2
                echo '    os.arch = aarch64' >&2
                """.trimIndent(),
            )

        val result = JavaRuntimeValidator(timeoutMillis = 2_000).validate(java)

        assertEquals(JavaRuntimeValidationStatus.VALID, result.status)
        assertEquals(25, result.majorVersion)
        assertEquals("25.0.3", result.version)
        assertEquals("Eclipse Temurin", result.vendor)
        assertEquals("aarch64", result.architecture)
    }

    @Test
    fun `returns timeout for executable that does not finish within budget`() {
        val java =
            fakeExecutable(
                """
                #!/usr/bin/env sh
                sleep 5
                """.trimIndent(),
            )

        val result = JavaRuntimeValidator(timeoutMillis = 100).validate(java)

        assertEquals(JavaRuntimeValidationStatus.TIMEOUT, result.status)
        assertTrue(result.reason.orEmpty().contains("timed out"))
    }

    private fun fakeExecutable(script: String): Path {
        val directory = Files.createTempDirectory("craftless-java-validator")
        val executable = directory.resolve("java")
        Files.writeString(executable, script + "\n")
        executable.toFile().setExecutable(true, true)
        return executable
    }
}
