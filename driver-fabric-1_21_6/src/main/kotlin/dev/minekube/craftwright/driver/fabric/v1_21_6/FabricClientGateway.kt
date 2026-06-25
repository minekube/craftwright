package dev.minekube.craftwright.driver.fabric.v1_21_6

import dev.minekube.craftwright.driver.api.ConnectionTarget
import dev.minekube.craftwright.driver.api.PlayerPosition
import dev.minekube.craftwright.protocol.ClientState
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

    fun sendChat(message: String)

    fun sendCommand(command: String)

    fun player(): FabricClientPlayer?

    fun move(intent: FabricMovementIntent)

    fun stop()
}

data class FabricClientPlayer(
    val name: String,
    val state: ClientState,
    val position: PlayerPosition,
)

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

    override fun player(): FabricClientPlayer {
        val player = client.player
        return FabricClientPlayer(
            name = player?.name?.string ?: client.session.username,
            state = if (player != null && client.world != null && client.networkHandler != null) {
                ClientState.CONNECTED
            } else {
                ClientState.RUNNING
            },
            position = player?.let {
                PlayerPosition(
                    x = it.x,
                    y = it.y,
                    z = it.z,
                )
            } ?: PlayerPosition(0.0, 0.0, 0.0),
        )
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
}
