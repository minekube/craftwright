package com.minekube.craftless.driver.fabric.official

import com.minekube.craftless.driver.fabric.discovery.FabricClientStateGraphSnapshot
import net.minecraft.client.Minecraft
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal fun interface OfficialFabricClientStateProvider {
    fun snapshot(): FabricClientStateGraphSnapshot
}

internal class MinecraftOfficialFabricClientStateProvider(
    private val clientProvider: () -> Minecraft = Minecraft::getInstance,
) : OfficialFabricClientStateProvider {
    override fun snapshot(): FabricClientStateGraphSnapshot =
        queryOnClient(clientProvider()) {
            val currentPlayer = player
            val connection = getConnection()
            FabricClientStateGraphSnapshot(
                connected = connection != null && currentPlayer != null,
                player = currentPlayer != null,
                inventory = currentPlayer?.inventory != null,
                camera = getCameraEntity() != null || currentPlayer != null,
                interactionManager = gameMode != null,
                world = level != null,
                recipes = currentPlayer?.recipeBook != null && connection?.recipes() != null,
                recipeCrafting = gameMode != null && currentPlayer?.containerMenu != null,
            )
        }

    private fun <T> queryOnClient(
        client: Minecraft,
        query: Minecraft.() -> T,
    ): T {
        if (client.isSameThread) {
            return client.query()
        }
        val result = CompletableFuture<T>()
        client.execute {
            runCatching {
                client.query()
            }.onSuccess(result::complete)
                .onFailure(result::completeExceptionally)
        }
        return result.get(CLIENT_QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }

    private companion object {
        const val CLIENT_QUERY_TIMEOUT_MS = 2_000L
    }
}
