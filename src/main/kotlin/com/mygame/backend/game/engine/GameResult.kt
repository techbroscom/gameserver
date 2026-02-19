package com.mygame.backend.game.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class GameResult(
    val winnerIds: List<String>,
    val loserIds: List<String>,
    val rankings: List<RankedPlayer>,
    val coinDeltas: Map<String, Long>,
    val xpDeltas: Map<String, Int>,
    val summary: Map<String, JsonElement>
)

@Serializable
data class RankedPlayer(
    val playerId: String,
    val rank: Int,
    val score: Int
)
