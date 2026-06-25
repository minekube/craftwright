package com.minekube.craftless.testkit

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

class MinecraftServerJarProvisioner(
    private val http: HttpClient,
    private val manifestUrl: String = DEFAULT_MANIFEST_URL,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun provision(
        version: String,
        target: Path,
    ): Path {
        require(version.isNotBlank()) { "minecraft server version is required" }
        val manifest =
            json.decodeFromString<LauncherVersionManifest>(
                http.get(manifestUrl).bodyAsText(),
            )
        val versionUrl =
            manifest.versions
                .firstOrNull { it.id == version }
                ?.url
                ?: error("minecraft server version $version not found in version manifest")
        val versionMetadata =
            json.decodeFromString<LauncherVersionMetadata>(
                http.get(versionUrl).bodyAsText(),
            )
        val serverUrl =
            versionMetadata.downloads.server?.url
                ?: error("minecraft server version $version does not include a server download")
        Files.createDirectories(target.parent)
        Files.write(target, http.get(serverUrl).body<ByteArray>())
        return target
    }

    companion object {
        const val DEFAULT_MANIFEST_URL: String =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    }
}

suspend fun LocalServerLayout.provisionMinecraftServerJar(
    version: String,
    provisioner: MinecraftServerJarProvisioner,
): Path =
    provisioner.provision(
        version = version,
        target = artifactsDir.resolve("minecraft-server-$version.jar"),
    )

@Serializable
private data class LauncherVersionManifest(
    val versions: List<LauncherVersionEntry> = emptyList(),
)

@Serializable
private data class LauncherVersionEntry(
    val id: String,
    val url: String,
)

@Serializable
private data class LauncherVersionMetadata(
    val downloads: LauncherDownloads = LauncherDownloads(),
)

@Serializable
private data class LauncherDownloads(
    val server: LauncherDownload? = null,
)

@Serializable
private data class LauncherDownload(
    val url: String,
)
