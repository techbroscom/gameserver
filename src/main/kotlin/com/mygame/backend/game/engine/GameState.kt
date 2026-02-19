package com.mygame.backend.game.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

enum class GamePhase {
    WAITING, IN_PROGRESS, GAME_OVER
}

@Serializable
data class GameState(
    val roomId: String,
    val gameType: String,
    val players: Map<String, PlayerGameState>,
    val phase: GamePhase,
    val turnOrder: List<String> = emptyList(),
    val currentTurnIndex: Int = 0,
    val tickCount: Long = 0,
    val elapsedMs: Long = 0,
    val timeoutMs: Long = 300_000,
    val custom: MutableMap<String, JsonElement>
)

@Serializable
data class PlayerGameState(
    val playerId: String,
    val score: Int = 0,
    val isAlive: Boolean = true,
    val isReady: Boolean = false,
    val custom: MutableMap<String, JsonElement>
)
