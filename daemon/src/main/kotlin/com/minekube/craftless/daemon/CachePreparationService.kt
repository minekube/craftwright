package com.minekube.craftless.daemon

import com.minekube.craftless.protocol.CacheLaunchPlan
import com.minekube.craftless.protocol.CachePrepareRequest
import com.minekube.craftless.protocol.CachePrepareResult
import com.minekube.craftless.protocol.CachePreparedArtifact
import com.minekube.craftless.protocol.CachePreparedArtifactKind
import com.minekube.craftless.protocol.CachePreparedArtifactStatus
import com.minekube.craftless.protocol.FABRIC_META_BASE_URL
import com.minekube.craftless.protocol.Loader
import com.minekube.craftless.protocol.MINECRAFT_JAVA_RUNTIME_INDEX_URL
import com.minekube.craftless.protocol.MINECRAFT_VERSION_INDEX_URL
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class CachePreparationService(
    workspaceRoot: Path,
    private val metadataFetcher: CacheMetadataFetcher = KtorCacheMetadataFetcher(),
) {
    private val root: Path = workspaceRoot.toAbsolutePath().normalize()
    private val json = Json { encodeDefaults = true }

    suspend fun prepare(request: CachePrepareRequest): CachePrepareResult {
        val versionIndex = metadataFetcher.fetchText(MINECRAFT_VERSION_INDEX_URL)
        val versionManifestUrl = versionIndex.versionManifestUrl(request.minecraftVersion)
        val versionManifest = metadataFetcher.fetchText(versionManifestUrl)
        val clientJarUrl = versionManifest.clientJarUrl(request.minecraftVersion)
        val minecraftLibraries = versionManifest.minecraftLibraries()
        val minecraftNativeLibraries = versionManifest.minecraftNativeLibraries()
        val assetIndexMetadata = versionManifest.assetIndexMetadata(request.minecraftVersion)
        val assetIndex = metadataFetcher.fetchText(assetIndexMetadata.url)
        val assetObjects = assetIndex.assetObjects()
        val javaRuntime = resolveJavaRuntime(versionManifest)
        val fabricMetadata = resolveFabricMetadata(request)
        val fabricLibraries = fabricMetadata?.profile?.fabricLibraries().orEmpty()
        val baseResult = CachePrepareResult.forRequest(request, fabricMetadata?.loaderVersion)
        val launchArgumentsArtifact =
            baseResult.launchArgumentsArtifact(
                versionManifest = versionManifest,
                fabricProfile = fabricMetadata?.profile,
            )
        val resolvedBaseArtifacts =
            baseResult.artifacts
                .map { artifact ->
                    when (artifact.kind) {
                        CachePreparedArtifactKind.MINECRAFT_VERSION_MANIFEST -> artifact.copy(source = versionManifestUrl)
                        CachePreparedArtifactKind.MINECRAFT_CLIENT_JAR -> artifact.copy(source = clientJarUrl)
                        CachePreparedArtifactKind.MINECRAFT_ASSET_INDEX -> artifact.copy(source = assetIndexMetadata.url)
                        CachePreparedArtifactKind.FABRIC_LOADER_PROFILE -> artifact.copy(source = fabricMetadata?.profileUrl)
                        else -> artifact
                    }
                }
        val loaderMetadataArtifacts =
            resolvedBaseArtifacts.filter { artifact ->
                artifact.kind == CachePreparedArtifactKind.FABRIC_LOADER_VERSIONS ||
                    artifact.kind == CachePreparedArtifactKind.FABRIC_LOADER_PROFILE
            }
        val coreArtifacts = resolvedBaseArtifacts - loaderMetadataArtifacts.toSet()
        val artifacts =
            coreArtifacts +
                javaRuntime?.artifacts.orEmpty() +
                listOfNotNull(launchArgumentsArtifact) +
                minecraftLibraries.map { it.artifact } +
                minecraftNativeLibraries.flatMap { native -> listOf(native.libraryArtifact, native.directoryArtifact) } +
                loaderMetadataArtifacts +
                assetObjects.map { it.artifact } +
                fabricLibraries.map { it.artifact }
        val result =
            baseResult.copy(
                artifacts = artifacts,
                launch = CacheLaunchPlan.fromArtifacts(artifacts),
            )
        Files.createDirectories(root)
        listOf(
            result.cacheRoot,
            result.minecraftVersionRoot,
            result.loaderRoot,
            result.runtimeRoot,
        ).forEach { handle ->
            Files.createDirectories(resolveHandle(handle))
        }
        writeTextArtifact(
            result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_VERSION_INDEX },
            versionIndex,
        )
        writeTextArtifact(
            result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_VERSION_MANIFEST },
            versionManifest,
        )
        writeBytesArtifact(
            result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_CLIENT_JAR },
            metadataFetcher.fetchBytes(clientJarUrl),
        )
        writeTextArtifact(
            result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_ASSET_INDEX },
            assetIndex,
        )
        fabricMetadata?.let { metadata ->
            writeTextArtifact(
                result.artifacts.single { it.kind == CachePreparedArtifactKind.FABRIC_LOADER_VERSIONS },
                metadata.loaderVersions,
            )
            writeTextArtifact(
                result.artifacts.single { it.kind == CachePreparedArtifactKind.FABRIC_LOADER_PROFILE },
                metadata.profile,
            )
        }
        assetObjects.forEach { asset ->
            writeBytesArtifact(asset.artifact, metadataFetcher.fetchBytes(asset.source))
        }
        javaRuntime?.let { runtime ->
            writeTextArtifact(runtime.indexArtifact, runtime.index)
            writeTextArtifact(runtime.manifestArtifact, runtime.manifest)
            runtime.files.forEach { file ->
                val target = writeBytesArtifact(file.artifact, metadataFetcher.fetchBytes(file.source))
                if (file.executable) target.toFile().setExecutable(true, true)
            }
        }
        minecraftLibraries.forEach { library ->
            writeBytesArtifact(library.artifact, metadataFetcher.fetchBytes(library.source))
        }
        minecraftNativeLibraries.forEach { native ->
            val bytes = metadataFetcher.fetchBytes(native.source)
            writeBytesArtifact(native.libraryArtifact, bytes)
            extractNativeLibrary(native, bytes)
        }
        fabricLibraries.forEach { library ->
            writeBytesArtifact(library.artifact, metadataFetcher.fetchBytes(library.source))
        }
        launchArgumentsArtifact?.let { artifact ->
            writeTextArtifact(
                artifact,
                result.launchArgumentsJson(
                    versionManifest = versionManifest,
                    fabricProfile = fabricMetadata?.profile,
                ),
            )
        }
        val manifest = resolveHandle(result.manifest)
        Files.createDirectories(manifest.parent)
        Files.writeString(manifest, json.encodeToString(result) + "\n")
        return result
    }

    private suspend fun resolveFabricMetadata(request: CachePrepareRequest): FabricCacheMetadata? {
        if (request.loader != Loader.FABRIC) return null
        val loaderVersionsUrl = fabricLoaderVersionsUrl(request.minecraftVersion)
        val loaderVersions = metadataFetcher.fetchText(loaderVersionsUrl)
        val loaderVersion = loaderVersions.compatibleFabricLoaderVersion(request.loaderVersion)
        val profileUrl = fabricLoaderProfileUrl(request.minecraftVersion, loaderVersion)
        return FabricCacheMetadata(
            loaderVersion = loaderVersion,
            loaderVersions = loaderVersions,
            profileUrl = profileUrl,
            profile = metadataFetcher.fetchText(profileUrl),
        )
    }

    private suspend fun resolveJavaRuntime(versionManifest: String): JavaRuntimeCacheMetadata? {
        val component = versionManifest.javaRuntimeComponent() ?: return null
        val platform = currentJavaRuntimePlatformKey()
        val index = metadataFetcher.fetchText(MINECRAFT_JAVA_RUNTIME_INDEX_URL)
        val manifestUrl = index.javaRuntimeManifestUrl(platform, component)
        val manifest = metadataFetcher.fetchText(manifestUrl)
        return JavaRuntimeCacheMetadata(
            platform = platform,
            component = component,
            index = index,
            manifestUrl = manifestUrl,
            manifest = manifest,
            files = manifest.javaRuntimeFiles(platform, component),
        )
    }

    private fun resolveHandle(handle: String): Path {
        require(!Path.of(handle).isAbsolute) { "cache handle must be relative" }
        val resolved = root.resolve(handle).normalize()
        require(resolved.startsWith(root)) { "cache handle must stay under the workspace root" }
        return resolved
    }

    private fun writeBytesArtifact(
        artifact: CachePreparedArtifact,
        bytes: ByteArray,
    ): Path {
        val target = resolveHandle(artifact.handle)
        Files.createDirectories(target.parent)
        Files.write(target, bytes)
        return target
    }

    private fun writeTextArtifact(
        artifact: CachePreparedArtifact,
        text: String,
    ) {
        val target = resolveHandle(artifact.handle)
        Files.createDirectories(target.parent)
        Files.writeString(target, text)
    }

    private fun extractNativeLibrary(
        native: MinecraftNativeLibraryArtifact,
        bytes: ByteArray,
    ) {
        val targetRoot = resolveHandle(native.directoryArtifact.handle)
        Files.createDirectories(targetRoot)
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                if (!entry.isDirectory && !entry.name.startsWith("META-INF/")) {
                    val target = targetRoot.resolve(entry.name).normalize()
                    require(target.startsWith(targetRoot)) { "native library entry must stay under native directory" }
                    Files.createDirectories(target.parent)
                    Files.copy(zip, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
                zip.closeEntry()
            }
        }
    }
}

private data class FabricCacheMetadata(
    val loaderVersion: String,
    val loaderVersions: String,
    val profileUrl: String,
    val profile: String,
)

private data class JavaRuntimeCacheMetadata(
    val platform: String,
    val component: String,
    val index: String,
    val manifestUrl: String,
    val manifest: String,
    val files: List<JavaRuntimeFileArtifact>,
) {
    val indexArtifact: CachePreparedArtifact =
        CachePreparedArtifact(
            kind = CachePreparedArtifactKind.JAVA_RUNTIME_INDEX,
            handle = "cache/runtimes/index.json",
            source = MINECRAFT_JAVA_RUNTIME_INDEX_URL,
            status = CachePreparedArtifactStatus.RESOLVED,
        )

    val manifestArtifact: CachePreparedArtifact =
        CachePreparedArtifact(
            kind = CachePreparedArtifactKind.JAVA_RUNTIME_MANIFEST,
            handle = "cache/runtimes/$platform/$component/manifest.json",
            source = manifestUrl,
            status = CachePreparedArtifactStatus.RESOLVED,
        )

    val artifacts: List<CachePreparedArtifact> = listOf(indexArtifact, manifestArtifact) + files.map { it.artifact }
}

@Serializable
private data class CacheLaunchArgumentsFile(
    val schemaVersion: Int = 1,
    val mainClass: String,
    val jvm: List<String>,
    val game: List<String>,
)

interface CacheMetadataFetcher {
    suspend fun fetchText(url: String): String

    suspend fun fetchBytes(url: String): ByteArray = fetchText(url).encodeToByteArray()
}

class KtorCacheMetadataFetcher : CacheMetadataFetcher {
    override suspend fun fetchText(url: String): String =
        httpClient().use { http ->
            val response = http.get(url)
            require(response.status.isSuccess()) { "metadata fetch failed for $url: ${response.status.value}" }
            response.bodyAsText()
        }

    override suspend fun fetchBytes(url: String): ByteArray =
        httpClient().use { http ->
            val response = http.get(url)
            require(response.status.isSuccess()) { "artifact fetch failed for $url: ${response.status.value}" }
            response.bodyAsBytes()
        }

    private fun httpClient(): HttpClient =
        HttpClient(CIO) {
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 60_000
                requestTimeoutMillis = 120_000
            }
        }
}

private fun String.versionManifestUrl(minecraftVersion: String): String {
    val versions =
        Json
            .parseToJsonElement(this)
            .jsonObject["versions"]
            ?.jsonArray
            .orEmpty()
    val version =
        versions.firstOrNull { version ->
            version.jsonObject["id"]?.jsonPrimitive?.content == minecraftVersion
        } ?: error("minecraft version $minecraftVersion was not found in version index")
    return version.jsonObject["url"]?.jsonPrimitive?.content ?: error("minecraft version $minecraftVersion is missing metadata url")
}

private fun String.clientJarUrl(minecraftVersion: String): String =
    Json
        .parseToJsonElement(this)
        .jsonObject["downloads"]
        ?.jsonObject
        ?.get("client")
        ?.jsonObject
        ?.get("url")
        ?.jsonPrimitive
        ?.content
        ?: error("minecraft version $minecraftVersion is missing client jar url")

private fun String.assetIndexMetadata(minecraftVersion: String): AssetIndexMetadata {
    val assetIndex =
        Json
            .parseToJsonElement(this)
            .jsonObject["assetIndex"]
            ?.jsonObject
            ?: error("minecraft version $minecraftVersion is missing asset index metadata")
    val url = assetIndex["url"]?.jsonPrimitive?.content ?: error("minecraft version $minecraftVersion is missing asset index url")
    return AssetIndexMetadata(url)
}

private data class AssetIndexMetadata(
    val url: String,
)

private fun String.compatibleFabricLoaderVersion(requestedLoaderVersion: String?): String {
    val versions =
        Json
            .parseToJsonElement(this)
            .jsonArray
            .mapNotNull { entry ->
                val loader = entry.jsonObject["loader"]?.jsonObject ?: return@mapNotNull null
                val version = loader["version"]?.jsonPrimitive?.content ?: return@mapNotNull null
                FabricLoaderVersion(
                    version = version,
                    stable = loader["stable"]?.jsonPrimitive?.booleanOrNull == true,
                )
            }
    requestedLoaderVersion?.let { requested ->
        return versions.firstOrNull { it.version == requested }?.version
            ?: error("fabric loader version $requested is not compatible with this minecraft version")
    }
    return versions.firstOrNull { it.stable }?.version
        ?: versions.firstOrNull()?.version
        ?: error("no compatible fabric loader version was found")
}

private data class FabricLoaderVersion(
    val version: String,
    val stable: Boolean,
)

private fun CachePrepareResult.launchArgumentsArtifact(
    versionManifest: String,
    fabricProfile: String?,
): CachePreparedArtifact? {
    val hasLaunchMainClass = fabricProfile?.launchMainClass() != null || versionManifest.launchMainClass() != null
    if (!hasLaunchMainClass) return null
    return CachePreparedArtifact(
        kind = CachePreparedArtifactKind.LAUNCH_ARGUMENTS,
        handle = manifest.removeSuffix(".json") + ".launch.json",
        status = CachePreparedArtifactStatus.INDEXED,
    )
}

private fun CachePrepareResult.launchArgumentsJson(
    versionManifest: String,
    fabricProfile: String?,
): String {
    val variables =
        mapOf(
            "assets_root" to "cache/assets",
            "classpath" to launch.classpath.joinToString(File.pathSeparator),
            "game_directory" to "{{gameRoot}}",
            "natives_directory" to launch.nativePath.joinToString(File.pathSeparator),
            "version_name" to minecraftVersion,
        )
    val mainClass =
        fabricProfile?.launchMainClass()
            ?: versionManifest.launchMainClass()
            ?: error("cache preparation needs a launch main class")
    val jvm =
        versionManifest
            .launchArgumentValues("jvm")
            .map { argument -> argument.resolveLaunchVariables(variables) }
    val game =
        (
            versionManifest.launchArgumentValues("game") +
                fabricProfile.launchArgumentValuesOrEmpty("game")
        ).map { argument -> argument.resolveLaunchVariables(variables) }
    return Json.encodeToString(
        CacheLaunchArgumentsFile(
            mainClass = mainClass,
            jvm = jvm,
            game = game,
        ),
    ) + "\n"
}

private fun String?.launchArgumentValuesOrEmpty(section: String): List<String> = this?.launchArgumentValues(section).orEmpty()

private fun String.launchMainClass(): String? =
    Json
        .parseToJsonElement(this)
        .jsonObject["mainClass"]
        ?.jsonPrimitive
        ?.content

private fun String.launchArgumentValues(section: String): List<String> =
    Json
        .parseToJsonElement(this)
        .jsonObject["arguments"]
        ?.jsonObject
        ?.get(section)
        ?.jsonArray
        .orEmpty()
        .flatMap { argument -> argument.launchArgumentValues() }

private fun JsonElement.launchArgumentValues(): List<String> {
    jsonPrimitiveOrNull()?.content?.let { return listOf(it) }
    val item = jsonObject
    if (item["rules"]?.jsonArray.orEmpty().any { rule -> rule.jsonObject["action"]?.jsonPrimitive?.content == "disallow" }) {
        return emptyList()
    }
    val value = item["value"] ?: return emptyList()
    value.jsonPrimitiveOrNull()?.content?.let { return listOf(it) }
    return value.jsonArray.map { element -> element.jsonPrimitive.content }
}

private fun JsonElement.jsonPrimitiveOrNull() =
    runCatching {
        jsonPrimitive
    }.getOrNull()

private fun String.resolveLaunchVariables(variables: Map<String, String>): String =
    LAUNCH_VARIABLE_PATTERN.replace(this) { match ->
        variables[match.groupValues[1]] ?: "{{${match.groupValues[1]}}}"
    }

private val LAUNCH_VARIABLE_PATTERN = Regex("""\$\{([^}]+)}""")

private fun String.javaRuntimeComponent(): String? =
    Json
        .parseToJsonElement(this)
        .jsonObject["javaVersion"]
        ?.jsonObject
        ?.get("component")
        ?.jsonPrimitive
        ?.content
        ?.also { component -> requireFileSafeCacheSegment(component, "Java runtime component") }

private fun String.javaRuntimeManifestUrl(
    platform: String,
    component: String,
): String =
    Json
        .parseToJsonElement(this)
        .jsonObject[platform]
        ?.jsonObject
        ?.get(component)
        ?.jsonArray
        ?.firstOrNull()
        ?.jsonObject
        ?.get("manifest")
        ?.jsonObject
        ?.get("url")
        ?.jsonPrimitive
        ?.content
        ?: error("Java runtime manifest for $platform/$component was not found")

private fun String.javaRuntimeFiles(
    platform: String,
    component: String,
): List<JavaRuntimeFileArtifact> =
    Json
        .parseToJsonElement(this)
        .jsonObject["files"]
        ?.jsonObject
        .orEmpty()
        .mapNotNull { (path, file) ->
            val item = file.jsonObject
            val type = item["type"]?.jsonPrimitive?.content ?: return@mapNotNull null
            if (type != "file") return@mapNotNull null
            val source =
                item["downloads"]
                    ?.jsonObject
                    ?.get("raw")
                    ?.jsonObject
                    ?.get("url")
                    ?.jsonPrimitive
                    ?.content
                    ?: return@mapNotNull null
            JavaRuntimeFileArtifact(
                platform = platform,
                component = component,
                path = path,
                source = source,
                executable = path.isJavaExecutablePath(),
            )
        }

private fun currentJavaRuntimePlatformKey(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    return when {
        "win" in os -> "windows-x64"
        "mac" in os || "darwin" in os -> if ("aarch64" in arch || "arm64" in arch) "mac-os-arm64" else "mac-os"
        else -> "linux"
    }
}

private fun String.isJavaExecutablePath(): Boolean = this == "bin/java" || this == "bin/java.exe"

private fun fabricLoaderVersionsUrl(minecraftVersion: String): String = "$FABRIC_META_BASE_URL/versions/loader/$minecraftVersion"

private fun fabricLoaderProfileUrl(
    minecraftVersion: String,
    loaderVersion: String,
): String = "$FABRIC_META_BASE_URL/versions/loader/$minecraftVersion/$loaderVersion/profile/json"

private fun String.fabricLibraries(): List<FabricLibraryArtifact> =
    Json
        .parseToJsonElement(this)
        .jsonObject["libraries"]
        ?.jsonArray
        .orEmpty()
        .mapNotNull { library ->
            val item = library.jsonObject
            item["downloads"]
                ?.jsonObject
                ?.get("artifact")
                ?.jsonObject
                ?.let { artifact ->
                    val url = artifact["url"]?.jsonPrimitive?.content ?: return@let null
                    return@mapNotNull FabricLibraryArtifact(url)
                }
            val name = item["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val baseUrl = item["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val path = name.mavenPath()
            FabricLibraryArtifact(baseUrl.trimEnd('/') + "/$path")
        }

private fun String.minecraftLibraries(): List<MinecraftLibraryArtifact> =
    Json
        .parseToJsonElement(this)
        .jsonObject["libraries"]
        ?.jsonArray
        .orEmpty()
        .mapNotNull { library ->
            val item = library.jsonObject
            item["downloads"]
                ?.jsonObject
                ?.get("artifact")
                ?.jsonObject
                ?.get("url")
                ?.jsonPrimitive
                ?.content
                ?.let(::MinecraftLibraryArtifact)
        }

private fun String.minecraftNativeLibraries(): List<MinecraftNativeLibraryArtifact> =
    Json
        .parseToJsonElement(this)
        .jsonObject["libraries"]
        ?.jsonArray
        .orEmpty()
        .mapNotNull { library ->
            val item = library.jsonObject
            val classifier =
                item["natives"]
                    ?.jsonObject
                    ?.get(currentNativeClassifierKey())
                    ?.jsonPrimitive
                    ?.content
                    ?: return@mapNotNull null
            item["downloads"]
                ?.jsonObject
                ?.get("classifiers")
                ?.jsonObject
                ?.get(classifier)
                ?.jsonObject
                ?.get("url")
                ?.jsonPrimitive
                ?.content
                ?.let(::MinecraftNativeLibraryArtifact)
        }

private fun currentNativeClassifierKey(): String {
    val os = System.getProperty("os.name").lowercase()
    return when {
        "win" in os -> "windows"
        "mac" in os || "darwin" in os -> "osx"
        else -> "linux"
    }
}

private fun String.assetObjects(): List<MinecraftAssetObject> =
    Json
        .parseToJsonElement(this)
        .jsonObject["objects"]
        ?.jsonObject
        .orEmpty()
        .values
        .mapNotNull { asset ->
            val hash = asset.jsonObject["hash"]?.jsonPrimitive?.content ?: return@mapNotNull null
            MinecraftAssetObject(hash)
        }

private data class MinecraftAssetObject(
    val hash: String,
) {
    val source: String = "$MINECRAFT_ASSET_BASE_URL/${hash.take(2)}/$hash"

    val artifact: CachePreparedArtifact =
        CachePreparedArtifact(
            kind = CachePreparedArtifactKind.MINECRAFT_ASSET_OBJECT,
            handle = "cache/assets/objects/${hash.sha256Hex()}.asset",
            source = source,
            status = CachePreparedArtifactStatus.CACHED,
        )
}

private const val MINECRAFT_ASSET_BASE_URL = "https://resources.download.minecraft.net"

private data class MinecraftLibraryArtifact(
    val source: String,
) {
    private val handle: String = "cache/libraries/minecraft/${source.sha256Hex()}.jar"

    val artifact: CachePreparedArtifact =
        CachePreparedArtifact(
            kind = CachePreparedArtifactKind.MINECRAFT_LIBRARY,
            handle = handle,
            source = source,
            status = CachePreparedArtifactStatus.CACHED,
        )
}

private data class MinecraftNativeLibraryArtifact(
    val source: String,
) {
    private val fingerprint: String = source.sha256Hex()

    val libraryArtifact: CachePreparedArtifact =
        CachePreparedArtifact(
            kind = CachePreparedArtifactKind.MINECRAFT_NATIVE_LIBRARY,
            handle = "cache/libraries/native/$fingerprint.jar",
            source = source,
            status = CachePreparedArtifactStatus.CACHED,
        )

    val directoryArtifact: CachePreparedArtifact =
        CachePreparedArtifact(
            kind = CachePreparedArtifactKind.MINECRAFT_NATIVE_DIRECTORY,
            handle = "cache/natives/$fingerprint",
            status = CachePreparedArtifactStatus.EXTRACTED,
        )
}

private data class JavaRuntimeFileArtifact(
    val platform: String,
    val component: String,
    val path: String,
    val source: String,
    val executable: Boolean,
) {
    init {
        requireFileSafeCacheSegment(platform, "Java runtime platform")
        requireFileSafeCacheSegment(component, "Java runtime component")
        requireRelativeCachePath(path, "Java runtime file path")
    }

    val artifact: CachePreparedArtifact =
        CachePreparedArtifact(
            kind =
                if (executable) {
                    CachePreparedArtifactKind.JAVA_RUNTIME_EXECUTABLE
                } else {
                    CachePreparedArtifactKind.JAVA_RUNTIME_FILE
                },
            handle = "cache/runtimes/$platform/$component/image/$path",
            source = source,
            status = CachePreparedArtifactStatus.CACHED,
        )
}

private data class FabricLibraryArtifact(
    val source: String,
) {
    private val handle: String = "cache/libraries/fabric/${source.sha256Hex()}.jar"

    val artifact: CachePreparedArtifact =
        CachePreparedArtifact(
            kind = CachePreparedArtifactKind.FABRIC_LIBRARY,
            handle = handle,
            status = CachePreparedArtifactStatus.CACHED,
        )
}

private fun String.sha256Hex(): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(encodeToByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

private fun String.mavenPath(): String {
    val parts = split(':')
    require(parts.size >= 3) { "maven coordinate $this must include group, artifact, and version" }
    val group = parts[0].replace('.', '/')
    val artifact = parts[1]
    val version = parts[2]
    val classifier =
        parts
            .getOrNull(3)
            ?.takeUnless { it.isBlank() }
            ?.let { "-$it" }
            .orEmpty()
    return "$group/$artifact/$version/$artifact-$version$classifier.jar"
}

private fun requireFileSafeCacheSegment(
    value: String,
    label: String,
) {
    require(value.isNotBlank()) { "$label is required" }
    require(!value.contains('/')) { "$label must be a file-safe segment" }
    require(!value.contains('\\')) { "$label must use forward slashes" }
    require(!value.contains("..")) { "$label must be a file-safe segment" }
}

private fun requireRelativeCachePath(
    value: String,
    label: String,
) {
    require(value.isNotBlank()) { "$label is required" }
    require(!value.contains('\\')) { "$label must use forward slashes" }
    require(!Path.of(value).isAbsolute) { "$label must be relative" }
    val normalized = Path.of(value).normalize()
    require(!normalized.startsWith("..")) { "$label must stay under the runtime image" }
}
