package com.mygame.backend.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

enum class TargetType {
    BROADCAST, MASTER_CLIENT, SPECIFIC_PLAYERS
}

@Serializable
data class GameEvent(
    val senderId: String,
    val roomId: String,
    val opCode: Int,
    val payload: Map<String, JsonElement>,
    val timestamp: Long = System.currentTimeMillis(),
    val targetType: TargetType = TargetType.BROADCAST,
    val targetIds: List<String> = emptyList() // Used when SPECIFIC_PLAYERS
)
