package dev.minekube.craftwright.testkit

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

data class LocalServerFixture(
    val root: Path,
    val port: Int,
) {
    fun prepare(): LocalServerLayout {
        Files.createDirectories(root)
        val logsDir = root.resolve("logs")
        val artifactsDir = root.resolve("artifacts")
        Files.createDirectories(logsDir)
        Files.createDirectories(artifactsDir)

        val serverProperties = root.resolve("server.properties")
        serverProperties.writeText(
            """
            online-mode=false
            server-port=$port
            enable-command-block=true
            spawn-protection=0
            """.trimIndent() + "\n"
        )

        return LocalServerLayout(
            root = root,
            serverProperties = serverProperties,
            logsDir = logsDir,
            artifactsDir = artifactsDir,
        )
    }
}

data class LocalServerLayout(
    val root: Path,
    val serverProperties: Path,
    val logsDir: Path,
    val artifactsDir: Path,
)
