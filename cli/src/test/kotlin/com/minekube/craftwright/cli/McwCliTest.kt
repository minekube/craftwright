package com.minekube.craftwright.cli

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McwCliTest {
    @Test
    fun `cli registers first jvm command tree`() {
        val commands = McwCli.registeredCommandPaths()

        assertTrue(commands.contains("versions"))
        assertTrue(commands.contains("profiles"))
        assertTrue(commands.contains("clients create"))
        assertTrue(commands.contains("clients list"))
        assertTrue(commands.contains("clients connect"))
        assertTrue(commands.contains("clients api"))
        assertTrue(commands.contains("server start"))
        assertTrue(commands.contains("test run"))
    }

    @Test
    fun `clients api once prints server metadata and keeps server reachable during callback`() {
        val output = StringBuilder()
        var versionStatus = 0

        val exit = McwCli.run(
            listOf("clients", "api", "--once"),
            stdout = { output.appendLine(it) },
            afterStart = { metadata ->
                kotlinx.coroutines.runBlocking {
                    HttpClient(CIO).use { http ->
                        versionStatus = http.get("${metadata.url}/version").status.value
                    }
                }
            },
        )

        assertEquals(0, exit)
        assertEquals(200, versionStatus)

        val json = Json.parseToJsonElement(output.toString().trim()).jsonObject
        assertEquals(true.toString(), json["ok"]?.jsonPrimitive?.content)
        assertTrue(json["url"]?.jsonPrimitive?.content?.startsWith("http://127.0.0.1:") == true)
        assertEquals("/openapi.json", json["openapi"]?.jsonPrimitive?.content)
        assertEquals("/events", json["events"]?.jsonPrimitive?.content)
    }
}
