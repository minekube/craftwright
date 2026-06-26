package com.minekube.craftless.daemon

import com.minekube.craftless.protocol.CachePrepareRequest
import com.minekube.craftless.protocol.CachePreparedArtifactKind
import com.minekube.craftless.protocol.FABRIC_META_BASE_URL
import com.minekube.craftless.protocol.Loader
import com.minekube.craftless.protocol.MINECRAFT_JAVA_RUNTIME_INDEX_URL
import com.minekube.craftless.protocol.MINECRAFT_VERSION_INDEX_URL
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CachePreparationServiceTest {
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
                                          "${'$'}{game_directory}"
                                        ]
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
                                    javaExecutableUrl to "java-binary".encodeToByteArray(),
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
            val assetObject = result.artifacts.single { it.kind == CachePreparedArtifactKind.MINECRAFT_ASSET_OBJECT }
            assertEquals(assetObjectUrl, assetObject.source)
            assertTrue(assetObject.handle.startsWith("cache/assets/objects/"))
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
            assertTrue(Files.readString(workspace.resolve("cache/minecraft/version_manifest_v2.json")).contains("1.21.6"))
            assertTrue(Files.readString(workspace.resolve("cache/minecraft/versions/1.21.6/version.json")).contains("client.jar"))
            assertEquals("client-jar", Files.readString(workspace.resolve("cache/minecraft/versions/1.21.6/client.jar")))
            assertTrue(Files.readString(workspace.resolve("cache/runtimes/index.json")).contains("java-runtime-gamma"))
            assertTrue(Files.readString(workspace.resolve(javaRuntimeManifest.handle)).contains("bin/java"))
            assertEquals("java-binary", Files.readString(workspace.resolve(javaExecutable.handle)))
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
            assertTrue(launchArgumentsJson.contains("\"$launchClasspath\""))
            assertTrue(launchArgumentsJson.contains("\"--fabric-test\""))
            assertTrue(launchArgumentsJson.contains("\"{{gameRoot}}\""))
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
