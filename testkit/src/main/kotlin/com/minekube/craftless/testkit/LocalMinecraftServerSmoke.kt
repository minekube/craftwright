package com.minekube.craftless.testkit

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import java.nio.file.Path
import kotlinx.coroutines.runBlocking

object LocalMinecraftServerSmoke {
    fun run(config: LocalMinecraftServerSmokeConfig = LocalMinecraftServerSmokeConfig.fromEnvironment()): LocalMinecraftServerSmokeResult {
        return runWithServer(config)
    }

    fun runWithServer(
        config: LocalMinecraftServerSmokeConfig = LocalMinecraftServerSmokeConfig.fromEnvironment(),
        provisionServerJar: suspend (LocalServerLayout, HttpClient) -> Path = { layout, http ->
            layout.provisionMinecraftServerJar(
                version = config.minecraftVersion,
                provisioner = MinecraftServerJarProvisioner(http),
            )
        },
        action: (LocalServerLayout, LocalMinecraftServerHandle) -> Unit = { _, _ -> },
    ): LocalMinecraftServerSmokeResult {
        if (!config.enabled) {
            return LocalMinecraftServerSmokeResult(
                status = LocalMinecraftServerSmokeStatus.SKIPPED,
                message = "set CRAFTLESS_LOCAL_SERVER_SMOKE=1 to run the local server smoke",
                root = config.root,
            )
        }

        return runBlocking {
            HttpClient(CIO).use { http ->
                val layout = LocalServerFixture(root = config.root, port = config.port).prepare()
                val serverJar = provisionServerJar(layout, http)
                val server = layout.startMinecraftServer(
                    serverJar = serverJar,
                    javaExecutable = config.javaExecutable,
                    minHeap = config.minHeap,
                    maxHeap = config.maxHeap,
                    readinessTimeoutMillis = config.readinessTimeoutMillis,
                    shutdownTimeoutMillis = config.shutdownTimeoutMillis,
                )
                val processResult = try {
                    action(layout, server)
                    server.stopAndCollect()
                } catch (failure: Throwable) {
                    if (server.isRunning()) {
                        server.stopAndCollect()
                    }
                    throw failure
                }
                LocalMinecraftServerSmokeResult(
                    status = LocalMinecraftServerSmokeStatus.RAN,
                    message = "local Minecraft server smoke collected ${processResult.evidenceCount} evidence event(s)",
                    root = config.root,
                    serverLog = layout.serverLog,
                    evidenceLog = layout.evidenceLog,
                    exitCode = processResult.exitCode,
                    evidenceCount = processResult.evidenceCount,
                )
            }
        }
    }
}

data class LocalMinecraftServerSmokeResult(
    val status: LocalMinecraftServerSmokeStatus,
    val message: String,
    val root: Path,
    val serverLog: Path? = null,
    val evidenceLog: Path? = null,
    val exitCode: Int? = null,
    val evidenceCount: Int = 0,
)

enum class LocalMinecraftServerSmokeStatus {
    SKIPPED,
    RAN,
}

fun main() {
    val result = LocalMinecraftServerSmoke.run()
    println(result.message)
    result.serverLog?.let { println("serverLog=$it") }
    result.evidenceLog?.let { println("evidenceLog=$it") }
    result.exitCode?.let { println("exitCode=$it") }
}

data class LocalMinecraftServerSmokeConfig(
    val enabled: Boolean,
    val root: Path,
    val minecraftVersion: String = "1.21.6",
    val port: Int = 25565,
    val javaExecutable: Path = Path.of(System.getProperty("java.home")).resolve("bin").resolve("java"),
    val readinessTimeoutMillis: Long = 120_000,
    val shutdownTimeoutMillis: Long = 10_000,
    val minHeap: String = "512M",
    val maxHeap: String = "1G",
) {
    init {
        require(minecraftVersion.isNotBlank()) { "minecraft smoke version is required" }
        require(port in 1..65535) { "minecraft smoke server port must be between 1 and 65535" }
        require(readinessTimeoutMillis > 0) { "minecraft smoke readiness timeout must be positive" }
        require(shutdownTimeoutMillis > 0) { "minecraft smoke shutdown timeout must be positive" }
        require(minHeap.isNotBlank()) { "minecraft smoke minimum heap is required" }
        require(maxHeap.isNotBlank()) { "minecraft smoke maximum heap is required" }
    }

    companion object {
        private const val ENABLED = "CRAFTLESS_LOCAL_SERVER_SMOKE"
        private const val FABRIC_ENABLED = "CRAFTLESS_FABRIC_CLIENT_SMOKE"
        private const val ROOT = "CRAFTLESS_LOCAL_SERVER_SMOKE_ROOT"
        private const val VERSION = "CRAFTLESS_SMOKE_MINECRAFT_VERSION"
        private const val PORT = "CRAFTLESS_SMOKE_SERVER_PORT"
        private const val JAVA_EXECUTABLE = "CRAFTLESS_SMOKE_JAVA_EXECUTABLE"
        private const val READINESS_TIMEOUT = "CRAFTLESS_SMOKE_READINESS_TIMEOUT_MS"
        private const val SHUTDOWN_TIMEOUT = "CRAFTLESS_SMOKE_SHUTDOWN_TIMEOUT_MS"
        private const val MIN_HEAP = "CRAFTLESS_SMOKE_MIN_HEAP"
        private const val MAX_HEAP = "CRAFTLESS_SMOKE_MAX_HEAP"

        fun fromEnvironment(env: Map<String, String> = System.getenv()): LocalMinecraftServerSmokeConfig =
            LocalMinecraftServerSmokeConfig(
                enabled = env.isEnabled(ENABLED) || env.isEnabled(FABRIC_ENABLED),
                root = env[ROOT]?.takeIf { it.isNotBlank() }
                    ?.let(Path::of)
                    ?: Path.of("build", "craftless-local-server-smoke"),
                minecraftVersion = env[VERSION]?.takeIf { it.isNotBlank() } ?: "1.21.6",
                port = env[PORT]?.toIntStrict(PORT) ?: 25565,
                javaExecutable = env[JAVA_EXECUTABLE]?.takeIf { it.isNotBlank() }
                    ?.let(Path::of)
                    ?: Path.of(System.getProperty("java.home")).resolve("bin").resolve("java"),
                readinessTimeoutMillis = env[READINESS_TIMEOUT]?.toLongStrict(READINESS_TIMEOUT) ?: 120_000,
                shutdownTimeoutMillis = env[SHUTDOWN_TIMEOUT]?.toLongStrict(SHUTDOWN_TIMEOUT) ?: 10_000,
                minHeap = env[MIN_HEAP]?.takeIf { it.isNotBlank() } ?: "512M",
                maxHeap = env[MAX_HEAP]?.takeIf { it.isNotBlank() } ?: "1G",
            )

        private fun String.toIntStrict(name: String): Int =
            toIntOrNull() ?: error("$name must be an integer")

        private fun String.toLongStrict(name: String): Long =
            toLongOrNull() ?: error("$name must be a long integer")

        private fun Map<String, String>.isEnabled(name: String): Boolean =
            this[name] == "1" || this[name].equals("true", ignoreCase = true)
    }
}
