package com.mygame.backend.game.engine.impl

import com.mygame.backend.game.engine.*
import com.mygame.backend.models.GameEvent
import com.mygame.backend.models.Player
import com.mygame.backend.models.TargetType
import kotlinx.serialization.json.*

class NumberGuessEngine : GameEngine {
    override val gameType: String = "NUMBER_GUESS"

    override fun initializeGame(players: List<Player>, config: Map<String, String>): GameState {
        val secret = (1..100).random()
        val turnOrder = players.map { it.id }.shuffled()
        
        val initialPlayerStates = players.associate { player ->
            player.id to PlayerGameState(
                playerId = player.id,
                custom = mutableMapOf(
                    "attempts" to JsonPrimitive(0)
                )
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
                "secretNumber" to JsonPrimitive(secret), // Secret! kept in custom state but not sent to client in partial updates usually
                "totalAttempts" to JsonPrimitive(0),
                "maxAttempts" to JsonPrimitive(10),
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
        if (opCode != 10) return EventResult(state, error = "Invalid OpCode")
        
        val currentTurnPlayer = state.turnOrder[state.currentTurnIndex % state.turnOrder.size]
        if (senderId != currentTurnPlayer) return EventResult(state, error = "Not your turn")

        val guess = payload["guess"]?.jsonPrimitive?.intOrNull 
            ?: return EventResult(state, error = "Missing guess")
            
        val secret = state.custom["secretNumber"]!!.jsonPrimitive.int
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
        
        val event = GameEvent(
            senderId = senderId,
            roomId = state.roomId,
            opCode = 10,
            payload = mapOf(
                "playerId" to JsonPrimitive(senderId),
                "guess" to JsonPrimitive(guess),
                "hint" to JsonPrimitive(hint),
                "attemptsLeft" to JsonPrimitive(maxAttempts - totalAttempts)
            ),
            targetType = TargetType.BROADCAST
        )
        
        if (hint == "CORRECT") {
            state.custom["winnerId"] = JsonPrimitive(senderId)
        }
        
        return EventResult(
            updatedState = state.copy(currentTurnIndex = state.currentTurnIndex + 1),
            broadcastToRoom = listOf(event)
        )
    }

    override fun checkWinCondition(state: GameState): GameResult? {
        val winnerId = state.custom["winnerId"]?.jsonPrimitive?.contentOrNull
        if (winnerId != null) {
            // We have a winner
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
                 summary = mapOf("secretNumber" to state.custom["secretNumber"]!!)
            )
        }
        
        val totalAttempts = state.custom["totalAttempts"]!!.jsonPrimitive.int
        val maxAttempts = state.custom["maxAttempts"]!!.jsonPrimitive.int
        
        if (totalAttempts >= maxAttempts) {
            // Draw / Loss for everyone
             val coinDeltas = mutableMapOf<String, Long>()
             val xpDeltas = mutableMapOf<String, Int>()
             val rankings = mutableListOf<RankedPlayer>()
             
             state.players.keys.forEach { pid ->
                 coinDeltas[pid] = 0 // No loss? Or small loss? Prompt doesn't specify draw coins for NumberGuess.
                 // "If max attempts reached... no winner (draw)"
                 // RewardConfig says: entryFee: 0. loserCoins: -10.
                 // I'll assume 0 change for draw.
                 xpDeltas[pid] = 5 
                 rankings.add(RankedPlayer(pid, 1, 0))
             }
             
             return GameResult(
                 winnerIds = emptyList(),
                 loserIds = emptyList(),
                 rankings = rankings,
                 coinDeltas = coinDeltas,
                 xpDeltas = xpDeltas,
                 summary = mapOf("secretNumber" to state.custom["secretNumber"]!!)
            )
        }
        
        return null
    }

    override fun onPlayerDisconnect(state: GameState, playerId: String): GameState {
        return state
    }
}
