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
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
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
                                        "id": "1.21.6",
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
                                        "id": "1.21.6",
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
            val assetObjectUrl = "https://resources.download.minecraft.net/ab/abcdef0123456789abcdef0123456789abcdef01"
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
                                        "id": "1.21.6",
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
                                          "hash": "abcdef0123456789abcdef0123456789abcdef01",
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
                ),
                result.artifacts.map { it.kind },
            )
            assertEquals("0.17.2", result.loaderVersion)
            assertEquals(versionUrl, result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_VERSION_MANIFEST }.source)
            assertEquals(clientJarUrl, result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_CLIENT_JAR }.source)
            assertEquals(assetIndexUrl, result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_ASSET_INDEX }.source)
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
                "cache/assets/objects/ab/abcdef0123456789abcdef0123456789abcdef01",
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
            assertEquals(null, fabricLibrary.source)
            assertTrue(fabricLibrary.handle.startsWith("cache/libraries/fabric/"))
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
            assertTrue(launchArgumentsJson.contains("\"{{gameRoot}}\""))
            assertFalse(launchArgumentsJson.contains("\"--demo\""))
            assertEquals("minecraft-library", Files.readString(workspace.resolve(minecraftLibrary.handle)))
            assertTrue(Files.isRegularFile(workspace.resolve(nativeLibrary.handle)))
            assertEquals("native-bytes", Files.readString(workspace.resolve(nativeDirectory.handle).resolve("libcraftless-test.dylib")))
            assertTrue(!Files.exists(workspace.resolve(nativeDirectory.handle).resolve("META-INF")))
            assertTrue(Files.readString(workspace.resolve("cache/assets/indexes/1.21.6.json")).contains("test.ogg"))
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
    fun `cache preparation resumes after per-file artifact fetch failure`() =
        runBlocking {
            val workspace = Files.createTempDirectory("craftless-cache-retry")
            val versionUrl = "https://metadata.test/1.21.6.json"
            val clientJarUrl = "https://metadata.test/client.jar"
            val assetIndexUrl = "https://metadata.test/assets/1.21.6.json"
            val assetObjectUrl = "https://resources.download.minecraft.net/ab/abcdef0123456789abcdef0123456789abcdef01"
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
                                      "hash": "abcdef0123456789abcdef0123456789abcdef01",
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

private fun nativeZipBytes(): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        zip.putNextEntry(ZipEntry("libcraftless-test.dylib"))
        zip.write("native-bytes".encodeToByteArray())
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

private class StaticCacheMetadataFetcher(
    private val responses: Map<String, String>,
    private val binaryResponses: Map<String, ByteArray> = emptyMap(),
) : CacheMetadataFetcher {
    override suspend fun fetchText(url: String): String = requireNotNull(responses[url]) { "missing test response for $url" }

    override suspend fun fetchBytes(url: String): ByteArray =
        requireNotNull(binaryResponses[url]) {
            "missing test binary response for $url"
        }
}

private class CountingCacheMetadataFetcher(
    private val responses: Map<String, String>,
    binaryResponses: Map<String, ByteArray> = emptyMap(),
) : CacheMetadataFetcher {
    private val binaryResponses = binaryResponses.toMutableMap()
    private val binaryFetches = linkedMapOf<String, Int>()

    override suspend fun fetchText(url: String): String = requireNotNull(responses[url]) { "missing test response for $url" }

    override suspend fun fetchBytes(url: String): ByteArray {
        binaryFetches[url] = binaryFetchCount(url) + 1
        return requireNotNull(binaryResponses[url]) {
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
