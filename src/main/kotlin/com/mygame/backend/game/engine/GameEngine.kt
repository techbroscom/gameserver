package com.mygame.backend.game.engine

import com.mygame.backend.models.GameEvent
import com.mygame.backend.models.Player
import kotlinx.serialization.json.JsonElement

interface GameEngine {
    val gameType: String

    fun initializeGame(players: List<Player>, config: Map<String, String>): GameState

    fun onTick(state: GameState, deltaMs: Long): TickResult

    fun onPlayerEvent(
        state: GameState,
        senderId: String,
        opCode: Int,
        payload: Map<String, JsonElement>
    ): EventResult

    fun onPlayerDisconnect(state: GameState, playerId: String): GameState

    fun checkWinCondition(state: GameState): GameResult?
}

data class TickResult(
    val updatedState: GameState,
    val broadcastEvents: List<GameEvent> = emptyList()
)

data class EventResult(
    val updatedState: GameState,
    val broadcastToRoom: List<GameEvent> = emptyList(),
    val sendToPlayer: List<GameEvent> = emptyList(),
    val error: String? = null
)
