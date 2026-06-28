package com.minekube.craftless.daemon

import com.minekube.craftless.protocol.CacheCleanupRequest
import com.minekube.craftless.protocol.CacheExportRequest
import com.minekube.craftless.protocol.CachePrepareRequest
import com.minekube.craftless.protocol.CachePreparedArtifactKind
import com.minekube.craftless.protocol.FABRIC_META_BASE_URL
import com.minekube.craftless.protocol.JavaRuntimeProviderKind
import com.minekube.craftless.protocol.JavaRuntimeSelectionStatus
import com.minekube.craftless.protocol.Loader
import com.minekube.craftless.protocol.MINECRAFT_JAVA_RUNTIME_INDEX_URL
import com.minekube.craftless.protocol.MINECRAFT_VERSION_INDEX_URL
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CachePreparationServiceTest {
    @Test
    fun `cache preparation resolves latest release alias before building cache handles`() =
        runBlocking {
            val workspace = Files.createTempDirectory("craftless-cache-latest-release")
            val releaseVersionUrl = "https://metadata.test/1.21.6.json"
            val snapshotVersionUrl = "https://metadata.test/26.3-snapshot-1.json"
            val clientJarUrl = "https://metadata.test/client-1.21.6.jar"
            val assetIndexUrl = "https://metadata.test/assets/1.21.6.json"
            val service =
                CachePreparationService(
                    workspaceRoot = workspace,
                    metadataFetcher =
                        StaticCacheMetadataFetcher(
                            mapOf(
                                MINECRAFT_VERSION_INDEX_URL to
                                    """
                                    {
                                      "latest": {
                                        "release": "1.21.6",
                                        "snapshot": "26.3-snapshot-1"
                                      },
                                      "versions": [
                                        { "id": "1.21.6", "url": "$releaseVersionUrl" },
                                        { "id": "26.3-snapshot-1", "url": "$snapshotVersionUrl" }
                                      ]
                                    }
                                    """.trimIndent(),
                                releaseVersionUrl to
                                    """
                                    {
                                      "id": "1.21.6",
                                      "assetIndex": {
                                        "id": "26",
                                        "url": "$assetIndexUrl"
                                      },
                                      "downloads": {
                                        "client": { "url": "$clientJarUrl" }
                                      }
                                    }
                                    """.trimIndent(),
                                assetIndexUrl to """{"objects":{}}""",
                            ),
                            binaryResponses = mapOf(clientJarUrl to "client-jar".encodeToByteArray()),
                        ),
                )

            val result = service.prepare(CachePrepareRequest("latest-release", Loader.VANILLA))

            assertEquals("1.21.6", result.minecraftVersion)
            assertEquals("cache/prepared/1.21.6-vanilla.json", result.manifest)
            assertEquals(
                "cache/minecraft/versions/1.21.6/version.json",
                result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_VERSION_MANIFEST }.handle,
            )
            assertEquals(
                releaseVersionUrl,
                result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_VERSION_MANIFEST }.source,
            )
            assertEquals(
                "cache/minecraft/versions/1.21.6/client.jar",
                result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_CLIENT_JAR }.handle,
            )
            assertTrue(Files.isRegularFile(workspace.resolve(result.manifest)))
            assertTrue(Files.isRegularFile(workspace.resolve("cache/minecraft/versions/1.21.6/client.jar")))
            assertFalse(Files.exists(workspace.resolve("cache/minecraft/versions/latest-release")))
        }

    @Test
    fun `cache preparation resolves latest snapshot alias before building cache handles`() =
        runBlocking {
            val workspace = Files.createTempDirectory("craftless-cache-latest-snapshot")
            val releaseVersionUrl = "https://metadata.test/1.21.6.json"
            val snapshotVersionUrl = "https://metadata.test/26.3-snapshot-1.json"
            val clientJarUrl = "https://metadata.test/client-26.3-snapshot-1.jar"
            val assetIndexUrl = "https://metadata.test/assets/26.3-snapshot-1.json"
            val service =
                CachePreparationService(
                    workspaceRoot = workspace,
                    metadataFetcher =
                        StaticCacheMetadataFetcher(
                            mapOf(
                                MINECRAFT_VERSION_INDEX_URL to
                                    """
                                    {
                                      "latest": {
                                        "release": "1.21.6",
                                        "snapshot": "26.3-snapshot-1"
                                      },
                                      "versions": [
                                        { "id": "1.21.6", "url": "$releaseVersionUrl" },
                                        { "id": "26.3-snapshot-1", "url": "$snapshotVersionUrl" }
                                      ]
                                    }
                                    """.trimIndent(),
                                snapshotVersionUrl to
                                    """
                                    {
                                      "id": "26.3-snapshot-1",
                                      "assetIndex": {
                                        "id": "26-snapshot",
                                        "url": "$assetIndexUrl"
                                      },
                                      "downloads": {
                                        "client": { "url": "$clientJarUrl" }
                                      }
                                    }
                                    """.trimIndent(),
                                assetIndexUrl to """{"objects":{}}""",
                            ),
                            binaryResponses = mapOf(clientJarUrl to "snapshot-client-jar".encodeToByteArray()),
                        ),
                )

            val result = service.prepare(CachePrepareRequest("latest-snapshot", Loader.VANILLA))

            assertEquals("26.3-snapshot-1", result.minecraftVersion)
            assertEquals("cache/prepared/26.3-snapshot-1-vanilla.json", result.manifest)
            assertEquals(
                "cache/minecraft/versions/26.3-snapshot-1/version.json",
                result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_VERSION_MANIFEST }.handle,
            )
            assertEquals(
                snapshotVersionUrl,
                result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_VERSION_MANIFEST }.source,
            )
            assertTrue(Files.isRegularFile(workspace.resolve(result.manifest)))
            assertTrue(Files.isRegularFile(workspace.resolve("cache/minecraft/versions/26.3-snapshot-1/client.jar")))
            assertFalse(Files.exists(workspace.resolve("cache/minecraft/versions/latest-snapshot")))
        }

    @Test
    fun `fabric cache preparation resolves latest release before requesting fabric metadata`() =
        runBlocking {
            val workspace = Files.createTempDirectory("craftless-cache-latest-fabric")
            val versionUrl = "https://metadata.test/1.21.6.json"
            val clientJarUrl = "https://metadata.test/client.jar"
            val assetIndexUrl = "https://metadata.test/assets/1.21.6.json"
            val loaderVersionsUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6"
            val loaderProfileUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6/0.17.2/profile/json"
            val fabricLoaderJarUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.17.2/fabric-loader-0.17.2.jar"
            val service =
                CachePreparationService(
                    workspaceRoot = workspace,
                    metadataFetcher =
                        StaticCacheMetadataFetcher(
                            mapOf(
                                MINECRAFT_VERSION_INDEX_URL to
                                    """
                                    {
                                      "latest": {
                                        "release": "1.21.6",
                                        "snapshot": "26.3-snapshot-1"
                                      },
                                      "versions": [{ "id": "1.21.6", "url": "$versionUrl" }]
                                    }
                                    """.trimIndent(),
                                versionUrl to
                                    """
                                    {
                                      "id": "1.21.6",
                                      "assetIndex": {
                                        "id": "26",
                                        "url": "$assetIndexUrl"
                                      },
                                      "downloads": {
                                        "client": { "url": "$clientJarUrl" }
                                      }
                                    }
                                    """.trimIndent(),
                                assetIndexUrl to """{"objects":{}}""",
                                loaderVersionsUrl to """[{ "loader": { "version": "0.17.2", "stable": true } }]""",
                                loaderProfileUrl to
                                    """
                                    {
                                      "id": "fabric-loader-0.17.2-1.21.6",
                                      "libraries": [
                                        {
                                          "name": "net.fabricmc:fabric-loader:0.17.2",
                                          "url": "https://maven.fabricmc.net/"
                                        }
                                      ]
                                    }
                                    """.trimIndent(),
                            ),
                            binaryResponses =
                                mapOf(
                                    clientJarUrl to "client-jar".encodeToByteArray(),
                                    fabricLoaderJarUrl to "fabric-loader-jar".encodeToByteArray(),
                                ),
                        ),
                )

            val result = service.prepare(CachePrepareRequest("latest-release", Loader.FABRIC))

            assertEquals("1.21.6", result.minecraftVersion)
            assertEquals("0.17.2", result.loaderVersion)
            assertEquals(
                loaderVersionsUrl,
                result.artifacts.single { it.kind == CachePreparedArtifactKind.FABRIC_LOADER_VERSIONS }.source,
            )
            assertEquals(
                loaderProfileUrl,
                result.artifacts.single { it.kind == CachePreparedArtifactKind.FABRIC_LOADER_PROFILE }.source,
            )
            assertEquals("cache/prepared/1.21.6-fabric-0.17.2.json", result.manifest)
            assertFalse(Files.exists(workspace.resolve("cache/loaders/fabric/latest-release")))
        }

    @Test
    fun `fabric cache preparation resolves fabric api mod artifact from maven metadata`() =
        runBlocking {
            val workspace = Files.createTempDirectory("craftless-cache-fabric-api")
            val versionUrl = "https://metadata.test/1.21.6.json"
            val clientJarUrl = "https://metadata.test/client.jar"
            val assetIndexUrl = "https://metadata.test/assets/1.21.6.json"
            val loaderVersionsUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6"
            val loaderProfileUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6/0.17.2/profile/json"
            val fabricLoaderJarUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.17.2/fabric-loader-0.17.2.jar"
            val fabricApiMetadataUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml"
            val fabricApiJarUrl =
                "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.129.0+1.21.6/fabric-api-0.129.0+1.21.6.jar"
            val service =
                CachePreparationService(
                    workspaceRoot = workspace,
                    metadataFetcher =
                        StaticCacheMetadataFetcher(
                            mapOf(
                                MINECRAFT_VERSION_INDEX_URL to
                                    """
                                    { "versions": [{ "id": "1.21.6", "url": "$versionUrl" }] }
                                    """.trimIndent(),
                                versionUrl to
                                    """
                                    {
                                      "id": "1.21.6",
                                      "assetIndex": {
                                        "id": "26",
                                        "url": "$assetIndexUrl"
                                      },
                                      "downloads": {
                                        "client": { "url": "$clientJarUrl" }
                                      }
                                    }
                                    """.trimIndent(),
                                assetIndexUrl to """{"objects":{}}""",
                                loaderVersionsUrl to """[{ "loader": { "version": "0.17.2", "stable": true } }]""",
                                loaderProfileUrl to
                                    """
                                    {
                                      "id": "fabric-loader-0.17.2-1.21.6",
                                      "libraries": [
                                        {
                                          "name": "net.fabricmc:fabric-loader:0.17.2",
                                          "url": "https://maven.fabricmc.net/"
                                        }
                                      ]
                                    }
                                    """.trimIndent(),
                                fabricApiMetadataUrl to
                                    """
                                    <metadata>
                                      <groupId>net.fabricmc.fabric-api</groupId>
                                      <artifactId>fabric-api</artifactId>
                                      <versioning>
                                        <versions>
                                          <version>0.127.0+1.21.5</version>
                                          <version>0.128.2+1.21.6</version>
                                          <version>0.129.0+1.21.6</version>
                                        </versions>
                                      </versioning>
                                    </metadata>
                                    """.trimIndent(),
                            ),
                            binaryResponses =
                                mapOf(
                                    clientJarUrl to "client-jar".encodeToByteArray(),
                                    fabricLoaderJarUrl to "fabric-loader-jar".encodeToByteArray(),
                                    fabricApiJarUrl to "fabric-api-jar".encodeToByteArray(),
                                ),
                        ),
                )

            val result = service.prepare(CachePrepareRequest("1.21.6", Loader.FABRIC))

            val fabricApi = result.artifacts.single { it.kind == CachePreparedArtifactKind.FABRIC_MOD }
            assertEquals(fabricApiJarUrl, fabricApi.source)
            assertTrue(result.launch.mods.contains(fabricApi.handle))
            assertEquals("fabric-api-jar", Files.readString(workspace.resolve(fabricApi.handle)))
        }

    @Test
    fun `fabric cache preparation requires matching fabric api artifact`() =
        runBlocking {
            val workspace = Files.createTempDirectory("craftless-cache-missing-fabric-api")
            val versionUrl = "https://metadata.test/1.21.7.json"
            val clientJarUrl = "https://metadata.test/client.jar"
            val assetIndexUrl = "https://metadata.test/assets/1.21.7.json"
            val loaderVersionsUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.7"
            val loaderProfileUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.7/0.17.2/profile/json"
            val fabricLoaderJarUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.17.2/fabric-loader-0.17.2.jar"
            val fabricApiMetadataUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml"
            val service =
                CachePreparationService(
                    workspaceRoot = workspace,
                    metadataFetcher =
                        StaticCacheMetadataFetcher(
                            mapOf(
                                MINECRAFT_VERSION_INDEX_URL to
                                    """
                                    { "versions": [{ "id": "1.21.7", "url": "$versionUrl" }] }
                                    """.trimIndent(),
                                versionUrl to
                                    """
                                    {
                                      "id": "1.21.7",
                                      "assetIndex": {
                                        "id": "26",
                                        "url": "$assetIndexUrl"
                                      },
                                      "downloads": {
                                        "client": { "url": "$clientJarUrl" }
                                      }
                                    }
                                    """.trimIndent(),
                                assetIndexUrl to """{"objects":{}}""",
                                loaderVersionsUrl to """[{ "loader": { "version": "0.17.2", "stable": true } }]""",
                                loaderProfileUrl to
                                    """
                                    {
                                      "id": "fabric-loader-0.17.2-1.21.7",
                                      "libraries": [
                                        {
                                          "name": "net.fabricmc:fabric-loader:0.17.2",
                                          "url": "https://maven.fabricmc.net/"
                                        }
                                      ]
                                    }
                                    """.trimIndent(),
                                fabricApiMetadataUrl to
                                    """
                                    <metadata>
                                      <groupId>net.fabricmc.fabric-api</groupId>
                                      <artifactId>fabric-api</artifactId>
                                      <versioning>
                                        <versions>
                                          <version>0.129.0+1.21.6</version>
                                        </versions>
                                      </versioning>
                                    </metadata>
                                    """.trimIndent(),
                            ),
                            binaryResponses =
                                mapOf(
                                    clientJarUrl to "client-jar".encodeToByteArray(),
                                    fabricLoaderJarUrl to "fabric-loader-jar".encodeToByteArray(),
                                ),
                        ),
                )

            val error =
                assertFailsWith<IllegalStateException> {
                    service.prepare(CachePrepareRequest("1.21.7", Loader.FABRIC))
                }

            assertTrue(error.message?.contains("Fabric API") == true)
            assertTrue(error.message?.contains("1.21.7") == true)
        }

    @Test
    fun `fabric cache preparation lets fabric libraries replace duplicate minecraft libraries`() =
        runBlocking {
            val workspace = Files.createTempDirectory("craftless-cache-fabric-library-dedupe")
            val versionUrl = "https://metadata.test/1.21.6.json"
            val clientJarUrl = "https://metadata.test/client.jar"
            val minecraftAsmUrl = "https://libraries.minecraft.net/org/ow2/asm/asm/9.6/asm-9.6.jar"
            val minecraftAuthlibUrl = "https://libraries.minecraft.net/com/mojang/authlib/6.0.54/authlib-6.0.54.jar"
            val assetIndexUrl = "https://metadata.test/assets/1.21.6.json"
            val loaderVersionsUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6"
            val loaderProfileUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6/0.17.2/profile/json"
            val fabricAsmUrl = "https://maven.fabricmc.net/org/ow2/asm/asm/9.10.1/asm-9.10.1.jar"
            val fabricLoaderUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.17.2/fabric-loader-0.17.2.jar"
            val service =
                CachePreparationService(
                    workspaceRoot = workspace,
                    metadataFetcher =
                        StaticCacheMetadataFetcher(
                            mapOf(
                                MINECRAFT_VERSION_INDEX_URL to
                                    """
                                    { "versions": [{ "id": "1.21.6", "url": "$versionUrl" }] }
                                    """.trimIndent(),
                                versionUrl to
                                    """
                                    {
                                      "id": "1.21.6",
                                      "assetIndex": {
                                        "id": "26",
                                        "url": "$assetIndexUrl"
                                      },
                                      "libraries": [
                                        {
                                          "name": "org.ow2.asm:asm:9.6",
                                          "downloads": { "artifact": { "url": "$minecraftAsmUrl" } }
                                        },
                                        {
                                          "name": "com.mojang:authlib:6.0.54",
                                          "downloads": { "artifact": { "url": "$minecraftAuthlibUrl" } }
                                        }
                                      ],
                                      "downloads": {
                                        "client": { "url": "$clientJarUrl" }
                                      }
                                    }
                                    """.trimIndent(),
                                assetIndexUrl to """{"objects":{}}""",
                                loaderVersionsUrl to """[{ "loader": { "version": "0.17.2", "stable": true } }]""",
                                loaderProfileUrl to
                                    """
                                    {
                                      "id": "fabric-loader-0.17.2-1.21.6",
                                      "libraries": [
                                        {
                                          "name": "org.ow2.asm:asm:9.10.1",
                                          "url": "https://maven.fabricmc.net/"
                                        },
                                        {
                                          "name": "net.fabricmc:fabric-loader:0.17.2",
                                          "url": "https://maven.fabricmc.net/"
                                        }
                                      ]
                                    }
                                    """.trimIndent(),
                            ),
                            binaryResponses =
                                mapOf(
                                    clientJarUrl to "client-jar".encodeToByteArray(),
                                    minecraftAsmUrl to "minecraft-asm".encodeToByteArray(),
                                    minecraftAuthlibUrl to "minecraft-authlib".encodeToByteArray(),
                                    fabricAsmUrl to "fabric-asm".encodeToByteArray(),
                                    fabricLoaderUrl to "fabric-loader".encodeToByteArray(),
                                ),
                        ),
                )

            val result = service.prepare(CachePrepareRequest("1.21.6", Loader.FABRIC))

            assertTrue(result.launch.classpath.none { it.contains(minecraftAsmUrl.sha256HexForTest()) })
            assertEquals(2, result.launch.classpath.count { it.startsWith("cache/libraries/fabric/") })
            assertEquals(1, result.launch.classpath.count { it.startsWith("cache/libraries/minecraft/") })
            assertEquals(
                "minecraft-authlib",
                Files.readString(
                    workspace.resolve(
                        result.artifacts
                            .single {
                                it.kind == CachePreparedArtifactKind.MINECRAFT_LIBRARY &&
                                    it.source == minecraftAuthlibUrl
                            }.handle,
                    ),
                ),
            )
        }

    @Test
    fun `cache preparation extracts rule selected native artifact libraries outside classpath`() =
        runBlocking {
            val workspace = Files.createTempDirectory("craftless-cache-rule-natives")
            val versionUrl = "https://metadata.test/1.21.6.json"
            val clientJarUrl = "https://metadata.test/client.jar"
            val lwjglUrl = "https://libraries.minecraft.net/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar"
            val nativeArtifact = currentTestNativeArtifact()
            val wrongNativeArtifact = nativeArtifact.wrongPlatform()
            val nativeUrl = "https://libraries.minecraft.net/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-${nativeArtifact.classifier}.jar"
            val wrongNativeUrl =
                "https://libraries.minecraft.net/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-${wrongNativeArtifact.classifier}.jar"
            val assetIndexUrl = "https://metadata.test/assets/1.21.6.json"
            val service =
                CachePreparationService(
                    workspaceRoot = workspace,
                    metadataFetcher =
                        StaticCacheMetadataFetcher(
                            mapOf(
                                MINECRAFT_VERSION_INDEX_URL to
                                    """
                                    { "versions": [{ "id": "1.21.6", "url": "$versionUrl" }] }
                                    """.trimIndent(),
                                versionUrl to
                                    """
                                    {
                                      "id": "1.21.6",
                                      "assetIndex": {
                                        "id": "26",
                                        "url": "$assetIndexUrl"
                                      },
                                      "libraries": [
                                        {
                                          "name": "org.lwjgl:lwjgl:3.3.3",
                                          "downloads": { "artifact": { "url": "$lwjglUrl" } }
                                        },
                                        {
                                          "name": "org.lwjgl:lwjgl:3.3.3:${nativeArtifact.classifier}",
                                          "rules": [{ "action": "allow", "os": { "name": "${nativeArtifact.osName}" } }],
                                          "downloads": { "artifact": { "url": "$nativeUrl" } }
                                        },
                                        {
                                          "name": "org.lwjgl:lwjgl:3.3.3:${wrongNativeArtifact.classifier}",
                                          "rules": [{ "action": "allow", "os": { "name": "${wrongNativeArtifact.osName}" } }],
                                          "downloads": { "artifact": { "url": "$wrongNativeUrl" } }
                                        }
                                      ],
                                      "downloads": {
                                        "client": { "url": "$clientJarUrl" }
                                      }
                                    }
                                    """.trimIndent(),
                                assetIndexUrl to """{"objects":{}}""",
                            ),
                            binaryResponses =
                                mapOf(
                                    clientJarUrl to "client-jar".encodeToByteArray(),
                                    lwjglUrl to "lwjgl-jar".encodeToByteArray(),
                                    nativeUrl to nativeZipBytes(),
                                    wrongNativeUrl to nativeZipBytes(),
                                ),
                        ),
                )

            val result = service.prepare(CachePrepareRequest("1.21.6", Loader.VANILLA))

            val nativeLibrary = result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_NATIVE_LIBRARY }
            val nativeDirectory = result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_NATIVE_DIRECTORY }
            assertEquals(nativeUrl, nativeLibrary.source)
            assertTrue(result.launch.classpath.none { it == nativeLibrary.handle })
            assertEquals(listOf(nativeDirectory.handle), result.launch.nativePath)
            assertEquals("native-bytes", Files.readString(workspace.resolve(nativeDirectory.handle).resolve("libcraftless-test.dylib")))
        }

    @Test
    fun `cache preparation resolves and stores minecraft version metadata`() =
        runBlocking {
            val workspace = Files.createTempDirectory("craftless-cache-resolution")
            val versionUrl = "https://metadata.test/1.21.6.json"
            val clientJarUrl = "https://metadata.test/client.jar"
            val minecraftLibraryUrl = "https://libraries.minecraft.net/com/mojang/authlib/6.0.54/authlib-6.0.54.jar"
            val nativeLibraryUrl = "https://libraries.minecraft.net/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives.jar"
            val assetIndexUrl = "https://metadata.test/assets/1.21.6.json"
            val assetHash = "a4b45e57b3934836f20ccf8529c18bcd1e120129"
            val assetObjectUrl = "https://resources.download.minecraft.net/a4/$assetHash"
            val loggingConfigUrl = "https://piston-data.test/client-1.21.2.xml"
            val javaRuntimeManifestUrl = "https://metadata.test/runtime/java-runtime-gamma/manifest.json"
            val javaExecutableUrl = "https://metadata.test/runtime/java-runtime-gamma/bin/java"
            val javaRuntimeFileUrl = "https://metadata.test/runtime/java-runtime-gamma/lib/runtime.txt"
            val loaderVersionsUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6"
            val loaderProfileUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6/0.17.2/profile/json"
            val fabricLoaderJarUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.17.2/fabric-loader-0.17.2.jar"
            val service =
                CachePreparationService(
                    workspaceRoot = workspace,
                    metadataFetcher =
                        StaticCacheMetadataFetcher(
                            mapOf(
                                MINECRAFT_VERSION_INDEX_URL to
                                    """
                                    {
                                      "versions": [
                                        { "id": "1.21.6", "url": "$versionUrl" }
                                      ]
                                    }
                                    """.trimIndent(),
                                versionUrl to
                                    """
                                    {
                                      "id": "1.21.6",
                                      "assetIndex": {
                                        "id": "26",
                                        "url": "$assetIndexUrl"
                                      },
                                      "javaVersion": {
                                        "component": "java-runtime-gamma",
                                        "majorVersion": 21
                                      },
                                      "mainClass": "test.minecraft.Main",
                                      "arguments": {
                                        "jvm": [
                                          "-Djava.library.path=${'$'}{natives_directory}",
                                          {
                                            "rules": [{ "action": "allow" }],
                                            "value": ["-Dcraftless.test=true"]
                                          },
                                          "-cp",
                                          "${'$'}{classpath}"
                                        ],
                                        "game": [
                                          "--version",
                                          "${'$'}{version_name}",
                                          "--assetsDir",
                                          "${'$'}{assets_root}",
                                          "--assetIndex",
                                          "${'$'}{assets_index_name}",
                                          "--gameDir",
                                          "${'$'}{game_directory}",
                                          {
                                            "rules": [
                                              {
                                                "action": "allow",
                                                "features": {
                                                  "is_demo_user": true
                                                }
                                              }
                                            ],
                                            "value": "--demo"
                                          }
                                        ]
                                      },
                                      "logging": {
                                        "client": {
                                          "argument": "-Dlog4j.configurationFile=${'$'}{path}",
                                          "file": {
                                            "id": "client-1.21.2.xml",
                                            "url": "$loggingConfigUrl"
                                          },
                                          "type": "log4j2-xml"
                                        }
                                      },
                                      "libraries": [
                                        {
                                          "name": "com.mojang:authlib:6.0.54",
                                          "downloads": {
                                            "artifact": {
                                              "url": "$minecraftLibraryUrl"
                                            }
                                          }
                                        },
                                        {
                                          "name": "org.lwjgl:lwjgl:3.3.3",
                                          "natives": {
                                            "linux": "natives-linux",
                                            "osx": "natives-osx",
                                            "windows": "natives-windows"
                                          },
                                          "downloads": {
                                            "classifiers": {
                                              "natives-linux": {
                                                "url": "$nativeLibraryUrl"
                                              },
                                              "natives-osx": {
                                                "url": "$nativeLibraryUrl"
                                              },
                                              "natives-windows": {
                                                "url": "$nativeLibraryUrl"
                                              }
                                            }
                                          }
                                        }
                                      ],
                                      "downloads": {
                                        "client": { "url": "$clientJarUrl" }
                                      }
                                    }
                                    """.trimIndent(),
                                MINECRAFT_JAVA_RUNTIME_INDEX_URL to javaRuntimeIndexJson(javaRuntimeManifestUrl),
                                javaRuntimeManifestUrl to
                                    """
                                    {
                                      "files": {
                                        "bin/java": {
                                          "type": "file",
                                          "downloads": {
                                            "raw": { "url": "$javaExecutableUrl" }
                                          }
                                        },
                                        "lib/runtime.txt": {
                                          "type": "file",
                                          "downloads": {
                                            "raw": { "url": "$javaRuntimeFileUrl" }
                                          }
                                        },
                                        "legal": {
                                          "type": "directory"
                                        }
                                      }
                                    }
                                    """.trimIndent(),
                                assetIndexUrl to
                                    """
                                    {
                                      "objects": {
                                        "minecraft/sounds/random/test.ogg": {
                                          "hash": "$assetHash",
                                          "size": 10
                                        }
                                      }
                                    }
                                    """.trimIndent(),
                                loaderVersionsUrl to
                                    """
                                    [
                                      { "loader": { "version": "0.17.1", "stable": false } },
                                      { "loader": { "version": "0.17.2", "stable": true } }
                                    ]
                                    """.trimIndent(),
                                loaderProfileUrl to
                                    """
                                    {
                                      "id": "fabric-loader-0.17.2-1.21.6",
                                      "mainClass": "test.fabric.Main",
                                      "arguments": {
                                        "game": ["--fabric-test", "enabled"]
                                      },
                                      "libraries": [
                                        {
                                          "name": "net.fabricmc:fabric-loader:0.17.2",
                                          "url": "https://maven.fabricmc.net/"
                                        }
                                      ]
                                    }
                                    """.trimIndent(),
                            ),
                            binaryResponses =
                                mapOf(
                                    clientJarUrl to "client-jar".encodeToByteArray(),
                                    minecraftLibraryUrl to "minecraft-library".encodeToByteArray(),
                                    nativeLibraryUrl to nativeZipBytes(),
                                    loggingConfigUrl to "<Configuration/>".encodeToByteArray(),
                                    javaExecutableUrl to fakeJavaBytes("21.0.11"),
                                    javaRuntimeFileUrl to "runtime-file".encodeToByteArray(),
                                    assetObjectUrl to "asset-bytes".encodeToByteArray(),
                                    fabricLoaderJarUrl to "fabric-loader-jar".encodeToByteArray(),
                                ),
                        ),
                )

            val result = service.prepare(CachePrepareRequest("1.21.6", Loader.FABRIC))

            assertEquals(
                listOf(
                    CachePreparedArtifactKind.MINECRAFT_VERSION_INDEX,
                    CachePreparedArtifactKind.MINECRAFT_VERSION_MANIFEST,
                    CachePreparedArtifactKind.MINECRAFT_CLIENT_JAR,
                    CachePreparedArtifactKind.MINECRAFT_ASSET_INDEX,
                    CachePreparedArtifactKind.JAVA_RUNTIME_INDEX,
                    CachePreparedArtifactKind.JAVA_RUNTIME_MANIFEST,
                    CachePreparedArtifactKind.JAVA_RUNTIME_EXECUTABLE,
                    CachePreparedArtifactKind.JAVA_RUNTIME_FILE,
                    CachePreparedArtifactKind.LAUNCH_ARGUMENTS,
                    CachePreparedArtifactKind.MINECRAFT_LOG_CONFIG,
                    CachePreparedArtifactKind.MINECRAFT_LIBRARY,
                    CachePreparedArtifactKind.MINECRAFT_NATIVE_LIBRARY,
                    CachePreparedArtifactKind.MINECRAFT_NATIVE_DIRECTORY,
                    CachePreparedArtifactKind.FABRIC_LOADER_VERSIONS,
                    CachePreparedArtifactKind.FABRIC_LOADER_PROFILE,
                    CachePreparedArtifactKind.MINECRAFT_ASSET_OBJECT,
                    CachePreparedArtifactKind.FABRIC_LIBRARY,
                    CachePreparedArtifactKind.FABRIC_MOD,
                ),
                result.artifacts.map { it.kind },
            )
            assertEquals("0.17.2", result.loaderVersion)
            assertEquals(versionUrl, result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_VERSION_MANIFEST }.source)
            assertEquals(clientJarUrl, result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_CLIENT_JAR }.source)
            assertEquals(assetIndexUrl, result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_ASSET_INDEX }.source)
            assertEquals(
                "cache/assets/indexes/26.json",
                result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_ASSET_INDEX }.handle,
            )
            assertEquals(loaderProfileUrl, result.artifacts.single { it.kind == CachePreparedArtifactKind.FABRIC_LOADER_PROFILE }.source)
            val javaRuntimeIndex = result.artifacts.single { it.kind == CachePreparedArtifactKind.JAVA_RUNTIME_INDEX }
            assertEquals(MINECRAFT_JAVA_RUNTIME_INDEX_URL, javaRuntimeIndex.source)
            assertEquals("cache/runtimes/index.json", javaRuntimeIndex.handle)
            val javaRuntimeManifest = result.artifacts.single { it.kind == CachePreparedArtifactKind.JAVA_RUNTIME_MANIFEST }
            assertEquals(javaRuntimeManifestUrl, javaRuntimeManifest.source)
            assertTrue(javaRuntimeManifest.handle.endsWith("/java-runtime-gamma/manifest.json"))
            val javaExecutable = result.artifacts.single { it.kind == CachePreparedArtifactKind.JAVA_RUNTIME_EXECUTABLE }
            assertEquals(javaExecutableUrl, javaExecutable.source)
            assertTrue(javaExecutable.handle.endsWith("/java-runtime-gamma/image/bin/java"))
            val javaRuntimeFile = result.artifacts.single { it.kind == CachePreparedArtifactKind.JAVA_RUNTIME_FILE }
            assertEquals(javaRuntimeFileUrl, javaRuntimeFile.source)
            assertTrue(javaRuntimeFile.handle.endsWith("/java-runtime-gamma/image/lib/runtime.txt"))
            val launchArguments = result.artifacts.single { it.kind == CachePreparedArtifactKind.LAUNCH_ARGUMENTS }
            assertEquals(null, launchArguments.source)
            assertEquals("cache/prepared/1.21.6-fabric-0.17.2.launch.json", launchArguments.handle)
            val loggingConfig = result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_LOG_CONFIG }
            assertEquals(loggingConfigUrl, loggingConfig.source)
            assertEquals("cache/minecraft/versions/1.21.6/logging/client-1.21.2.xml", loggingConfig.handle)
            assertEquals("<Configuration/>", Files.readString(workspace.resolve(loggingConfig.handle)))
            val assetObject = result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_ASSET_OBJECT }
            assertEquals(assetObjectUrl, assetObject.source)
            assertEquals(
                "cache/assets/objects/a4/$assetHash",
                assetObject.handle,
            )
            assertEquals("CACHED", assetObject.status.name)
            val minecraftLibrary = result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_LIBRARY }
            assertEquals(minecraftLibraryUrl, minecraftLibrary.source)
            assertTrue(minecraftLibrary.handle.startsWith("cache/libraries/minecraft/"))
            assertEquals("CACHED", minecraftLibrary.status.name)
            val nativeLibrary = result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_NATIVE_LIBRARY }
            assertEquals(nativeLibraryUrl, nativeLibrary.source)
            assertTrue(nativeLibrary.handle.startsWith("cache/libraries/native/"))
            assertEquals("CACHED", nativeLibrary.status.name)
            val nativeDirectory = result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_NATIVE_DIRECTORY }
            assertEquals(null, nativeDirectory.source)
            assertTrue(nativeDirectory.handle.startsWith("cache/natives/"))
            assertEquals("EXTRACTED", nativeDirectory.status.name)
            val fabricLibrary = result.artifacts.single { it.kind == CachePreparedArtifactKind.FABRIC_LIBRARY }
            assertEquals(fabricLoaderJarUrl, fabricLibrary.source)
            assertTrue(fabricLibrary.handle.startsWith("cache/libraries/fabric/"))
            val fabricApi = result.artifacts.single { it.kind == CachePreparedArtifactKind.FABRIC_MOD }
            assertEquals(DEFAULT_TEST_FABRIC_API_JAR_URL, fabricApi.source)
            assertTrue(result.launch.mods.contains(fabricApi.handle))
            assertEquals(
                listOf(
                    minecraftLibrary.handle,
                    fabricLibrary.handle,
                    "cache/minecraft/versions/1.21.6/client.jar",
                ),
                result.launch.classpath,
            )
            assertEquals(listOf(nativeDirectory.handle), result.launch.nativePath)
            assertEquals(javaExecutable.handle, result.launch.javaExecutable)
            assertEquals(launchArguments.handle, result.launch.arguments)
            assertEquals(21, result.javaSelection?.requirement?.majorVersion)
            assertEquals("java-runtime-gamma", result.javaSelection?.requirement?.component)
            assertEquals(JavaRuntimeSelectionStatus.SELECTED, result.javaSelection?.status)
            assertEquals(JavaRuntimeProviderKind.MANAGED, result.javaSelection?.selected?.provider)
            assertEquals(21, result.javaSelection?.selected?.majorVersion)
            assertEquals(javaExecutable.handle, result.javaSelection?.selected?.executable)
            assertTrue(Files.readString(workspace.resolve("cache/minecraft/version_manifest_v2.json")).contains("1.21.6"))
            assertTrue(Files.readString(workspace.resolve("cache/minecraft/versions/1.21.6/version.json")).contains("client.jar"))
            assertEquals("client-jar", Files.readString(workspace.resolve("cache/minecraft/versions/1.21.6/client.jar")))
            assertTrue(Files.readString(workspace.resolve("cache/runtimes/index.json")).contains("java-runtime-gamma"))
            assertTrue(Files.readString(workspace.resolve(javaRuntimeManifest.handle)).contains("bin/java"))
            assertTrue(Files.readString(workspace.resolve(javaExecutable.handle)).contains("openjdk version"))
            assertEquals("runtime-file", Files.readString(workspace.resolve(javaRuntimeFile.handle)))
            assertTrue(Files.isExecutable(workspace.resolve(javaExecutable.handle)))
            val launchArgumentsJson = Files.readString(workspace.resolve(launchArguments.handle))
            val launchClasspath =
                listOf(
                    minecraftLibrary.handle,
                    fabricLibrary.handle,
                    "cache/minecraft/versions/1.21.6/client.jar",
                ).joinToString(File.pathSeparator)
            assertTrue(launchArgumentsJson.contains("\"mainClass\":\"test.fabric.Main\""))
            assertTrue(launchArgumentsJson.contains("\"-Djava.library.path=${nativeDirectory.handle}\""))
            assertTrue(
                launchArgumentsJson.contains(
                    "-Dlog4j.configurationFile=cache/minecraft/versions/1.21.6/logging/client-1.21.2.xml",
                ),
            )
            assertTrue(launchArgumentsJson.contains("\"$launchClasspath\""))
            assertTrue(launchArgumentsJson.contains("\"--fabric-test\""))
            assertTrue(launchArgumentsJson.contains("\"--assetIndex\""))
            assertTrue(launchArgumentsJson.contains("\"26\""))
            assertFalse(launchArgumentsJson.contains("{{assets_index_name}}"))
            assertTrue(launchArgumentsJson.contains("\"{{gameRoot}}\""))
            assertFalse(launchArgumentsJson.contains("\"--demo\""))
            assertEquals("minecraft-library", Files.readString(workspace.resolve(minecraftLibrary.handle)))
            assertTrue(Files.isRegularFile(workspace.resolve(nativeLibrary.handle)))
            assertEquals("native-bytes", Files.readString(workspace.resolve(nativeDirectory.handle).resolve("libcraftless-test.dylib")))
            assertTrue(!Files.exists(workspace.resolve(nativeDirectory.handle).resolve("META-INF")))
            assertTrue(Files.readString(workspace.resolve("cache/assets/indexes/26.json")).contains("test.ogg"))
            assertEquals("asset-bytes", Files.readString(workspace.resolve(assetObject.handle)))
            assertTrue(Files.readString(workspace.resolve("cache/loaders/fabric/1.21.6/versions.json")).contains("0.17.2"))
            assertTrue(Files.readString(workspace.resolve("cache/loaders/fabric/1.21.6/0.17.2/profile.json")).contains("fabric-loader"))
            assertEquals(
                "fabric-loader-jar",
                Files.readString(workspace.resolve(fabricLibrary.handle)),
            )
            assertTrue(Files.readString(workspace.resolve(result.manifest)).contains("MINECRAFT_VERSION_MANIFEST"))
            assertTrue(Files.readString(workspace.resolve(result.manifest)).contains("JAVA_RUNTIME_EXECUTABLE"))
            assertTrue(Files.readString(workspace.resolve(result.manifest)).contains("LAUNCH_ARGUMENTS"))
            assertTrue(Files.readString(workspace.resolve(result.manifest)).contains("MINECRAFT_LIBRARY"))
            assertTrue(Files.readString(workspace.resolve(result.manifest)).contains("MINECRAFT_NATIVE_DIRECTORY"))
            assertTrue(Files.readString(workspace.resolve(result.manifest)).contains("FABRIC_LIBRARY"))
            assertTrue(Files.readString(workspace.resolve(result.manifest)).contains("\"javaSelection\""))
        }

    @Test
    fun `cache preparation rejects invalid minecraft asset hashes before writing cache handles`() =
        runBlocking {
            val workspace = Files.createTempDirectory("craftless-cache-invalid-asset-hash")
            val versionUrl = "https://metadata.test/1.21.6.json"
            val clientJarUrl = "https://metadata.test/client.jar"
            val assetIndexUrl = "https://metadata.test/assets/1.21.6.json"
            val service =
                CachePreparationService(
                    workspaceRoot = workspace,
                    metadataFetcher =
                        StaticCacheMetadataFetcher(
                            mapOf(
                                MINECRAFT_VERSION_INDEX_URL to
                                    """
                                    { "versions": [{ "id": "1.21.6", "url": "$versionUrl" }] }
                                    """.trimIndent(),
                                versionUrl to
                                    """
                                    {
                                      "id": "1.21.6",
                                      "assetIndex": {
                                        "id": "1.21.6",
                                        "url": "$assetIndexUrl"
                                      },
                                      "downloads": {
                                        "client": { "url": "$clientJarUrl" }
                                      }
                                    }
                                    """.trimIndent(),
                                assetIndexUrl to
                                    """
                                    {
                                      "objects": {
                                        "minecraft/sounds/random/test.ogg": {
                                          "hash": "../not-a-sha1",
                                          "size": 10
                                        }
                                      }
                                    }
                                    """.trimIndent(),
                            ),
                            binaryResponses = mapOf(clientJarUrl to "client-jar".encodeToByteArray()),
                        ),
                )

            val failure =
                assertFailsWith<IllegalArgumentException> {
                    service.prepare(CachePrepareRequest("1.21.6", Loader.VANILLA))
                }

            assertEquals("Minecraft asset hash must be a SHA-1 hex string", failure.message)
        }

    @Test
    fun `cache preparation rejects invalid minecraft logging config ids before writing cache handles`() =
        runBlocking {
            val workspace = Files.createTempDirectory("craftless-cache-invalid-logging-id")
            val versionUrl = "https://metadata.test/1.21.6.json"
            val clientJarUrl = "https://metadata.test/client.jar"
            val assetIndexUrl = "https://metadata.test/assets/1.21.6.json"
            val loggingConfigUrl = "https://piston-data.test/client.xml"
            val service =
                CachePreparationService(
                    workspaceRoot = workspace,
                    metadataFetcher =
                        StaticCacheMetadataFetcher(
                            mapOf(
                                MINECRAFT_VERSION_INDEX_URL to
                                    """
                                    { "versions": [{ "id": "1.21.6", "url": "$versionUrl" }] }
                                    """.trimIndent(),
                                versionUrl to
                                    """
                                    {
                                      "id": "1.21.6",
                                      "assetIndex": {
                                        "id": "1.21.6",
                                        "url": "$assetIndexUrl"
                                      },
                                      "logging": {
                                        "client": {
                                          "argument": "-Dlog4j.configurationFile=${'$'}{path}",
                                          "file": {
                                            "id": "../client.xml",
                                            "url": "$loggingConfigUrl"
                                          },
                                          "type": "log4j2-xml"
                                        }
                                      },
                                      "downloads": {
                                        "client": { "url": "$clientJarUrl" }
                                      }
                                    }
                                    """.trimIndent(),
                                assetIndexUrl to """{"objects":{}}""",
                            ),
                            binaryResponses =
                                mapOf(
                                    clientJarUrl to "client-jar".encodeToByteArray(),
                                    loggingConfigUrl to "<Configuration/>".encodeToByteArray(),
                                ),
                        ),
                )

            val failure =
                assertFailsWith<IllegalArgumentException> {
                    service.prepare(CachePrepareRequest("1.21.6", Loader.VANILLA))
                }

            assertEquals("Minecraft logging config id must be a file-safe segment", failure.message)
        }

    @Test
    fun `cache preparation rejects invalid asset index ids before writing cache handles`() =
        runBlocking {
            val workspace = Files.createTempDirectory("craftless-cache-invalid-asset-index-id")
            val versionUrl = "https://metadata.test/1.21.6.json"
            val clientJarUrl = "https://metadata.test/client.jar"
            val assetIndexUrl = "https://metadata.test/assets/26.json"
            val service =
                CachePreparationService(
                    workspaceRoot = workspace,
                    metadataFetcher =
                        StaticCacheMetadataFetcher(
                            mapOf(
                                MINECRAFT_VERSION_INDEX_URL to
                                    """
                                    { "versions": [{ "id": "1.21.6", "url": "$versionUrl" }] }
                                    """.trimIndent(),
                                versionUrl to
                                    """
                                    {
                                      "id": "1.21.6",
                                      "assetIndex": {
                                        "id": "../26",
                                        "url": "$assetIndexUrl"
                                      },
                                      "downloads": {
                                        "client": { "url": "$clientJarUrl" }
                                      }
                                    }
                                    """.trimIndent(),
                                assetIndexUrl to """{"objects":{}}""",
                            ),
                            binaryResponses = mapOf(clientJarUrl to "client-jar".encodeToByteArray()),
                        ),
                )

            val failure =
                assertFailsWith<IllegalArgumentException> {
                    service.prepare(CachePrepareRequest("1.21.6", Loader.VANILLA))
                }

            assertEquals("Minecraft asset index id must be a file-safe segment", failure.message)
        }

    @Test
    fun `cache preparation uses pinned compatible fabric loader version`() =
        runBlocking {
            val workspace = Files.createTempDirectory("craftless-cache-loader-pin")
            val versionUrl = "https://metadata.test/1.21.6.json"
            val clientJarUrl = "https://metadata.test/client.jar"
            val assetIndexUrl = "https://metadata.test/assets/1.21.6.json"
            val loaderVersionsUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6"
            val pinnedProfileUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6/0.16.14/profile/json"
            val service =
                CachePreparationService(
                    workspaceRoot = workspace,
                    metadataFetcher =
                        StaticCacheMetadataFetcher(
                            mapOf(
                                MINECRAFT_VERSION_INDEX_URL to
                                    """
                                    { "versions": [{ "id": "1.21.6", "url": "$versionUrl" }] }
                                    """.trimIndent(),
                                versionUrl to
                                    """
                                    {
                                      "id": "1.21.6",
                                      "assetIndex": {
                                        "id": "1.21.6",
                                        "url": "$assetIndexUrl"
                                      },
                                      "downloads": {
                                        "client": { "url": "$clientJarUrl" }
                                      }
                                    }
                                    """.trimIndent(),
                                assetIndexUrl to """{"objects":{}}""",
                                loaderVersionsUrl to
                                    """
                                    [
                                      { "loader": { "version": "0.17.2", "stable": true } },
                                      { "loader": { "version": "0.16.14", "stable": true } }
                                    ]
                                    """.trimIndent(),
                                pinnedProfileUrl to """{"id":"fabric-loader-0.16.14-1.21.6"}""",
                            ),
                            binaryResponses = mapOf(clientJarUrl to "client-jar".encodeToByteArray()),
                        ),
                )

            val result =
                service.prepare(
                    CachePrepareRequest(
                        minecraftVersion = "1.21.6",
                        loader = Loader.FABRIC,
                        loaderVersion = "0.16.14",
                    ),
                )

            assertEquals("0.16.14", result.loaderVersion)
            assertEquals("cache/loaders/fabric/1.21.6/0.16.14", result.loaderRoot)
            assertTrue(Files.readString(workspace.resolve(result.manifest)).contains("0.16.14"))
            assertTrue(Files.readString(workspace.resolve("cache/loaders/fabric/1.21.6/0.16.14/profile.json")).contains("0.16.14"))
        }

    @Test
    fun `cache preparation reuses existing binary artifacts and fetches missing files`() =
        runBlocking {
            val workspace = Files.createTempDirectory("craftless-cache-resume")
            val versionUrl = "https://metadata.test/1.21.6.json"
            val clientJarUrl = "https://metadata.test/client.jar"
            val assetIndexUrl = "https://metadata.test/assets/1.21.6.json"
            val fetcher =
                CountingCacheMetadataFetcher(
                    responses =
                        mapOf(
                            MINECRAFT_VERSION_INDEX_URL to
                                """
                                { "versions": [{ "id": "1.21.6", "url": "$versionUrl" }] }
                                """.trimIndent(),
                            versionUrl to
                                """
                                {
                                  "id": "1.21.6",
                                  "assetIndex": {
                                    "id": "1.21.6",
                                    "url": "$assetIndexUrl"
                                  },
                                  "mainClass": "test.minecraft.Main",
                                  "arguments": {
                                    "jvm": [],
                                    "game": ["--gameDir", "${'$'}{game_directory}"]
                                  },
                                  "downloads": {
                                    "client": { "url": "$clientJarUrl" }
                                  }
                                }
                                """.trimIndent(),
                            assetIndexUrl to """{"objects":{}}""",
                        ),
                    binaryResponses = mapOf(clientJarUrl to "downloaded-client".encodeToByteArray()),
                )
            val cachedClient = workspace.resolve("cache/minecraft/versions/1.21.6/client.jar")
            Files.createDirectories(cachedClient.parent)
            Files.writeString(cachedClient, "cached-client")
            val service = CachePreparationService(workspaceRoot = workspace, metadataFetcher = fetcher)

            service.prepare(CachePrepareRequest("1.21.6", Loader.VANILLA))

            assertEquals("cached-client", Files.readString(cachedClient))
            assertEquals(0, fetcher.binaryFetchCount(clientJarUrl))

            Files.delete(cachedClient)
            service.prepare(CachePrepareRequest("1.21.6", Loader.VANILLA))

            assertEquals("downloaded-client", Files.readString(cachedClient))
            assertEquals(1, fetcher.binaryFetchCount(clientJarUrl))
        }

    @Test
    fun `cache preparation refetches corrupt existing asset objects`() =
        runBlocking {
            val workspace = Files.createTempDirectory("craftless-cache-corrupt-asset-resume")
            val versionUrl = "https://metadata.test/1.21.6.json"
            val clientJarUrl = "https://metadata.test/client.jar"
            val assetIndexUrl = "https://metadata.test/assets/1.21.6.json"
            val assetHash = "e2b4694e41e508d3cba98550e509c7fc82aaca8a"
            val assetObjectUrl = "https://resources.download.minecraft.net/e2/$assetHash"
            val assetObject = workspace.resolve("cache/assets/objects/e2/$assetHash")
            val fetcher =
                CountingCacheMetadataFetcher(
                    responses =
                        mapOf(
                            MINECRAFT_VERSION_INDEX_URL to
                                """
                                { "versions": [{ "id": "1.21.6", "url": "$versionUrl" }] }
                                """.trimIndent(),
                            versionUrl to
                                """
                                {
                                  "id": "1.21.6",
                                  "assetIndex": {
                                    "id": "1.21.6",
                                    "url": "$assetIndexUrl"
                                  },
                                  "mainClass": "test.minecraft.Main",
                                  "arguments": {
                                    "jvm": [],
                                    "game": ["--gameDir", "${'$'}{game_directory}"]
                                  },
                                  "downloads": {
                                    "client": { "url": "$clientJarUrl" }
                                  }
                                }
                                """.trimIndent(),
                            assetIndexUrl to
                                """
                                {
                                  "objects": {
                                    "minecraft/sounds/random/test.ogg": {
                                      "hash": "$assetHash",
                                      "size": 16
                                    }
                                  }
                                }
                                """.trimIndent(),
                        ),
                    binaryResponses =
                        mapOf(
                            clientJarUrl to "downloaded-client".encodeToByteArray(),
                            assetObjectUrl to "downloaded-asset".encodeToByteArray(),
                        ),
                )
            Files.createDirectories(assetObject.parent)
            Files.writeString(assetObject, "corrupt-asset")
            val service = CachePreparationService(workspaceRoot = workspace, metadataFetcher = fetcher)

            service.prepare(CachePrepareRequest("1.21.6", Loader.VANILLA))

            assertEquals("downloaded-asset", Files.readString(assetObject))
            assertEquals(1, fetcher.binaryFetchCount(assetObjectUrl))

            service.prepare(CachePrepareRequest("1.21.6", Loader.VANILLA))

            assertEquals("downloaded-asset", Files.readString(assetObject))
            assertEquals(1, fetcher.binaryFetchCount(assetObjectUrl))
        }

    @Test
    fun `cache preparation fetches independent asset objects concurrently`() =
        runBlocking {
            val workspace = Files.createTempDirectory("craftless-cache-parallel-assets")
            val versionUrl = "https://metadata.test/1.21.6.json"
            val clientJarUrl = "https://metadata.test/client.jar"
            val assetIndexUrl = "https://metadata.test/assets/1.21.6.json"
            val assetBytes =
                listOf("asset-one", "asset-two", "asset-three", "asset-four")
                    .associateBy { bytes -> bytes.sha1HexForTest() }
            val assetUrls =
                assetBytes.keys.associateWith { hash ->
                    "https://resources.download.minecraft.net/${hash.take(2)}/$hash"
                }
            val fetcher =
                ConcurrentCountingCacheMetadataFetcher(
                    responses =
                        mapOf(
                            MINECRAFT_VERSION_INDEX_URL to
                                """{ "versions": [{ "id": "1.21.6", "url": "$versionUrl" }] }""",
                            versionUrl to
                                """
                                {
                                  "id": "1.21.6",
                                  "assetIndex": {
                                    "id": "26",
                                    "url": "$assetIndexUrl"
                                  },
                                  "downloads": {
                                    "client": { "url": "$clientJarUrl" }
                                  }
                                }
                                """.trimIndent(),
                            assetIndexUrl to
                                """
                                {
                                  "objects": {
                                    "one": { "hash": "${assetBytes.keys.elementAt(0)}" },
                                    "two": { "hash": "${assetBytes.keys.elementAt(1)}" },
                                    "three": { "hash": "${assetBytes.keys.elementAt(2)}" },
                                    "four": { "hash": "${assetBytes.keys.elementAt(3)}" }
                                  }
                                }
                                """.trimIndent(),
                        ),
                    binaryResponses =
                        mapOf(clientJarUrl to "client-jar".encodeToByteArray()) +
                            assetBytes
                                .mapKeys { (hash, _) -> requireNotNull(assetUrls[hash]) }
                                .mapValues { (_, bytes) -> bytes.encodeToByteArray() },
                    delayedUrls = assetUrls.values.toSet(),
                    delayMillis = 100,
                )
            val service = CachePreparationService(workspaceRoot = workspace, metadataFetcher = fetcher)

            val result = service.prepare(CachePrepareRequest("1.21.6", Loader.VANILLA))

            assertTrue(fetcher.maxConcurrentFetches() > 1, "asset object downloads should overlap")
            val assetArtifacts = result.artifacts.filter { it.kind == CachePreparedArtifactKind.MINECRAFT_ASSET_OBJECT }
            assertEquals(4, assetArtifacts.size)
            assetArtifacts.forEach { artifact ->
                assertTrue(Files.isRegularFile(workspace.resolve(artifact.handle)))
            }
        }

    @Test
    fun `cache preparation refetches corrupt metadata checksum binaries`() =
        runBlocking {
            val workspace = Files.createTempDirectory("craftless-cache-corrupt-metadata-resume")
            val versionUrl = "https://metadata.test/1.21.6.json"
            val clientJarUrl = "https://metadata.test/client.jar"
            val minecraftLibraryUrl = "https://libraries.minecraft.net/com/mojang/authlib/6.0.54/authlib-6.0.54.jar"
            val nativeArtifact = currentTestNativeArtifact()
            val nativeUrl = "https://libraries.minecraft.net/org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-${nativeArtifact.classifier}.jar"
            val assetIndexUrl = "https://metadata.test/assets/1.21.6.json"
            val javaRuntimeManifestUrl = "https://metadata.test/runtime/java-runtime-gamma/manifest.json"
            val javaExecutableUrl = "https://metadata.test/runtime/java-runtime-gamma/bin/java"
            val javaRuntimeFileUrl = "https://metadata.test/runtime/java-runtime-gamma/lib/runtime.txt"
            val loaderVersionsUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6"
            val loaderProfileUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6/0.17.2/profile/json"
            val fabricLoaderJarUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.17.2/fabric-loader-0.17.2.jar"
            val nativeBytes = nativeZipBytes("downloaded-native")
            val corruptNativeBytes = nativeZipBytes("corrupt-native")
            val javaExecutableBytes = fakeJavaBytes("21.0.11")
            val corruptJavaExecutableBytes = fakeJavaBytes("21.0.99")
            val javaRuntimePlatform = currentTestJavaRuntimePlatformKey()
            val clientJar = workspace.resolve("cache/minecraft/versions/1.21.6/client.jar")
            val minecraftLibrary = workspace.resolve("cache/libraries/minecraft/${minecraftLibraryUrl.sha256HexForTest()}.jar")
            val nativeLibrary = workspace.resolve("cache/libraries/native/${nativeUrl.sha256HexForTest()}.jar")
            val javaExecutable = workspace.resolve("cache/runtimes/$javaRuntimePlatform/java-runtime-gamma/image/bin/java")
            val javaRuntimeFile = workspace.resolve("cache/runtimes/$javaRuntimePlatform/java-runtime-gamma/image/lib/runtime.txt")
            val fabricLibrary = workspace.resolve("cache/libraries/fabric/${fabricLoaderJarUrl.sha256HexForTest()}.jar")
            val fetcher =
                CountingCacheMetadataFetcher(
                    responses =
                        mapOf(
                            MINECRAFT_VERSION_INDEX_URL to
                                """
                                { "versions": [{ "id": "1.21.6", "url": "$versionUrl" }] }
                                """.trimIndent(),
                            versionUrl to
                                """
                                {
                                  "id": "1.21.6",
                                  "assetIndex": {
                                    "id": "1.21.6",
                                    "url": "$assetIndexUrl"
                                  },
                                  "javaVersion": {
                                    "component": "java-runtime-gamma",
                                    "majorVersion": 21
                                  },
                                  "mainClass": "test.minecraft.Main",
                                  "arguments": {
                                    "jvm": [],
                                    "game": ["--gameDir", "${'$'}{game_directory}"]
                                  },
                                  "libraries": [
                                    {
                                      "name": "com.mojang:authlib:6.0.54",
                                      "downloads": {
                                        "artifact": {
                                          "url": "$minecraftLibraryUrl",
                                          "sha1": "${"downloaded-library".sha1HexForTest()}"
                                        }
                                      }
                                    },
                                    {
                                      "name": "org.lwjgl:lwjgl:3.3.3",
                                      "natives": {
                                        "${nativeArtifact.osName}": "${nativeArtifact.classifier}"
                                      },
                                      "downloads": {
                                        "classifiers": {
                                          "${nativeArtifact.classifier}": {
                                            "url": "$nativeUrl",
                                            "sha1": "${nativeBytes.sha1HexForTest()}"
                                          }
                                        }
                                      }
                                    }
                                  ],
                                  "downloads": {
                                    "client": {
                                      "url": "$clientJarUrl",
                                      "sha1": "${"downloaded-client".sha1HexForTest()}"
                                    }
                                  }
                                }
                                """.trimIndent(),
                            MINECRAFT_JAVA_RUNTIME_INDEX_URL to javaRuntimeIndexJson(javaRuntimeManifestUrl),
                            javaRuntimeManifestUrl to
                                """
                                {
                                  "files": {
                                    "bin/java": {
                                      "type": "file",
                                      "downloads": {
                                        "raw": {
                                          "url": "$javaExecutableUrl",
                                          "sha1": "${javaExecutableBytes.sha1HexForTest()}"
                                        }
                                      }
                                    },
                                    "lib/runtime.txt": {
                                      "type": "file",
                                      "downloads": {
                                        "raw": {
                                          "url": "$javaRuntimeFileUrl",
                                          "sha1": "${"runtime-file".sha1HexForTest()}"
                                        }
                                      }
                                    }
                                  }
                                }
                                """.trimIndent(),
                            assetIndexUrl to """{"objects":{}}""",
                            loaderVersionsUrl to """[{ "loader": { "version": "0.17.2", "stable": true } }]""",
                            loaderProfileUrl to
                                """
                                {
                                  "id": "fabric-loader-0.17.2-1.21.6",
                                  "libraries": [
                                    {
                                      "name": "net.fabricmc:fabric-loader:0.17.2",
                                      "downloads": {
                                        "artifact": {
                                          "url": "$fabricLoaderJarUrl",
                                          "sha1": "${"downloaded-fabric".sha1HexForTest()}"
                                        }
                                      }
                                    }
                                  ]
                                }
                                """.trimIndent(),
                        ),
                    binaryResponses =
                        mapOf(
                            clientJarUrl to "downloaded-client".encodeToByteArray(),
                            minecraftLibraryUrl to "downloaded-library".encodeToByteArray(),
                            nativeUrl to nativeBytes,
                            javaExecutableUrl to javaExecutableBytes,
                            javaRuntimeFileUrl to "runtime-file".encodeToByteArray(),
                            fabricLoaderJarUrl to "downloaded-fabric".encodeToByteArray(),
                        ),
                )
            writeTestFile(clientJar, "corrupt-client".encodeToByteArray())
            writeTestFile(minecraftLibrary, "corrupt-library".encodeToByteArray())
            writeTestFile(nativeLibrary, corruptNativeBytes)
            writeTestFile(javaExecutable, corruptJavaExecutableBytes)
            javaExecutable.toFile().setExecutable(true, true)
            writeTestFile(javaRuntimeFile, "corrupt-runtime-file".encodeToByteArray())
            writeTestFile(fabricLibrary, "corrupt-fabric".encodeToByteArray())
            val service = CachePreparationService(workspaceRoot = workspace, metadataFetcher = fetcher)

            service.prepare(CachePrepareRequest("1.21.6", Loader.FABRIC))

            val extractedNative =
                workspace
                    .resolve("cache/natives/${nativeUrl.sha256HexForTest()}")
                    .resolve("libcraftless-test.dylib")
            assertEquals("downloaded-client", Files.readString(clientJar))
            assertEquals("downloaded-library", Files.readString(minecraftLibrary))
            assertEquals("downloaded-native", Files.readString(extractedNative))
            assertTrue(Files.readString(javaExecutable).contains("21.0.11"))
            assertEquals("runtime-file", Files.readString(javaRuntimeFile))
            assertEquals("downloaded-fabric", Files.readString(fabricLibrary))
            listOf(
                clientJarUrl,
                minecraftLibraryUrl,
                nativeUrl,
                javaExecutableUrl,
                javaRuntimeFileUrl,
                fabricLoaderJarUrl,
            ).forEach { url -> assertEquals(1, fetcher.binaryFetchCount(url), url) }

            service.prepare(CachePrepareRequest("1.21.6", Loader.FABRIC))

            listOf(
                clientJarUrl,
                minecraftLibraryUrl,
                nativeUrl,
                javaExecutableUrl,
                javaRuntimeFileUrl,
                fabricLoaderJarUrl,
            ).forEach { url -> assertEquals(1, fetcher.binaryFetchCount(url), url) }
        }

    @Test
    fun `cache preparation resumes after per-file artifact fetch failure`() =
        runBlocking {
            val workspace = Files.createTempDirectory("craftless-cache-retry")
            val versionUrl = "https://metadata.test/1.21.6.json"
            val clientJarUrl = "https://metadata.test/client.jar"
            val assetIndexUrl = "https://metadata.test/assets/1.21.6.json"
            val assetHash = "a4b45e57b3934836f20ccf8529c18bcd1e120129"
            val assetObjectUrl = "https://resources.download.minecraft.net/a4/$assetHash"
            val fetcher =
                CountingCacheMetadataFetcher(
                    responses =
                        mapOf(
                            MINECRAFT_VERSION_INDEX_URL to
                                """
                                { "versions": [{ "id": "1.21.6", "url": "$versionUrl" }] }
                                """.trimIndent(),
                            versionUrl to
                                """
                                {
                                  "id": "1.21.6",
                                  "assetIndex": {
                                    "id": "1.21.6",
                                    "url": "$assetIndexUrl"
                                  },
                                  "mainClass": "test.minecraft.Main",
                                  "arguments": {
                                    "jvm": [],
                                    "game": ["--gameDir", "${'$'}{game_directory}"]
                                  },
                                  "downloads": {
                                    "client": { "url": "$clientJarUrl" }
                                  }
                                }
                                """.trimIndent(),
                            assetIndexUrl to
                                """
                                {
                                  "objects": {
                                    "minecraft/sounds/random/test.ogg": {
                                      "hash": "$assetHash",
                                      "size": 10
                                    }
                                  }
                                }
                                """.trimIndent(),
                        ),
                    binaryResponses = mapOf(clientJarUrl to "downloaded-client".encodeToByteArray()),
                )
            val service = CachePreparationService(workspaceRoot = workspace, metadataFetcher = fetcher)

            assertFailsWith<IllegalArgumentException> {
                service.prepare(CachePrepareRequest("1.21.6", Loader.VANILLA))
            }
            val clientJar = workspace.resolve("cache/minecraft/versions/1.21.6/client.jar")
            assertEquals("downloaded-client", Files.readString(clientJar))
            assertEquals(1, fetcher.binaryFetchCount(clientJarUrl))

            fetcher.addBinaryResponse(assetObjectUrl, "asset-bytes".encodeToByteArray())
            val result = service.prepare(CachePrepareRequest("1.21.6", Loader.VANILLA))

            assertEquals(1, fetcher.binaryFetchCount(clientJarUrl))
            assertEquals(2, fetcher.binaryFetchCount(assetObjectUrl))
            val assetObject = result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_ASSET_OBJECT }
            assertEquals("asset-bytes", Files.readString(workspace.resolve(assetObject.handle)))
            assertTrue(Files.isRegularFile(workspace.resolve(result.manifest)))
        }

    @Test
    fun `cache export and cleanup operate from prepared manifest handles`() =
        runBlocking {
            val workspace = Files.createTempDirectory("craftless-cache-export-cleanup")
            val versionUrl = "https://metadata.test/1.21.6.json"
            val clientJarUrl = "https://metadata.test/client.jar"
            val assetIndexUrl = "https://metadata.test/assets/1.21.6.json"
            val loaderVersionsUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6"
            val loaderProfileUrl = "$FABRIC_META_BASE_URL/versions/loader/1.21.6/0.17.2/profile/json"
            val service =
                CachePreparationService(
                    workspaceRoot = workspace,
                    metadataFetcher =
                        StaticCacheMetadataFetcher(
                            mapOf(
                                MINECRAFT_VERSION_INDEX_URL to
                                    """
                                    { "versions": [{ "id": "1.21.6", "url": "$versionUrl" }] }
                                    """.trimIndent(),
                                versionUrl to
                                    """
                                    {
                                      "id": "1.21.6",
                                      "assetIndex": {
                                        "id": "1.21.6",
                                        "url": "$assetIndexUrl"
                                      },
                                      "downloads": {
                                        "client": { "url": "$clientJarUrl" }
                                      }
                                    }
                                    """.trimIndent(),
                                assetIndexUrl to """{"objects":{}}""",
                                loaderVersionsUrl to """[{ "loader": { "version": "0.17.2", "stable": true } }]""",
                                loaderProfileUrl to """{"id":"fabric-loader-0.17.2-1.21.6"}""",
                            ),
                            binaryResponses = mapOf(clientJarUrl to "client-jar".encodeToByteArray()),
                        ),
                )

            val prepared = service.prepare(CachePrepareRequest("1.21.6", Loader.FABRIC))
            val exported =
                service.export(
                    CacheExportRequest(
                        manifest = prepared.manifest,
                        archive = "exports/prepared-cache.zip",
                    ),
                )

            assertEquals("exports/prepared-cache.zip", exported.archive)
            assertTrue(exported.included.contains(prepared.manifest))
            assertTrue(exported.included.contains("cache/minecraft/versions/1.21.6/client.jar"))
            assertTrue(zipEntries(workspace.resolve(exported.archive)).contains(prepared.manifest))
            assertTrue(zipEntries(workspace.resolve(exported.archive)).contains("cache/minecraft/versions/1.21.6/client.jar"))

            val cleaned = service.cleanup(CacheCleanupRequest(prepared.manifest))

            assertTrue(cleaned.deleted.contains(prepared.manifest))
            assertTrue(cleaned.deleted.contains("cache/minecraft/versions/1.21.6/client.jar"))
            assertEquals(emptyList(), cleaned.missing)
            assertTrue(!Files.exists(workspace.resolve(prepared.manifest)))
            assertTrue(!Files.exists(workspace.resolve("cache/minecraft/versions/1.21.6/client.jar")))
            assertTrue(Files.exists(workspace.resolve(exported.archive)))
        }
}

private fun nativeZipBytes(content: String = "native-bytes"): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        zip.putNextEntry(ZipEntry("libcraftless-test.dylib"))
        zip.write(content.encodeToByteArray())
        zip.closeEntry()
        zip.putNextEntry(ZipEntry("META-INF/ignored.txt"))
        zip.write("ignored".encodeToByteArray())
        zip.closeEntry()
    }
    return output.toByteArray()
}

private data class TestNativeArtifact(
    val osName: String,
    val classifier: String,
) {
    fun wrongPlatform(): TestNativeArtifact =
        when (osName) {
            "windows" -> TestNativeArtifact("linux", "natives-linux")
            else -> TestNativeArtifact("windows", "natives-windows")
        }
}

private fun currentTestNativeArtifact(): TestNativeArtifact {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val arm64 = arch == "aarch64" || arch == "arm64"
    return when {
        "win" in os && arm64 -> TestNativeArtifact("windows", "natives-windows-arm64")
        "win" in os -> TestNativeArtifact("windows", "natives-windows")
        ("mac" in os || "darwin" in os) && arm64 -> TestNativeArtifact("osx", "natives-macos-arm64")
        "mac" in os || "darwin" in os -> TestNativeArtifact("osx", "natives-macos")
        else -> TestNativeArtifact("linux", "natives-linux")
    }
}

private fun currentTestJavaRuntimePlatformKey(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    return when {
        "win" in os -> "windows-x64"
        "mac" in os || "darwin" in os -> if ("aarch64" in arch || "arm64" in arch) "mac-os-arm64" else "mac-os"
        else -> "linux"
    }
}

private fun fakeJavaBytes(version: String): ByteArray =
    """
    #!/usr/bin/env sh
    echo 'openjdk version "$version" 2026-04-21 LTS' >&2
    echo 'Eclipse Temurin Runtime Environment' >&2
    echo '    os.arch = aarch64' >&2
    """.trimIndent().encodeToByteArray()

private fun String.sha256HexForTest(): String =
    java.security.MessageDigest
        .getInstance("SHA-256")
        .digest(encodeToByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

private fun String.sha1HexForTest(): String = encodeToByteArray().sha1HexForTest()

private fun ByteArray.sha1HexForTest(): String =
    java.security.MessageDigest
        .getInstance("SHA-1")
        .digest(this)
        .joinToString("") { byte -> "%02x".format(byte) }

private fun writeTestFile(
    path: java.nio.file.Path,
    bytes: ByteArray,
) {
    Files.createDirectories(path.parent)
    Files.write(path, bytes)
}

private fun javaRuntimeIndexJson(manifestUrl: String): String =
    """
    {
      "linux": {
        "java-runtime-gamma": [
          { "manifest": { "url": "$manifestUrl" } }
        ]
      },
      "mac-os": {
        "java-runtime-gamma": [
          { "manifest": { "url": "$manifestUrl" } }
        ]
      },
      "mac-os-arm64": {
        "java-runtime-gamma": [
          { "manifest": { "url": "$manifestUrl" } }
        ]
      },
      "windows-x64": {
        "java-runtime-gamma": [
          { "manifest": { "url": "$manifestUrl" } }
        ]
      }
    }
    """.trimIndent()

private fun zipEntries(path: java.nio.file.Path): List<String> =
    ZipInputStream(Files.newInputStream(path)).use { zip ->
        generateSequence { zip.nextEntry }
            .map { entry ->
                entry.name.also { zip.closeEntry() }
            }.toList()
    }

private const val DEFAULT_TEST_FABRIC_API_VERSION = "0.129.0+1.21.6"
private const val DEFAULT_TEST_FABRIC_API_METADATA_URL =
    "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml"
private const val DEFAULT_TEST_FABRIC_API_JAR_URL =
    "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.129.0+1.21.6/fabric-api-0.129.0+1.21.6.jar"

private fun defaultTestFabricApiMetadata(): String =
    """
    <metadata>
      <groupId>net.fabricmc.fabric-api</groupId>
      <artifactId>fabric-api</artifactId>
      <versioning>
        <versions>
          <version>$DEFAULT_TEST_FABRIC_API_VERSION</version>
        </versions>
      </versioning>
    </metadata>
    """.trimIndent()

private class StaticCacheMetadataFetcher(
    private val responses: Map<String, String>,
    private val binaryResponses: Map<String, ByteArray> = emptyMap(),
) : CacheMetadataFetcher {
    override suspend fun fetchText(url: String): String =
        requireNotNull(responses[url] ?: defaultTestTextResponse(url)) {
            "missing test response for $url"
        }

    override suspend fun fetchBytes(url: String): ByteArray =
        requireNotNull(binaryResponses[url] ?: defaultTestBinaryResponse(url)) {
            "missing test binary response for $url"
        }
}

private fun defaultTestBinaryResponse(url: String): ByteArray? =
    if (url == DEFAULT_TEST_FABRIC_API_JAR_URL) {
        "fabric-api-jar".encodeToByteArray()
    } else {
        null
    }

private fun defaultTestTextResponse(url: String): String? =
    if (url == DEFAULT_TEST_FABRIC_API_METADATA_URL) {
        defaultTestFabricApiMetadata()
    } else {
        null
    }

private class CountingCacheMetadataFetcher(
    private val responses: Map<String, String>,
    binaryResponses: Map<String, ByteArray> = emptyMap(),
) : CacheMetadataFetcher {
    private val binaryResponses = binaryResponses.toMutableMap()
    private val binaryFetches = linkedMapOf<String, Int>()

    override suspend fun fetchText(url: String): String =
        requireNotNull(responses[url] ?: defaultTestTextResponse(url)) {
            "missing test response for $url"
        }

    override suspend fun fetchBytes(url: String): ByteArray {
        binaryFetches[url] = binaryFetchCount(url) + 1
        return requireNotNull(binaryResponses[url] ?: defaultTestBinaryResponse(url)) {
            "missing test binary response for $url"
        }
    }

    fun addBinaryResponse(
        url: String,
        bytes: ByteArray,
    ) {
        binaryResponses[url] = bytes
    }

    fun binaryFetchCount(url: String): Int = binaryFetches[url] ?: 0
}

private class ConcurrentCountingCacheMetadataFetcher(
    private val responses: Map<String, String>,
    private val binaryResponses: Map<String, ByteArray>,
    private val delayedUrls: Set<String>,
    private val delayMillis: Long,
) : CacheMetadataFetcher {
    private val inFlight = AtomicInteger()
    private val maxInFlight = AtomicInteger()

    override suspend fun fetchText(url: String): String =
        requireNotNull(responses[url] ?: defaultTestTextResponse(url)) {
            "missing test response for $url"
        }

    override suspend fun fetchBytes(url: String): ByteArray {
        if (url !in delayedUrls) {
            return requireNotNull(binaryResponses[url] ?: defaultTestBinaryResponse(url)) {
                "missing test binary response for $url"
            }
        }
        val current = inFlight.incrementAndGet()
        maxInFlight.updateAndGet { previous -> maxOf(previous, current) }
        return try {
            delay(delayMillis)
            requireNotNull(binaryResponses[url] ?: defaultTestBinaryResponse(url)) {
                "missing test binary response for $url"
            }
        } finally {
            inFlight.decrementAndGet()
        }
    }

    fun maxConcurrentFetches(): Int = maxInFlight.get()
}
