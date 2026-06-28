package com.minekube.craftless.driver.fabric.official

import net.minecraft.client.Minecraft
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal fun <T> queryOfficialMinecraftClient(
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
    return result.get(OFFICIAL_CLIENT_QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
}

private const val OFFICIAL_CLIENT_QUERY_TIMEOUT_MS = 2_000L
