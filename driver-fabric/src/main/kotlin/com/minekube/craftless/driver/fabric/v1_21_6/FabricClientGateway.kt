package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.driver.api.ConnectionTarget
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.TitleScreen
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen
import net.minecraft.client.network.CookieStorage
import net.minecraft.client.network.ServerAddress
import net.minecraft.client.network.ServerInfo

interface FabricClientGateway {
    fun execute(action: () -> Unit)

    fun executeOnClient(action: MinecraftClient.() -> Unit)

    fun connect(target: ConnectionTarget)

    fun stop()

    fun isConnected(): Boolean

    fun isReadyToConnect(): Boolean
}

class MinecraftFabricClientGateway(
    private val client: MinecraftClient = MinecraftClient.getInstance(),
) : FabricClientGateway {
    override fun execute(action: () -> Unit) {
        client.execute(action)
    }

    override fun executeOnClient(action: MinecraftClient.() -> Unit) {
        client.execute {
            client.action()
        }
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

    override fun stop() {
        client.scheduleStop()
    }

    override fun isConnected(): Boolean =
        client.networkHandler != null && client.player != null

    override fun isReadyToConnect(): Boolean =
        client.networkHandler == null && client.player == null && client.overlay == null
}
