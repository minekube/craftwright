package dev.minekube.craftwright.bridge.hmc

class HmcBridgeBackend private constructor(
    private val runner: BridgeCommandRunner,
) {
    fun connect(clientId: String, server: String): BridgeActionResult =
        run(ClientAction.CONNECT, clientId, "connect to $server", BridgeCommand("connect $server"))

    fun chat(clientId: String, message: String): BridgeActionResult =
        run(ClientAction.CHAT, clientId, "send chat message", BridgeCommand("chat $message"))

    fun move(clientId: String, intent: MoveIntent, ticks: Int): BridgeActionResult =
        run(ClientAction.MOVE, clientId, "move ${intent.name.lowercase()} for $ticks ticks", BridgeCommand("key ${intent.bridgeKey} $ticks"))

    fun jump(clientId: String): BridgeActionResult =
        run(ClientAction.JUMP, clientId, "jump", BridgeCommand("key space 2"))

    fun look(clientId: String, yaw: Double, pitch: Double): BridgeActionResult =
        run(ClientAction.LOOK, clientId, "set look direction", BridgeCommand("look $yaw $pitch"))

    private fun run(
        action: ClientAction,
        clientId: String,
        publicDescription: String,
        command: BridgeCommand,
    ): BridgeActionResult {
        require(clientId.isNotBlank()) { "client id is required" }
        val execution = runner.run(command)
        return BridgeActionResult(
            action = action,
            publicDescription = publicDescription,
            internalCommand = execution.command,
        )
    }

    companion object {
        fun dryRun(): HmcBridgeBackend = HmcBridgeBackend(BridgeCommandRunner { command -> BridgeCommandExecution(command) })
    }
}

fun interface BridgeCommandRunner {
    fun run(command: BridgeCommand): BridgeCommandExecution
}

data class BridgeCommandExecution(
    val command: BridgeCommand,
)

data class BridgeActionResult(
    val action: ClientAction,
    val publicDescription: String,
    val internalCommand: BridgeCommand,
)

class BridgeCommand internal constructor(
    private val value: String,
) {
    fun redacted(): String = "<internal bridge command>"
    internal fun raw(): String = value
}

enum class ClientAction {
    CONNECT,
    CHAT,
    MOVE,
    JUMP,
    LOOK,
}

enum class MoveIntent(internal val bridgeKey: String) {
    FORWARD("w"),
    BACKWARD("s"),
    LEFT("a"),
    RIGHT("d"),
}
