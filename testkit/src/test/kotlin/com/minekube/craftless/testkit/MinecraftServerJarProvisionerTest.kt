package com.minekube.craftless.testkit

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class MinecraftServerJarProvisionerTest {
    @Test
    fun `fixture provisions minecraft server jar from version manifest`() =
        runBlocking {
            val requestedUrls = mutableListOf<String>()
            val http =
                HttpClient(MockEngine) {
                    engine {
                        addHandler { request ->
                            val url = request.url.toString()
                            requestedUrls += url
                            when (url) {
                                "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json" ->
                                    respond(
                                        content =
                                            """
                                            {
                                              "versions": [
                                                {
                                                  "id": "1.21.6",
                                                  "url": "https://example.test/versions/1.21.6.json"
                                                }
                                              ]
                                            }
                                            """.trimIndent(),
                                        status = HttpStatusCode.OK,
                                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                    )

                                "https://example.test/versions/1.21.6.json" ->
                                    respond(
                                        content =
                                            """
                                            {
                                              "downloads": {
                                                "server": {
                                                  "url": "https://example.test/server-1.21.6.jar"
                                                }
                                              }
                                            }
                                            """.trimIndent(),
                                        status = HttpStatusCode.OK,
                                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                    )

                                "https://example.test/server-1.21.6.jar" ->
                                    respond(
                                        content = "server jar bytes",
                                        status = HttpStatusCode.OK,
                                        headers = headersOf(HttpHeaders.ContentType, "application/octet-stream"),
                                    )

                                else -> error("unexpected request $url")
                            }
                        }
                    }
                }
            val layout =
                LocalServerFixture(
                    root = Files.createTempDirectory("craftless-server-jar-provisioner"),
                    port = 25567,
                ).prepare()

            val provisioned =
                layout.provisionMinecraftServerJar(
                    version = "1.21.6",
                    provisioner = MinecraftServerJarProvisioner(http),
                )

            assertEquals(layout.artifactsDir.resolve("minecraft-server-1.21.6.jar"), provisioned)
            assertEquals("server jar bytes", Files.readString(provisioned))
            assertEquals(
                listOf(
                    "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json",
                    "https://example.test/versions/1.21.6.json",
                    "https://example.test/server-1.21.6.jar",
                ),
                requestedUrls,
            )
        }
}
