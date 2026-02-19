package com.mygame.backend.game.engine.impl

import com.mygame.backend.game.engine.*
import com.mygame.backend.models.GameEvent
import com.mygame.backend.models.Player
import com.mygame.backend.models.TargetType
import kotlinx.serialization.json.*

class BingoEngine : GameEngine {
    override val gameType: String = "BINGO"

    companion object {
        const val BOARD_SIZE = 5
        const val MAX_LINES = 5
        const val OP_CALL_NUMBER = 10
    }

    override fun initializeGame(players: List<Player>, config: Map<String, String>): GameState {
        // Ensure exactly 2 players for this implementation as per rules
        // But logic can support more? "Players: Exactly 2 players" in prompt.
        
        val initialPlayerStates = players.associate { player ->
            val board = (1..25).toList().shuffled()
            val custom = mutableMapOf<String, JsonElement>(
                "board" to Json.encodeToJsonElement(board),
                "markedIndexes" to JsonArray(emptyList()),
                "completedLines" to JsonArray(emptyList()),
                "bingoLetters" to JsonArray(emptyList()),
                "lineCount" to JsonPrimitive(0)
            )
            player.id to PlayerGameState(
                playerId = player.id,
                custom = custom,
                isAlive = true
            )
        }

        val turnOrder = players.map { it.id }.shuffled()
        
        return GameState(
            roomId = "", // Will be set by manager
            gameType = gameType,
            players = initialPlayerStates,
            phase = GamePhase.IN_PROGRESS,
            turnOrder = turnOrder,
            currentTurnIndex = 0,
            custom = mutableMapOf(
                "calledNumbers" to JsonArray(emptyList()),
                "turnNumber" to JsonPrimitive(1),
                "startedAt" to JsonPrimitive(System.currentTimeMillis())
            )
        )
    }

    override fun onTick(state: GameState, deltaMs: Long): TickResult {
        // Check turn timeout logic here if needed
        // For now just return no-op
        return TickResult(state)
    }

    override fun onPlayerEvent(
        state: GameState,
        senderId: String,
        opCode: Int,
        payload: Map<String, JsonElement>
    ): EventResult {
        if (opCode != OP_CALL_NUMBER) return EventResult(state, error = "Invalid OpCode")

        val currentTurnPlayer = state.turnOrder[state.currentTurnIndex % state.turnOrder.size]
        if (senderId != currentTurnPlayer) return EventResult(state, error = "Not your turn")

        val number = payload["number"]?.jsonPrimitive?.intOrNull
            ?: return EventResult(state, error = "Missing number")

        if (number !in 1..25) return EventResult(state, error = "Number out of range")

        val calledNumbers = state.custom["calledNumbers"]?.jsonArray?.map { it.jsonPrimitive.int } ?: emptyList()
        if (number in calledNumbers) return EventResult(state, error = "Number already called")

        // Update state
        val newCalledNumbers = calledNumbers + number
        state.custom["calledNumbers"] = Json.encodeToJsonElement(newCalledNumbers)
        
        // Update both players boards
        val updatedPlayers = state.players.toMutableMap()
        var anyLineCompleted = false
        val broadcastEvents = mutableListOf<GameEvent>()

        updatedPlayers.forEach { (pid, pState) ->
            val board = pState.custom["board"]!!.jsonArray.map { it.jsonPrimitive.int }
            val index = board.indexOf(number)
            if (index != -1) {
                val marked = pState.custom["markedIndexes"]!!.jsonArray.map { it.jsonPrimitive.int }.toMutableList()
                if (!marked.contains(index)) {
                     marked.add(index)
                     pState.custom["markedIndexes"] = Json.encodeToJsonElement(marked)
                     
                     // Check lines
                     val completed = pState.custom["completedLines"]!!.jsonArray.map { it.jsonPrimitive.content }.toMutableList()
                     val newLines = checkLines(marked, completed)
                     
                     if (newLines.isNotEmpty()) {
                         completed.addAll(newLines)
                         pState.custom["completedLines"] = Json.encodeToJsonElement(completed)
                         
                         val lineCount = completed.size
                         pState.custom["lineCount"] = JsonPrimitive(lineCount)
                         
                         val letters = listOf("B", "I", "N", "G", "O").take(lineCount)
                         pState.custom["bingoLetters"] = Json.encodeToJsonElement(letters)
                         anyLineCompleted = true
                     }
                }
            }
        }
        
        // Advance turn
        val nextTurnIndex = state.currentTurnIndex + 1
        
        // Create event
        val event = GameEvent(
            senderId = senderId,
            roomId = state.roomId,
            opCode = OP_CALL_NUMBER, // Start with generic opCode or specific
            // Wait, this is outgoing "NUMBER_CALLED".
            // The method signature returns GameEvents.
            // We should use server opCodes or messages. 
            // The prompt says "NUMBER_CALLED: { ... }" is a server event.
            // But we don't have opCodes for server messages, just JSON types.
            // GameEvent is for "client -> server" usually, but can be "server -> broadcast".
            // OpCode 10 = CALL_NUMBER is client -> server.
            // OpCode n/a for server messages (they are typed JSONs).
            // But `GameEvent` implies opCodes.
            // "Generic OpCodes (handled by backend)... Game-specific OpCodes... OpCode 10+...".
            // "Each event envelope: { senderId, roomId, opCode, payload }"
            // "onTick: returns updated state + events to broadcast".
            // "EventResult: broadcastToRoom: List<GameEvent>".
            
            // So GameEvent IS the broadcast vehicle.
            // I will reuse OpCode 10 for "NUMBER_CALLED" or define a new one?
            // "Server -> Client: { type: ..., payload: ... }".
            // The prompt in Section 10 says "Server -> Client: { type, requestId, payload }".
            // BUT Section 3 says "Generic OpCodes... OpCode 3 = GAME_STATE_SYNC".
            // And "Game-specific OpCodes 10+".
            
            // It seems we broadcast GameEvents with opCodes.
            // Client interprets them.
            // So "NUMBER_CALLED" should have an OpCode?
            // "OpCode 10 = MARK_CELL" (Bingo example).
            // I'll use 10 for "NUMBER_CALLED" broadcast too.
            
            payload = mapOf(
                "number" to JsonPrimitive(number),
                "calledBy" to JsonPrimitive(senderId),
                // We could include board updates here but they are in state sync usually?
                // Or we can put them in payload.
                // "NUMBER_CALLED: ... boardUpdates: { ... }"
                "boardUpdates" to JsonObject(updatedPlayers.mapValues { (_, p) ->
                     JsonObject(mapOf(
                         "markedIndexes" to p.custom["markedIndexes"]!!,
                         "completedLines" to p.custom["completedLines"]!!,
                         "bingoLetters" to p.custom["bingoLetters"]!!,
                         "lineCount" to p.custom["lineCount"]!!
                     ))
                })
            ),
            targetType = TargetType.BROADCAST
        )
        broadcastEvents.add(event)

        val newState = state.copy(
            currentTurnIndex = nextTurnIndex,
            players = updatedPlayers,
            custom = state.custom // updated above
        )

        return EventResult(updatedState = newState, broadcastToRoom = broadcastEvents)
    }

    override fun checkWinCondition(state: GameState): GameResult? {
        val winners = mutableListOf<String>()
        val losers = mutableListOf<String>()
        
        state.players.forEach { (pid, pState) ->
            val lineCount = pState.custom["lineCount"]?.jsonPrimitive?.int ?: 0
            if (lineCount >= 5) {
                winners.add(pid)
            }
        }
        
        if (winners.isNotEmpty()) {
            state.players.keys.forEach { pid ->
                if (!winners.contains(pid)) losers.add(pid)
            }
            
            // Calculate coins/xp
            val rankings = mutableListOf<RankedPlayer>()
            val coinDeltas = mutableMapOf<String, Long>()
            val xpDeltas = mutableMapOf<String, Int>()
            
            // Determine draw logic
            // If more than 1 winner (both reached 5 lines same turn), it's a draw?
            // Or both win?
            val isDraw = winners.size > 1
            
            // But logic says strict turns.
            // "If both players complete a new line on the same called number"
            // So yes, both can win same turn.
            
            if (isDraw) {
               winners.forEach { pid ->
                   rankings.add(RankedPlayer(pid, 1, 5))
                   coinDeltas[pid] = 10 // Draw coins
                   xpDeltas[pid] = 20  // Draw XP
               }
            } else {
               winners.forEach { pid ->
                   rankings.add(RankedPlayer(pid, 1, 5))
                   coinDeltas[pid] = 100 // Win
                   xpDeltas[pid] = 50
               }
               losers.forEach { pid ->
                   val score = state.players[pid]?.custom?.get("lineCount")?.jsonPrimitive?.int ?: 0
                   rankings.add(RankedPlayer(pid, 2, score))
                   coinDeltas[pid] = -20
                   xpDeltas[pid] = 10
               }
            }
            
            return GameResult(
                winnerIds = winners,
                loserIds = losers, // In draw, everyone is winner? Or just empty loserIds?
                                   // "drawCoins: +10 each ... no coin deduction for either"
                                   // So technically both are "winners" of the draw.
                rankings = rankings,
                coinDeltas = coinDeltas,
                xpDeltas = xpDeltas,
                summary = mapOf(
                    "totalNumbersCalled" to JsonPrimitive(state.custom["calledNumbers"]?.jsonArray?.size ?: 0)
                )
            )
        }
        
        return null
    }

    override fun onPlayerDisconnect(state: GameState, playerId: String): GameState {
        // Just mark as disconnected or kill?
        // Prompt says: "Wait 10s grace period... If no reconnect... opponent wins default"
        // This logic is usually outside engine (in RoomManager or Handler). 
        // Engine just updates state if needed.
        return state
    }
    
    private fun checkLines(marked: List<Int>, completed: List<String>): List<String> {
        val newLines = mutableListOf<String>()
        val lines = mapOf(
            "ROW_0" to listOf(0,1,2,3,4),
            "ROW_1" to listOf(5,6,7,8,9),
            "ROW_2" to listOf(10,11,12,13,14),
            "ROW_3" to listOf(15,16,17,18,19),
            "ROW_4" to listOf(20,21,22,23,24),
            "COL_0" to listOf(0,5,10,15,20),
            "COL_1" to listOf(1,6,11,16,21),
            "COL_2" to listOf(2,7,12,17,22),
            "COL_3" to listOf(3,8,13,18,23),
            "COL_4" to listOf(4,9,14,19,24),
            "DIAG_TL_BR" to listOf(0,6,12,18,24),
            "DIAG_TR_BL" to listOf(4,8,12,16,20)
        )
        
        lines.forEach { (name, indexes) ->
            if (!completed.contains(name)) {
                if (marked.containsAll(indexes)) {
                    newLines.add(name)
                }
            }
        }
        return newLines
    }
}
