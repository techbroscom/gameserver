package com.mygame.backend.game.engine.impl

import com.mygame.backend.game.engine.*
import com.mygame.backend.models.GameEvent
import com.mygame.backend.models.Player
import com.mygame.backend.models.TargetType
import kotlinx.serialization.json.*

class NumberGuessEngine : GameEngine {
    override val gameType: String = "NUMBER_GUESS"

    override fun initializeGame(players: List<Player>, config: Map<String, String>): GameState {
        val turnOrder = players.map { it.id }.shuffled()
        
        val initialPlayerStates = players.associate { player ->
            player.id to PlayerGameState(
                playerId = player.id,
                custom = mutableMapOf(
                    "attempts" to JsonPrimitive(0),
                    "hasSetSecret" to JsonPrimitive(false)
                )
            )
        }

        return GameState(
            roomId = "",
            gameType = gameType,
            players = initialPlayerStates,
            phase = GamePhase.WAITING, // Phase for setting secrets
            turnOrder = turnOrder,
            currentTurnIndex = 0,
            custom = mutableMapOf(
                "totalAttempts" to JsonPrimitive(0),
                "maxAttempts" to JsonPrimitive(20), // Increased max attempts as it's harder now? Or keep 10?
                "winnerId" to JsonNull
            )
        )
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
        // OpCode 11: Set Secret
        if (opCode == 11) {
            if (state.phase != GamePhase.WAITING) return EventResult(state, error = "Secret setting phase over")
            
            val secret = payload["secret"]?.jsonPrimitive?.intOrNull 
                ?: return EventResult(state, error = "Missing secret number")
            
            if (secret < 1 || secret > 100) return EventResult(state, error = "Secret must be 1-100")
            
            state.custom["secret_$senderId"] = JsonPrimitive(secret)
            state.players[senderId]?.custom?.put("hasSetSecret", JsonPrimitive(true))
            
            val allReady = state.players.values.all { p -> 
                p.custom["hasSetSecret"]?.jsonPrimitive?.boolean == true 
            }
            
            val updatedState = if (allReady) {
                state.copy(phase = GamePhase.IN_PROGRESS)
            } else {
                state
            }
            
            val event = GameEvent(
                senderId = senderId,
                roomId = state.roomId,
                opCode = 11,
                payload = mapOf(
                    "playerId" to JsonPrimitive(senderId),
                    "isReady" to JsonPrimitive(true),
                    "allReady" to JsonPrimitive(allReady)
                ),
                targetType = TargetType.BROADCAST
            )
            
            return EventResult(updatedState, broadcastToRoom = listOf(event))
        }

        // OpCode 10: Guess
        if (opCode == 10) {
            if (state.phase != GamePhase.IN_PROGRESS) return EventResult(state, error = "Game not in guessing phase")
            
            val currentTurnPlayer = state.turnOrder[state.currentTurnIndex % state.turnOrder.size]
            if (senderId != currentTurnPlayer) return EventResult(state, error = "Not your turn")

            val guess = payload["guess"]?.jsonPrimitive?.intOrNull 
                ?: return EventResult(state, error = "Missing guess")
                
            val opponentId = state.players.keys.firstOrNull { it != senderId }
                ?: return EventResult(state, error = "No opponent found")
                
            val secret = state.custom["secret_$opponentId"]?.jsonPrimitive?.int
                ?: return EventResult(state, error = "Opponent secret not found")
                
            val totalAttempts = state.custom["totalAttempts"]!!.jsonPrimitive.int + 1
            val maxAttempts = state.custom["maxAttempts"]!!.jsonPrimitive.int
            
            // Update player attempts
            val playerState = state.players[senderId]!!
            val pAttempts = playerState.custom["attempts"]!!.jsonPrimitive.int + 1
            playerState.custom["attempts"] = JsonPrimitive(pAttempts)
            
            state.custom["totalAttempts"] = JsonPrimitive(totalAttempts)
            
            val hint = when {
                guess < secret -> "TOO_LOW"
                guess > secret -> "TOO_HIGH"
                else -> "CORRECT"
            }
            
            val nextTurnIndex = state.currentTurnIndex + 1
            val nextTurnPlayerId = state.turnOrder[nextTurnIndex % state.turnOrder.size]
            
            val event = GameEvent(
                senderId = senderId,
                roomId = state.roomId,
                opCode = 10,
                payload = mapOf(
                    "playerId" to JsonPrimitive(senderId),
                    "guess" to JsonPrimitive(guess),
                    "hint" to JsonPrimitive(hint),
                    "attemptsLeft" to JsonPrimitive(maxAttempts - totalAttempts),
                    "nextTurnPlayerId" to JsonPrimitive(nextTurnPlayerId)
                ),
                targetType = TargetType.BROADCAST
            )
            
            if (hint == "CORRECT") {
                state.custom["winnerId"] = JsonPrimitive(senderId)
            }
            
            return EventResult(
                updatedState = state.copy(currentTurnIndex = nextTurnIndex),
                broadcastToRoom = listOf(event)
            )
        }

        return EventResult(state, error = "Invalid OpCode")
    }

    override fun checkWinCondition(state: GameState): GameResult? {
        val winnerId = state.custom["winnerId"]?.jsonPrimitive?.contentOrNull
        if (winnerId != null) {
            val winner = winnerId
            val loosers = state.players.keys.filter { it != winner }
            
            val coinDeltas = mutableMapOf<String, Long>()
            val xpDeltas = mutableMapOf<String, Int>()
            val rankings = mutableListOf<RankedPlayer>()
            
            coinDeltas[winner] = 50
            xpDeltas[winner] = 30
            rankings.add(RankedPlayer(winner, 1, 10))
            
            loosers.forEach { pid ->
                coinDeltas[pid] = -10
                xpDeltas[pid] = 5
                rankings.add(RankedPlayer(pid, 2, 0))
            }
            
            return GameResult(
                 winnerIds = listOf(winner),
                 loserIds = loosers,
                 rankings = rankings,
                 coinDeltas = coinDeltas,
                 xpDeltas = xpDeltas,
                 summary = state.custom.filterKeys { it.startsWith("secret_") }
            )
        }
        
        val totalAttempts = state.custom["totalAttempts"]!!.jsonPrimitive.int
        val maxAttempts = state.custom["maxAttempts"]!!.jsonPrimitive.int
        
        if (totalAttempts >= maxAttempts) {
             val coinDeltas = mutableMapOf<String, Long>()
             val xpDeltas = mutableMapOf<String, Int>()
              val rankings = mutableListOf<RankedPlayer>()
              
              state.players.keys.forEach { pid ->
                  coinDeltas[pid] = 0
                  xpDeltas[pid] = 5 
                  rankings.add(RankedPlayer(pid, 1, 0))
              }
              
              return GameResult(
                  winnerIds = emptyList(),
                  loserIds = emptyList(),
                  rankings = rankings,
                  coinDeltas = coinDeltas,
                  xpDeltas = xpDeltas,
                  summary = state.custom.filterKeys { it.startsWith("secret_") }
             )
        }
        
        return null
    }

    override fun onPlayerDisconnect(state: GameState, playerId: String): GameState {
        return state
    }
}
