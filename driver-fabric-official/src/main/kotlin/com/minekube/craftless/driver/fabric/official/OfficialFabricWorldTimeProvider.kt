package com.minekube.craftless.driver.fabric.official

import net.minecraft.client.Minecraft

internal data class OfficialFabricWorldTime(
    val time: Long,
    val timeOfDay: Long,
)

internal fun interface OfficialFabricWorldTimeProvider {
    fun query(): OfficialFabricWorldTime
}

internal class MinecraftOfficialFabricWorldTimeProvider(
    private val clientProvider: () -> Minecraft = Minecraft::getInstance,
) : OfficialFabricWorldTimeProvider {
    override fun query(): OfficialFabricWorldTime =
        queryOfficialMinecraftClient(clientProvider()) {
            val currentWorld = requireNotNull(level) { "client is not connected to a server" }
            OfficialFabricWorldTime(
                time = currentWorld.defaultClockTime,
                timeOfDay = currentWorld.overworldClockTime,
            )
        }
}
