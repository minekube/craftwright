package com.minekube.craftless.daemon

import com.minekube.craftless.protocol.Client
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class ClientArtifactStore(
    workspaceRoot: Path,
) {
    private val root = workspaceRoot.toAbsolutePath().normalize()

    suspend fun read(
        client: Client,
        artifactId: String,
    ): ClientArtifact? {
        val decoded = artifactId.decodeArtifactId()
        val relative = decoded.toRelativeArtifactPath()
        val artifactRoot = root.resolve(client.instance.files.artifacts).normalize()
        val artifact = artifactRoot.resolve(relative).normalize()
        require(artifact.startsWith(artifactRoot)) { "artifact id must stay under client artifacts" }
        return withContext(Dispatchers.IO) {
            if (!Files.isRegularFile(artifact)) {
                null
            } else {
                ClientArtifact(
                    bytes = Files.readAllBytes(artifact),
                    contentType = artifact.contentType(),
                )
            }
        }
    }
}

data class ClientArtifact(
    val bytes: ByteArray,
    val contentType: ContentType,
)

private fun String.decodeArtifactId(): String = URLDecoder.decode(this, StandardCharsets.UTF_8)

private fun String.toRelativeArtifactPath(): Path {
    require(isNotBlank()) { "artifact id is required" }
    val path = Path.of(this)
    require(!path.isAbsolute) { "artifact id must be relative" }
    val normalized = path.normalize()
    require(normalized.toString().isNotBlank() && normalized.toString() != ".") { "artifact id is required" }
    require(normalized.none { segment -> segment.toString() == ".." }) {
        "artifact id must stay under client artifacts"
    }
    return normalized
}

private fun Path.contentType(): ContentType =
    when (fileName.toString().substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
        "png" -> ContentType.Image.PNG
        "jpg", "jpeg" -> ContentType.Image.JPEG
        "json" -> ContentType.Application.Json
        "txt", "log" -> ContentType.Text.Plain
        else -> ContentType.Application.OctetStream
    }
