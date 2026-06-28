package com.minekube.craftless.driver.fabric.official

import com.minekube.craftless.driver.fabric.discovery.FabricClientStateGraphSnapshot
import net.minecraft.client.Minecraft

internal fun interface OfficialFabricClientStateProvider {
    fun snapshot(): FabricClientStateGraphSnapshot
}

internal class MinecraftOfficialFabricClientStateProvider(
    private val clientProvider: () -> Minecraft = Minecraft::getInstance,
) : OfficialFabricClientStateProvider {
    override fun snapshot(): FabricClientStateGraphSnapshot =
        queryOfficialMinecraftClient(clientProvider()) {
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
}
