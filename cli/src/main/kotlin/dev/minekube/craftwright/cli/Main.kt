package dev.minekube.craftwright.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.CoreCliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands

fun main(args: Array<String>) {
    McwCli.root().main(args)
}

object McwCli {
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
}

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
