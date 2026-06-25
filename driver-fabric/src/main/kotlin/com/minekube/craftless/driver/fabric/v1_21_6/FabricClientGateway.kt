package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.driver.api.ConnectionTarget
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.TitleScreen
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen
import net.minecraft.client.network.CookieStorage
import net.minecraft.client.network.ServerAddress
import net.minecraft.client.network.ServerInfo
import net.minecraft.util.PlayerInput

interface FabricClientGateway {
    fun execute(action: () -> Unit)

    fun connect(target: ConnectionTarget)

    fun dispatchChatMessage(message: String)

    fun move(intent: FabricMovementIntent)

    fun stop()

    fun isConnected(): Boolean

    fun isReadyToConnect(): Boolean
}

data class FabricMovementIntent(
    val forward: Boolean = false,
    val backward: Boolean = false,
    val left: Boolean = false,
    val right: Boolean = false,
    val jump: Boolean = false,
    val sneak: Boolean = false,
    val sprint: Boolean = false,
    val ticks: Int = 1,
)

class MinecraftFabricClientGateway(
    private val client: MinecraftClient = MinecraftClient.getInstance(),
) : FabricClientGateway {
    override fun execute(action: () -> Unit) {
        client.execute(action)
    }

    override fun connect(target: ConnectionTarget) {
        val address = ServerAddress(target.host, target.port)
        val serverInfo = ServerInfo(
            "Craftless ${target.host}:${target.port}",
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

    override fun dispatchChatMessage(message: String) {
        require(message.isNotBlank()) { "chat message is required" }
        require(!message.startsWith("/")) { "chat message must not start with slash" }
        val networkHandler = requireNotNull(client.networkHandler) { "client is not connected to a server" }
        networkHandler.sendChatMessage(message)
    }

    override fun move(intent: FabricMovementIntent) {
        require(intent.ticks > 0) { "movement ticks must be positive" }
        val player = requireNotNull(client.player) { "client is not connected to a server" }
        player.input.playerInput = PlayerInput(
            intent.forward,
            intent.backward,
            intent.left,
            intent.right,
            intent.jump,
            intent.sneak,
            intent.sprint,
        )
    }

    override fun stop() {
        client.scheduleStop()
    }

    override fun isConnected(): Boolean =
        client.networkHandler != null && client.player != null

    override fun isReadyToConnect(): Boolean =
        client.networkHandler == null && client.player == null && client.overlay == null
}
