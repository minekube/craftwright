package com.minekube.craftwright.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.minekube.craftwright.daemon.LocalSessionApiServer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.CountDownLatch

fun main(args: Array<String>) {
    val exit = McwCli.run(args.toList(), stdout = ::println, stderr = System.err::println)
    if (exit != 0) {
        kotlin.system.exitProcess(exit)
    }
}

object McwCli {
    private val json = Json { encodeDefaults = true }

    fun root(): CoreCliktCommand = RootCommand().subcommands(
        LeafCommand("versions"),
        LeafCommand("profiles"),
        GroupCommand("clients").subcommands(
            LeafCommand("create"),
            LeafCommand("list"),
            LeafCommand("connect"),
            LeafCommand("api"),
        ),
        GroupCommand("server").subcommands(
            LeafCommand("start"),
        ),
        GroupCommand("test").subcommands(
            LeafCommand("run"),
        ),
    )

    fun registeredCommandPaths(): Set<String> = setOf(
        "versions",
        "profiles",
        "clients create",
        "clients list",
        "clients connect",
        "clients api",
        "server start",
        "test run",
    )

    fun run(
        args: List<String>,
        stdout: (String) -> Unit,
        stderr: (String) -> Unit = {},
        afterStart: (ApiServerMetadata) -> Unit = {},
    ): Int {
        if (args.take(2) == listOf("clients", "api")) {
            return runClientsApi(args.drop(2), stdout, stderr, afterStart)
        }
        root().main(args)
        return 0
    }

    private fun runClientsApi(
        args: List<String>,
        stdout: (String) -> Unit,
        stderr: (String) -> Unit,
        afterStart: (ApiServerMetadata) -> Unit,
    ): Int {
        val once = args.contains("--once")
        val port = args.optionValue("--port")?.toIntOrNull() ?: 0
        if (args.optionValue("--port") != null && port < 0) {
            stderr("error: --port must be a non-negative integer")
            return 2
        }

        LocalSessionApiServer.inMemory(port = port).use { server ->
            server.start()
            val metadata = ApiServerMetadata(
                ok = true,
                url = server.url(""),
                openapi = "/openapi.json",
                events = "/events",
            )
            stdout(json.encodeToString(metadata))
            afterStart(metadata)
            if (!once) {
                CountDownLatch(1).await()
            }
        }
        return 0
    }

    private fun List<String>.optionValue(name: String): String? {
        val index = indexOf(name)
        return if (index >= 0 && index + 1 < size) this[index + 1] else null
    }
}

@Serializable
data class ApiServerMetadata(
    val ok: Boolean,
    val url: String,
    val openapi: String,
    val events: String,
)

private class RootCommand : CoreCliktCommand(
    name = "mcw",
) {
    override fun help(context: Context): String =
        "Automate real Minecraft Java clients for tests, agents, and CI."

    override fun run() = Unit
}

private class GroupCommand(name: String) : CoreCliktCommand(name = name) {
    override fun run() = Unit
}

private class LeafCommand(name: String) : CoreCliktCommand(name = name) {
    override fun run() = Unit
}
