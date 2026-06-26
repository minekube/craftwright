package com.minekube.craftless.daemon

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class JavaRuntimeValidator(
    private val timeoutMillis: Long = 5_000,
) {
    fun validate(executable: Path): JavaRuntimeValidationResult {
        if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
            return JavaRuntimeValidationResult(
                executable = executable,
                status = JavaRuntimeValidationStatus.INVALID,
                reason = "Java executable is not an executable file",
            )
        }
        val process =
            runCatching {
                ProcessBuilder(executable.toString(), "-version")
                    .redirectErrorStream(true)
                    .start()
            }.getOrElse { error ->
                return JavaRuntimeValidationResult(
                    executable = executable,
                    status = JavaRuntimeValidationStatus.ERRORED,
                    reason = error.message ?: "Java executable could not be started",
                )
            }
        val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            return JavaRuntimeValidationResult(
                executable = executable,
                status = JavaRuntimeValidationStatus.TIMEOUT,
                reason = "Java validation timed out after ${timeoutMillis}ms",
            )
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val version = VERSION_REGEX.find(output)?.groupValues?.get(1)
        val major = version?.javaMajorVersion()
        if (process.exitValue() != 0 || version == null || major == null) {
            return JavaRuntimeValidationResult(
                executable = executable,
                status = JavaRuntimeValidationStatus.INVALID,
                reason = "Java executable did not return parseable version output",
                output = output,
            )
        }
        return JavaRuntimeValidationResult(
            executable = executable,
            status = JavaRuntimeValidationStatus.VALID,
            majorVersion = major,
            version = version,
            vendor = output.detectJavaVendor(),
            architecture = ARCH_REGEX.find(output)?.groupValues?.get(1),
            output = output,
        )
    }

    private fun String.javaMajorVersion(): Int? {
        if (startsWith("1.")) return split(".").getOrNull(1)?.toIntOrNull()
        return takeWhile { it.isDigit() }.toIntOrNull()
    }

    private fun String.detectJavaVendor(): String? =
        when {
            contains("Eclipse Temurin", ignoreCase = true) -> "Eclipse Temurin"
            contains("OpenJDK", ignoreCase = true) -> "OpenJDK"
            else -> null
        }

    private companion object {
        val VERSION_REGEX = Regex("""version\s+"([^"]+)"""")
        val ARCH_REGEX = Regex("""os\.arch\s*=\s*([^\s]+)""")
    }
}

data class JavaRuntimeValidationResult(
    val executable: Path,
    val status: JavaRuntimeValidationStatus,
    val majorVersion: Int? = null,
    val version: String? = null,
    val vendor: String? = null,
    val architecture: String? = null,
    val reason: String? = null,
    val output: String = "",
)

enum class JavaRuntimeValidationStatus {
    VALID,
    INVALID,
    TIMEOUT,
    ERRORED,
}
