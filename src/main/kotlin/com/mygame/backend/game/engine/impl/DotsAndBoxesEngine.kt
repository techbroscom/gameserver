package com.mygame.backend.game.engine.impl

import com.mygame.backend.game.engine.*
import com.mygame.backend.models.GameEvent
import com.mygame.backend.models.Player
import com.mygame.backend.models.TargetType
import kotlinx.serialization.json.*

class DotsAndBoxesEngine : GameEngine {
    override val gameType: String = "DOTS_AND_BOXES"

    companion object {
        const val GRID_SIZE = 4 // 4x4 dots, 3x3 boxes
        const val BOXES_COUNT = (GRID_SIZE - 1) * (GRID_SIZE - 1)
        const val H_LINES_COUNT = (GRID_SIZE - 1) * GRID_SIZE
        const val V_LINES_COUNT = GRID_SIZE * (GRID_SIZE - 1)
        
        const val OP_DRAW_LINE = 30
    }

    override fun initializeGame(players: List<Player>, config: Map<String, String>): GameState {
        // empty arrays for lines and boxes
        val hLines = List(H_LINES_COUNT) { "" } // "" means empty, "playerId" means drawn
        val vLines = List(V_LINES_COUNT) { "" }
        val boxes = List(BOXES_COUNT) { "" } // "" means unowned, "playerId" means owned by player

        val turnOrder = players.map { it.id }.shuffled()
        
        val colors = listOf("BLUE", "RED", "GREEN", "YELLOW")

        val initialPlayerStates = players.associateIndexed { index, player ->
            val color = colors[index % colors.size]
            val custom = mutableMapOf<String, JsonElement>(
                "color" to JsonPrimitive(color),
                "boxesWon" to JsonPrimitive(0)
            )
            player.id to PlayerGameState(
                playerId = player.id,
                custom = custom,
                isAlive = true
            )
        }

        return GameState(
            roomId = "", 
            gameType = gameType,
            players = initialPlayerStates,
            phase = GamePhase.IN_PROGRESS,
            turnOrder = turnOrder,
            currentTurnIndex = 0,
            custom = mutableMapOf(
                "hLines" to Json.encodeToJsonElement(hLines),
                "vLines" to Json.encodeToJsonElement(vLines),
                "boxes" to Json.encodeToJsonElement(boxes),
                "moveCount" to JsonPrimitive(0),
                "startedAt" to JsonPrimitive(System.currentTimeMillis())
            )
        )
    }

    private inline fun <T, K, V> Iterable<T>.associateIndexed(transform: (index: Int, T) -> Pair<K, V>): Map<K, V> {
        val destination = LinkedHashMap<K, V>()
        var index = 0
        for (element in this) {
            val pair = transform(index++, element)
            destination.put(pair.first, pair.second)
        }
        return destination
    }

    override fun onTick(state: GameState, deltaMs: Long): TickResult {
        return TickResult(state)
    }

    override fun onPlayerEvent(
        state: GameState,
        senderId: String,
        opCode: Int,
        payload: Map<String, JsonElement>
    ): EventResult {
        if (opCode != OP_DRAW_LINE) return EventResult(state, error = "Invalid OpCode")

        val currentTurnPlayer = state.turnOrder[state.currentTurnIndex % state.turnOrder.size]
        if (senderId != currentTurnPlayer) return EventResult(state, error = "Not your turn")

        val isHorizontal = payload["isHorizontal"]?.jsonPrimitive?.booleanOrNull
            ?: return EventResult(state, error = "Missing isHorizontal")
            
        val lineIndex = payload["lineIndex"]?.jsonPrimitive?.intOrNull
            ?: return EventResult(state, error = "Missing lineIndex")
            
        val hLines = state.custom["hLines"]?.jsonArray?.map { it.jsonPrimitive.content }?.toMutableList()
            ?: return EventResult(state, error = "Invalid hLines state")
            
        val vLines = state.custom["vLines"]?.jsonArray?.map { it.jsonPrimitive.content }?.toMutableList()
            ?: return EventResult(state, error = "Invalid vLines state")
            
        val boxes = state.custom["boxes"]?.jsonArray?.map { it.jsonPrimitive.content }?.toMutableList()
            ?: return EventResult(state, error = "Invalid boxes state")

        // Validation
        if (isHorizontal) {
            if (lineIndex !in 0 until H_LINES_COUNT) return EventResult(state, error = "H-Line index out of range")
            if (hLines[lineIndex].isNotEmpty()) return EventResult(state, error = "Line already drawn")
            hLines[lineIndex] = senderId
        } else {
            if (lineIndex !in 0 until V_LINES_COUNT) return EventResult(state, error = "V-Line index out of range")
            if (vLines[lineIndex].isNotEmpty()) return EventResult(state, error = "Line already drawn")
            vLines[lineIndex] = senderId
        }

        // Logic to check box completions
        var boxesCompletedThisTurn = 0
        
        // Helper to check if a specific box is complete
        fun checkAndCompleteBox(boxIndex: Int): Boolean {
            if (boxIndex !in 0 until BOXES_COUNT) return false
            if (boxes[boxIndex].isNotEmpty()) return false // Already owned
            
            val row = boxIndex / (GRID_SIZE - 1)
            val col = boxIndex % (GRID_SIZE - 1)
            
            // hLine indices for this box
            val topHLine = row * (GRID_SIZE - 1) + col
            val bottomHLine = (row + 1) * (GRID_SIZE - 1) + col
            
            // vLine indices for this box
            val leftVLine = col * (GRID_SIZE - 1) + row // wait, V-lines are organized by columns or rows?
            // Actually, let's define V-lines as (row * GRID_SIZE + col) where row is 0..2, col is 0..3
            // H-lines as (row * (GRID_SIZE - 1) + col) where row is 0..3, col is 0..2
            
            val topDraw = hLines[row * (GRID_SIZE - 1) + col].isNotEmpty()
            val bottomDraw = hLines[(row + 1) * (GRID_SIZE - 1) + col].isNotEmpty()
            val leftDraw = vLines[row * GRID_SIZE + col].isNotEmpty()
            val rightDraw = vLines[row * GRID_SIZE + col + 1].isNotEmpty()
            
            if (topDraw && bottomDraw && leftDraw && rightDraw) {
                boxes[boxIndex] = senderId
                return true
            }
            return false
        }

        // Determine which boxes to check based on the line drawn
        if (isHorizontal) {
            val row = lineIndex / (GRID_SIZE - 1)
            val col = lineIndex % (GRID_SIZE - 1)
            
            // Check box above
            if (row > 0) {
                if (checkAndCompleteBox((row - 1) * (GRID_SIZE - 1) + col)) {
                    boxesCompletedThisTurn++
                }
            }
            // Check box below
            if (row < GRID_SIZE - 1) {
                if (checkAndCompleteBox(row * (GRID_SIZE - 1) + col)) {
                    boxesCompletedThisTurn++
                }
            }
        } else {
            val row = lineIndex / GRID_SIZE
            val col = lineIndex % GRID_SIZE
            
            // Check box left
            if (col > 0) {
                if (checkAndCompleteBox(row * (GRID_SIZE - 1) + (col - 1))) {
                    boxesCompletedThisTurn++
                }
            }
            // Check box right
            if (col < GRID_SIZE - 1) {
                if (checkAndCompleteBox(row * (GRID_SIZE - 1) + col)) {
                    boxesCompletedThisTurn++
                }
            }
        }

        // Update state
        val moveCount = (state.custom["moveCount"]?.jsonPrimitive?.intOrNull ?: 0) + 1
        state.custom["hLines"] = Json.encodeToJsonElement(hLines)
        state.custom["vLines"] = Json.encodeToJsonElement(vLines)
        state.custom["boxes"] = Json.encodeToJsonElement(boxes)
        state.custom["moveCount"] = JsonPrimitive(moveCount)
        
        // Update player score if boxes completed
        if (boxesCompletedThisTurn > 0) {
            val playerState = state.players[senderId]
            if (playerState != null) {
                val currentWon = playerState.custom["boxesWon"]?.jsonPrimitive?.intOrNull ?: 0
                playerState.custom["boxesWon"] = JsonPrimitive(currentWon + boxesCompletedThisTurn)
                // Also update PlayerGameState score directly
                state.players.replace(senderId, playerState.copy(score = playerState.score + boxesCompletedThisTurn))
            }
        }

        // Advance turn only if no box was completed
        var nextTurnIndex = state.currentTurnIndex
        if (boxesCompletedThisTurn == 0) {
            nextTurnIndex++
            state.custom["currentTurnIndex"] = JsonPrimitive(nextTurnIndex) // Not strictly needed as we update state directly
        }
        
        val nextTurnPlayerId = state.turnOrder[nextTurnIndex % state.turnOrder.size]

        // Create broadcast event
        val event = GameEvent(
            senderId = senderId,
            roomId = state.roomId,
            opCode = OP_DRAW_LINE,
            payload = mapOf(
                "isHorizontal" to JsonPrimitive(isHorizontal),
                "lineIndex" to JsonPrimitive(lineIndex),
                "placedBy" to JsonPrimitive(senderId),
                "nextTurnPlayerId" to JsonPrimitive(nextTurnPlayerId),
                "hLines" to Json.encodeToJsonElement(hLines),
                "vLines" to Json.encodeToJsonElement(vLines),
                "boxes" to Json.encodeToJsonElement(boxes),
                "boxesCompletedThisTurn" to JsonPrimitive(boxesCompletedThisTurn),
                "moveCount" to JsonPrimitive(moveCount)
            ),
            targetType = TargetType.BROADCAST
        )

        val newState = state.copy(
            currentTurnIndex = nextTurnIndex,
            custom = state.custom
        )

        return EventResult(updatedState = newState, broadcastToRoom = listOf(event))
    }

    // Helper map replacement extension 
    private fun <K, V> Map<K, V>.replace(key: K, value: V): Map<K, V> {
        val map = this.toMutableMap()
        map[key] = value
        return map
    }

    override fun checkWinCondition(state: GameState): GameResult? {
        val boxes = state.custom["boxes"]?.jsonArray?.map { it.jsonPrimitive.content } ?: return null
        val moveCount = state.custom["moveCount"]?.jsonPrimitive?.intOrNull ?: 0

        // Check if all boxes are claimed
        if (boxes.all { it.isNotEmpty() }) {
            // Game over
            val playerScores = state.players.mapValues { it.value.score }
            
            // Find max score
            val maxScore = playerScores.values.maxOrNull() ?: 0
            
            val winners = playerScores.filter { it.value == maxScore }.keys.toList()
            val losers = playerScores.filter { it.value != maxScore }.keys.toList()
            
            val rankings = mutableListOf<RankedPlayer>()
            rankings.addAll(winners.map { RankedPlayer(it, 1, maxScore) })
            rankings.addAll(losers.map { RankedPlayer(it, 2, playerScores[it] ?: 0) })
            
            val isDraw = winners.size > 1
            
            val coinDeltas = mutableMapOf<String, Long>()
            val xpDeltas = mutableMapOf<String, Int>()
            
            if (isDraw) {
                state.players.keys.forEach {
                    coinDeltas[it] = 20L
                    xpDeltas[it] = 10
                }
            } else {
                winners.forEach { 
                    coinDeltas[it] = 50L 
                    xpDeltas[it] = 30
                }
                losers.forEach { 
                    coinDeltas[it] = -10L
                    xpDeltas[it] = 5
                }
            }

            return GameResult(
                winnerIds = winners,
                loserIds = losers,
                rankings = rankings,
                coinDeltas = coinDeltas,
                xpDeltas = xpDeltas,
                summary = mapOf(
                    "totalMoves" to JsonPrimitive(moveCount),
                    "isDraw" to JsonPrimitive(isDraw),
                    "finalScores" to Json.encodeToJsonElement(playerScores)
                )
            )
        }

        return null // Game still in progress
    }

    override fun onPlayerDisconnect(state: GameState, playerId: String): GameState {
        return state
    }
}
