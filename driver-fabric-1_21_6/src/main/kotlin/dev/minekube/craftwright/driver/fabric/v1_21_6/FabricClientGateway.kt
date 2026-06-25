package dev.minekube.craftwright.driver.fabric.v1_21_6

import dev.minekube.craftwright.driver.api.ConnectionTarget
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.TitleScreen
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen
import net.minecraft.client.network.CookieStorage
import net.minecraft.client.network.ServerAddress
import net.minecraft.client.network.ServerInfo

interface FabricClientGateway {
    fun execute(action: () -> Unit)

    fun connect(target: ConnectionTarget)

    fun sendChat(message: String)

    fun sendCommand(command: String)

    fun stop()
}

class MinecraftFabricClientGateway(
    private val client: MinecraftClient = MinecraftClient.getInstance(),
) : FabricClientGateway {
    override fun execute(action: () -> Unit) {
        client.execute(action)
    }

    override fun connect(target: ConnectionTarget) {
        val address = ServerAddress(target.host, target.port)
        val serverInfo = ServerInfo(
            "Craftwright ${target.host}:${target.port}",
            "${target.host}:${target.port}",
            ServerInfo.ServerType.OTHER,
        )
        ConnectScreen.connect(
            TitleScreen(),
            client,
            address,
            serverInfo,
            false,
            CookieStorage(emptyMap()),
        )
    }

    override fun sendChat(message: String) {
        require(message.isNotBlank()) { "chat message is required" }
        require(!message.startsWith("/")) { "chat message must not start with slash" }
        val networkHandler = requireNotNull(client.networkHandler) { "client is not connected to a server" }
        networkHandler.sendChatMessage(message)
    }

    override fun sendCommand(command: String) {
        require(command.isNotBlank()) { "chat command is required" }
        require(!command.startsWith("/")) { "chat command must not start with slash" }
        val networkHandler = requireNotNull(client.networkHandler) { "client is not connected to a server" }
        networkHandler.sendChatCommand(command)
    }

    override fun stop() {
        client.scheduleStop()
    }
}
