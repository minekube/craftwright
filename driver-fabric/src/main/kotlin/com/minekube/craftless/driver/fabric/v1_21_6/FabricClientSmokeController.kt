package com.minekube.craftless.driver.fabric.v1_21_6

import com.minekube.craftless.driver.api.ConnectionTarget
import com.minekube.craftless.driver.api.DriverActionInvocation
import kotlinx.serialization.json.JsonPrimitive
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class FabricClientSmokeController(
    val enabled: Boolean,
    val target: ConnectionTarget = ConnectionTarget("127.0.0.1", 25565),
    val chatMessage: String = "hello from Craftless Fabric smoke",
    val connectTimeout: Duration = 30_000.milliseconds,
    val startupSettleDelay: Duration = 0.milliseconds,
) {
    init {
        require(!startupSettleDelay.isNegative()) { "fabric smoke startup settle delay must not be negative" }
    }

    fun start(
        backend: FabricDriverBackend,
        gateway: FabricClientGateway,
        pollInterval: Duration = 250.milliseconds,
    ): Boolean {
        if (!enabled) {
            return false
        }
        thread(name = "craftless-fabric-smoke", isDaemon = true) {
            if (gateway.awaitReadyToConnect(connectTimeout, pollInterval)) {
                Thread.sleep(startupSettleDelay.inWholeMilliseconds)
                backend.connect(SMOKE_CLIENT_ID, target)
            }
            if (gateway.awaitConnected(connectTimeout, pollInterval)) {
                backend.invoke(
                    SMOKE_CLIENT_ID,
                    DriverActionInvocation(
                        action = "player.chat",
                        arguments = mapOf("message" to JsonPrimitive(chatMessage)),
                    ),
                )
            }
            backend.stop(SMOKE_CLIENT_ID)
        }
        return true
    }

    companion object {
        private const val ENABLED = "CRAFTLESS_FABRIC_CLIENT_SMOKE"
        private const val HOST = "CRAFTLESS_SMOKE_SERVER_HOST"
        private const val PORT = "CRAFTLESS_SMOKE_SERVER_PORT"
        private const val CHAT_MESSAGE = "CRAFTLESS_FABRIC_SMOKE_CHAT_MESSAGE"
        private const val CONNECT_TIMEOUT = "CRAFTLESS_FABRIC_SMOKE_CONNECT_TIMEOUT_MS"
        private const val STARTUP_SETTLE = "CRAFTLESS_FABRIC_SMOKE_STARTUP_SETTLE_MS"
        private const val SMOKE_CLIENT_ID = "fabric-smoke"

        fun fromEnvironment(env: Map<String, String> = System.getenv()): FabricClientSmokeController =
            FabricClientSmokeController(
                enabled = env.isEnabled(ENABLED),
                target = ConnectionTarget(
                    host = env[HOST]?.takeIf { it.isNotBlank() } ?: "127.0.0.1",
                    port = env[PORT]?.toIntStrict(PORT) ?: 25565,
                ),
                chatMessage = env[CHAT_MESSAGE]?.takeIf { it.isNotBlank() }
                    ?: "hello from Craftless Fabric smoke",
                connectTimeout = (env[CONNECT_TIMEOUT]?.toLongStrict(CONNECT_TIMEOUT) ?: 30_000).milliseconds,
                startupSettleDelay = (env[STARTUP_SETTLE]?.toLongStrict(STARTUP_SETTLE) ?: 0).milliseconds,
            )

        private fun Map<String, String>.isEnabled(name: String): Boolean =
            this[name] == "1" || this[name].equals("true", ignoreCase = true)

        private fun String.toIntStrict(name: String): Int =
            toIntOrNull() ?: error("$name must be an integer")

        private fun String.toLongStrict(name: String): Long =
            toLongOrNull() ?: error("$name must be a long integer")
    }
}

private fun FabricClientGateway.awaitConnected(
    timeout: Duration,
    pollInterval: Duration,
): Boolean {
    require(timeout.isPositive()) { "fabric smoke connect timeout must be positive" }
    require(pollInterval.isPositive()) { "fabric smoke poll interval must be positive" }
    val deadline = System.nanoTime() + timeout.inWholeNanoseconds
    while (System.nanoTime() < deadline) {
        if (isConnected()) {
            return true
        }
        Thread.sleep(pollInterval.inWholeMilliseconds.coerceAtLeast(1))
    }
    return isConnected()
}

private fun FabricClientGateway.awaitReadyToConnect(
    timeout: Duration,
    pollInterval: Duration,
): Boolean {
    require(timeout.isPositive()) { "fabric smoke connect timeout must be positive" }
    require(pollInterval.isPositive()) { "fabric smoke poll interval must be positive" }
    val deadline = System.nanoTime() + timeout.inWholeNanoseconds
    while (System.nanoTime() < deadline) {
        if (isReadyToConnect()) {
            return true
        }
        Thread.sleep(pollInterval.inWholeMilliseconds.coerceAtLeast(1))
    }
    return isReadyToConnect()
}
