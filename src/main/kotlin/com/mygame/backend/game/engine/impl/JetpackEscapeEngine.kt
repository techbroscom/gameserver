package com.mygame.backend.game.engine.impl

import com.mygame.backend.game.engine.*
import com.mygame.backend.models.GameEvent
import com.mygame.backend.models.Player
import com.mygame.backend.models.TargetType
import kotlinx.serialization.json.*

/**
 * Jetpack Animal Escape – 2-player competitive arcade game.
 *
 * Player roles:
 *   RUNNER  – climbs using jetpack, tries to survive or reach target height.
 *   BLOCKER – spawns obstacles to make the runner crash.
 *
 * OpCodes:
 *   20 – RUNNER_INPUT   { dx: Float, boost: Boolean }
 *   21 – SPAWN_OBSTACLE { x: Float, obstacleType: String }
 *   22 – PLAYER_HIT     (sent by server via tick when collision detected)
 *
 * Physics are applied server-side inside onTick (20 TPS from GameLoop).
 */
class JetpackEscapeEngine : GameEngine {
    override val gameType: String = "JETPACK_ESCAPE"

    // ── Game constants ──────────────────────────────────────────────────────
    private val gameWidth = 400.0        // Logical width of the game world
    private val gameHeight = 8000.0      // Total scrollable height
    private val targetHeight = 7000.0    // Runner wins when y >= this
    private val gameDurationMs = 35_000L // 35 seconds

    // Physics
    private val gravity = -350.0         // px/s² (pulls down)
    private val boostPower = 600.0       // px/s² (jetpack push up)
    private val maxVy = 500.0            // terminal velocity upward
    private val minVy = -400.0           // terminal velocity downward
    private val moveSpeed = 250.0        // horizontal movement px/s

    // Energy
    private val maxEnergy = 100.0
    private val energyRegenRate = 12.0   // per second
    private val rockCost = 20.0
    private val spikeCost = 40.0
    private val laserCost = 50.0

    // Obstacle
    private val obstacleWidth = 50.0
    private val obstacleHeight = 50.0
    private val runnerWidth = 40.0
    private val runnerHeight = 40.0

    // ── Initialization ──────────────────────────────────────────────────────
    override fun initializeGame(players: List<Player>, config: Map<String, String>): GameState {
        // First player is RUNNER, second is BLOCKER
        val playerIds = players.map { it.id }
        val runnerId = playerIds[0]
        val blockerId = playerIds[1]

        val initialPlayerStates = mapOf(
            runnerId to PlayerGameState(
                playerId = runnerId,
                custom = mutableMapOf(
                    "role" to JsonPrimitive("RUNNER"),
                    "x" to JsonPrimitive(gameWidth / 2),
                    "y" to JsonPrimitive(100.0),
                    "vx" to JsonPrimitive(0.0),
                    "vy" to JsonPrimitive(0.0),
                    "boosting" to JsonPrimitive(false),
                    "dx" to JsonPrimitive(0.0),  // horizontal input: -1, 0, 1
                    "alive" to JsonPrimitive(true)
                )
            ),
            blockerId to PlayerGameState(
                playerId = blockerId,
                custom = mutableMapOf(
                    "role" to JsonPrimitive("BLOCKER"),
                    "energy" to JsonPrimitive(maxEnergy),
                )
            )
        )

        return GameState(
            roomId = "",
            gameType = gameType,
            players = initialPlayerStates,
            phase = GamePhase.IN_PROGRESS,
            turnOrder = playerIds,
            custom = mutableMapOf(
                "runnerId" to JsonPrimitive(runnerId),
                "blockerId" to JsonPrimitive(blockerId),
                "obstacles" to JsonArray(emptyList()),
                "nextObstacleId" to JsonPrimitive(0),
                "targetHeight" to JsonPrimitive(targetHeight),
                "gameDurationMs" to JsonPrimitive(gameDurationMs),
                "winnerId" to JsonNull,
                "winReason" to JsonPrimitive(""),
                "broadcastTickCounter" to JsonPrimitive(0)
            ),
            timeoutMs = gameDurationMs + 5000 // slight buffer
        )
    }

    // ── Tick (called ~20x/sec) ──────────────────────────────────────────────
    override fun onTick(state: GameState, deltaMs: Long): TickResult {
        if (state.phase != GamePhase.IN_PROGRESS) return TickResult(state)

        val dt = deltaMs / 1000.0 // seconds
        val runnerId = state.custom["runnerId"]!!.jsonPrimitive.content
        val blockerId = state.custom["blockerId"]!!.jsonPrimitive.content
        val runner = state.players[runnerId]!!

        if (runner.custom["alive"]?.jsonPrimitive?.boolean != true) {
            return TickResult(state)
        }

        // ── Runner physics ──
        var x = runner.custom["x"]!!.jsonPrimitive.double
        var y = runner.custom["y"]!!.jsonPrimitive.double
        var vy = runner.custom["vy"]!!.jsonPrimitive.double
        val dx = runner.custom["dx"]!!.jsonPrimitive.double
        val boosting = runner.custom["boosting"]!!.jsonPrimitive.boolean

        // Apply gravity
        vy += gravity * dt

        // Apply boost
        if (boosting) {
            vy += boostPower * dt
        }

        // Clamp velocity
        vy = vy.coerceIn(minVy, maxVy)

        // Update position
        y += vy * dt
        x += dx * moveSpeed * dt

        // Clamp horizontal position
        x = x.coerceIn(runnerWidth / 2, gameWidth - runnerWidth / 2)

        // Clamp bottom (can't fall below 0)
        if (y < 0) {
            y = 0.0
            vy = 0.0
        }

        runner.custom["x"] = JsonPrimitive(x)
        runner.custom["y"] = JsonPrimitive(y)
        runner.custom["vy"] = JsonPrimitive(vy)

        // ── Blocker energy regeneration ──
        val blocker = state.players[blockerId]!!
        val currentEnergy = blocker.custom["energy"]!!.jsonPrimitive.double
        val newEnergy = (currentEnergy + energyRegenRate * dt).coerceAtMost(maxEnergy)
        blocker.custom["energy"] = JsonPrimitive(newEnergy)

        // ── Collision detection ──
        val obstacles = state.custom["obstacles"]!!.jsonArray
        var hit = false
        val hitObstacleId: Int

        for (obs in obstacles) {
            val obj = obs.jsonObject
            val ox = obj["x"]!!.jsonPrimitive.double
            val oy = obj["y"]!!.jsonPrimitive.double
            val ow = obj["w"]?.jsonPrimitive?.double ?: obstacleWidth
            val oh = obj["h"]?.jsonPrimitive?.double ?: obstacleHeight

            // Simple AABB collision
            if (x - runnerWidth / 2 < ox + ow / 2 &&
                x + runnerWidth / 2 > ox - ow / 2 &&
                y - runnerHeight / 2 < oy + oh / 2 &&
                y + runnerHeight / 2 > oy - oh / 2
            ) {
                hit = true
                break
            }
        }

        val broadcastEvents = mutableListOf<GameEvent>()

        if (hit) {
            runner.custom["alive"] = JsonPrimitive(false)
            state.custom["winnerId"] = JsonPrimitive(blockerId)
            state.custom["winReason"] = JsonPrimitive("RUNNER_HIT_OBSTACLE")

            broadcastEvents.add(
                GameEvent(
                    senderId = "SERVER",
                    roomId = state.roomId,
                    opCode = 22,
                    payload = mapOf(
                        "runnerId" to JsonPrimitive(runnerId),
                        "reason" to JsonPrimitive("OBSTACLE_HIT")
                    ),
                    targetType = TargetType.BROADCAST
                )
            )
        }

        // ── Check if runner reached target height ──
        if (!hit && y >= targetHeight) {
            state.custom["winnerId"] = JsonPrimitive(runnerId)
            state.custom["winReason"] = JsonPrimitive("REACHED_TARGET")
        }

        // ── Broadcast state snapshot every 3 ticks (~6.6 Hz) ──
        val tickCounter = (state.custom["broadcastTickCounter"]?.jsonPrimitive?.int ?: 0) + 1
        state.custom["broadcastTickCounter"] = JsonPrimitive(tickCounter)

        if (tickCounter % 3 == 0) {
            broadcastEvents.add(
                GameEvent(
                    senderId = "SERVER",
                    roomId = state.roomId,
                    opCode = 23, // STATE_SYNC
                    payload = mapOf(
                        "rx" to JsonPrimitive(x),
                        "ry" to JsonPrimitive(y),
                        "rvy" to JsonPrimitive(vy),
                        "energy" to JsonPrimitive(newEnergy),
                        "elapsed" to JsonPrimitive(state.elapsedMs + deltaMs)
                    ),
                    targetType = TargetType.BROADCAST
                )
            )
        }

        val updatedState = state.copy(
            elapsedMs = state.elapsedMs + deltaMs,
            tickCount = state.tickCount + 1
        )

        return TickResult(updatedState, broadcastEvents)
    }

    // ── Player events ───────────────────────────────────────────────────────
    override fun onPlayerEvent(
        state: GameState,
        senderId: String,
        opCode: Int,
        payload: Map<String, JsonElement>
    ): EventResult {
        val runnerId = state.custom["runnerId"]!!.jsonPrimitive.content
        val blockerId = state.custom["blockerId"]!!.jsonPrimitive.content

        when (opCode) {
            // ── OpCode 20: Runner Input ──
            20 -> {
                if (senderId != runnerId) return EventResult(state, error = "Only runner can send input")
                if (state.phase != GamePhase.IN_PROGRESS) return EventResult(state, error = "Game not in progress")

                val runner = state.players[runnerId]!!
                val dx = payload["dx"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val boost = payload["boost"]?.jsonPrimitive?.booleanOrNull ?: false

                runner.custom["dx"] = JsonPrimitive(dx.coerceIn(-1.0, 1.0))
                runner.custom["boosting"] = JsonPrimitive(boost)

                return EventResult(state)
            }

            // ── OpCode 21: Spawn Obstacle ──
            21 -> {
                if (senderId != blockerId) return EventResult(state, error = "Only blocker can spawn obstacles")
                if (state.phase != GamePhase.IN_PROGRESS) return EventResult(state, error = "Game not in progress")

                val obstacleType = payload["obstacleType"]?.jsonPrimitive?.contentOrNull ?: "rock"
                val x = payload["x"]?.jsonPrimitive?.doubleOrNull
                    ?: return EventResult(state, error = "Missing x position")

                // Calculate cost
                val cost = when (obstacleType) {
                    "rock" -> rockCost
                    "spike" -> spikeCost
                    "laser" -> laserCost
                    else -> return EventResult(state, error = "Unknown obstacle type: $obstacleType")
                }

                val blocker = state.players[blockerId]!!
                val currentEnergy = blocker.custom["energy"]!!.jsonPrimitive.double
                if (currentEnergy < cost) {
                    return EventResult(state, error = "Not enough energy")
                }

                // Deduct energy
                blocker.custom["energy"] = JsonPrimitive(currentEnergy - cost)

                // Place obstacle slightly above the runner's current Y position
                val runner = state.players[runnerId]!!
                val runnerY = runner.custom["y"]!!.jsonPrimitive.double
                val obstacleY = runnerY + 600.0 // Place above runner's view

                val nextId = state.custom["nextObstacleId"]!!.jsonPrimitive.int
                state.custom["nextObstacleId"] = JsonPrimitive(nextId + 1)

                val newObstacle = buildJsonObject {
                    put("id", nextId)
                    put("type", obstacleType)
                    put("x", x.coerceIn(obstacleWidth / 2, gameWidth - obstacleWidth / 2))
                    put("y", obstacleY)
                    put("w", obstacleWidth)
                    put("h", obstacleHeight)
                }

                val currentObstacles = state.custom["obstacles"]!!.jsonArray.toMutableList()
                currentObstacles.add(newObstacle)
                state.custom["obstacles"] = JsonArray(currentObstacles)

                val event = GameEvent(
                    senderId = senderId,
                    roomId = state.roomId,
                    opCode = 21,
                    payload = mapOf(
                        "obstacle" to newObstacle,
                        "energy" to JsonPrimitive(currentEnergy - cost)
                    ),
                    targetType = TargetType.BROADCAST
                )

                return EventResult(state, broadcastToRoom = listOf(event))
            }

            else -> return EventResult(state, error = "Invalid OpCode for JetpackEscape: $opCode")
        }
    }

    // ── Win condition ────────────────────────────────────────────────────────
    override fun checkWinCondition(state: GameState): GameResult? {
        val runnerId = state.custom["runnerId"]!!.jsonPrimitive.content
        val blockerId = state.custom["blockerId"]!!.jsonPrimitive.content

        // Check explicit winner (set by tick)
        val winnerId = state.custom["winnerId"]?.let {
            if (it is JsonNull) null else it.jsonPrimitive.contentOrNull
        }

        if (winnerId != null) {
            return buildResult(state, winnerId, runnerId, blockerId)
        }

        // Timer ran out → Runner survives → Runner wins
        val gameDuration = state.custom["gameDurationMs"]!!.jsonPrimitive.long
        if (state.elapsedMs >= gameDuration) {
            state.custom["winReason"] = JsonPrimitive("TIME_UP_RUNNER_SURVIVED")
            return buildResult(state, runnerId, runnerId, blockerId)
        }

        return null
    }

    private fun buildResult(
        state: GameState,
        winnerId: String,
        runnerId: String,
        blockerId: String
    ): GameResult {
        val loserId = if (winnerId == runnerId) blockerId else runnerId
        val runnerHeight = state.players[runnerId]?.custom?.get("y")?.jsonPrimitive?.double ?: 0.0

        val coinDeltas = mutableMapOf(
            winnerId to 50L,
            loserId to -10L
        )
        val xpDeltas = mutableMapOf(
            winnerId to 30,
            loserId to 5
        )
        return GameResult(
            winnerIds = listOf(winnerId),
            loserIds = listOf(loserId),
            rankings = listOf(
                RankedPlayer(winnerId, 1, 10),
                RankedPlayer(loserId, 2, 0)
            ),
            coinDeltas = coinDeltas,
            xpDeltas = xpDeltas,
            summary = mapOf(
                "winReason" to (state.custom["winReason"] ?: JsonPrimitive("UNKNOWN")),
                "runnerHeight" to JsonPrimitive(runnerHeight),
                "elapsed" to JsonPrimitive(state.elapsedMs)
            )
        )
    }

    override fun onPlayerDisconnect(state: GameState, playerId: String): GameState {
        val runnerId = state.custom["runnerId"]!!.jsonPrimitive.content
        val blockerId = state.custom["blockerId"]!!.jsonPrimitive.content

        // If the runner disconnects, blocker wins. If blocker disconnects, runner wins.
        val winnerId = if (playerId == runnerId) blockerId else runnerId
        state.custom["winnerId"] = JsonPrimitive(winnerId)
        state.custom["winReason"] = JsonPrimitive("OPPONENT_DISCONNECTED")

        return state
    }
}
